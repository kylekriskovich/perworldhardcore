package com.kylekriskovich.perworldhardcore.command;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.HardcoreDimension;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HardcoreCommands implements CommandExecutor {

    private final PerWorldHardcorePlugin plugin;

    public HardcoreCommands(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;

        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            plugin.getLogger().severe("Multiverse-Core Dependency not found or not enabled!");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!sender.hasPermission("hardcore.admin")) {
            sender.sendMessage("No permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("/hardcore reload | status <world> | cull [delete] | create <name>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                plugin.loadHardcoreWorlds();
                sender.sendMessage("PerWorldHardcore config reloaded.");
                return true;

            case "status":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /hardcore status <world>");
                    return true;
                }
                statusWorld(sender, args[1]);
                return true;

            case "cull":
                boolean delete = args.length >= 2 && args[1].equalsIgnoreCase("delete");
                cullWorlds(sender, delete);
                return true;

            case "create":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /hardcore create <worldName> [flags]");
                    return true;
                }
                createHardcoreWorld(sender, args);
                return true;

            default:
                sender.sendMessage("/hardcore reload | status <world> | cull [delete] | create <name>");
                return true;
        }
    }

    // ------------------------------------------------------------------------
    // Subcommands
    // ------------------------------------------------------------------------

    private void statusWorld(CommandSender sender, String worldName) {
        World world = Bukkit.getWorld(worldName);
        boolean exists = world != null;
        boolean hardcore = plugin.isHardcoreWorld(world);

        sender.sendMessage("World: " + worldName);
        sender.sendMessage("  Exists: " + exists);
        sender.sendMessage("  Hardcore: " + hardcore);
    }

    private void cullWorlds(CommandSender sender, boolean delete) {
        // World-level (hardcore world id) candidates
        Set<String> worldCandidates = plugin.findCullableWorlds();

        if (worldCandidates.isEmpty()) {
            sender.sendMessage("No hardcore worlds are fully dead.");
            return;
        }

        sender.sendMessage("Cullable hardcore worlds: " + String.join(", ", worldCandidates));

        if (!delete) {
            sender.sendMessage("Run /hardcore cull delete to actually delete them via Multiverse.");
            return;
        }

        // Split into: safe to delete vs blocked (players online)
        List<String> blocked = new ArrayList<>();
        List<String> toCull = new ArrayList<>();

        for (String worldId : worldCandidates) {
            boolean hasPlayers = false;

            // Check all backing dimensions for online players
            for (String dimensionName : plugin.getDimensionNamesForWorld(worldId)) {
                World dimWorld = Bukkit.getWorld(dimensionName);
                if (dimWorld != null && !dimWorld.getPlayers().isEmpty()) {
                    hasPlayers = true;
                    break;
                }
            }

            if (hasPlayers) {
                blocked.add(worldId);
            } else {
                toCull.add(worldId);
            }
        }

        if (!blocked.isEmpty()) {
            sender.sendMessage("Skipping hardcore worlds with online players: "
                    + String.join(", ", blocked));
        }

        if (toCull.isEmpty()) {
            sender.sendMessage("No hardcore worlds are safe to cull right now.");
            return;
        }

        sender.sendMessage("Deleting worlds via Multiverse...");

        // Actually delete each safe hardcore world (all dimensions) via Multiverse
        for (String worldId : toCull) {
            List<String> dimensionNames = plugin.getDimensionNamesForWorld(worldId);

            for (String dimensionName : dimensionNames) {
                dispatchConsole(
                        Bukkit.getConsoleSender(),
                        "mv delete " + dimensionName
                );
            }

            plugin.removeHardcoreWorld(worldId);
        }

        sender.sendMessage("Cull completed.");
    }

    private void createHardcoreWorld(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /hardcore create <WorldName> [mv-args...] " +
                    "[--allow-spectator-on-death=<true|false>] " +
                    "[--allow-tp-after-death=<true|false>]");
            return;
        }

        String worldId = args[1]; // player-facing hardcore world name

        int maxWorlds = plugin.getMaxOpenHardcoreWorlds();
        int currentWorlds = plugin.getHardcoreWorldCount();
        if (currentWorlds >= maxWorlds) {
            sender.sendMessage("Cannot create more hardcore worlds. Limit is " + maxWorlds + ".");
            return;
        }

        // Ensure this hardcore world doesn't already exist in config
        if (plugin.hardcoreWorldExists(worldId)) {
            sender.sendMessage("Hardcore world '" + worldId + "' already exists.");
            return;
        }

        Boolean allowSpectatorOverride = null;
        Boolean allowTpAfterDeathOverride = null;

        // mvArgsOverworld: all mv create args for overworld
        // mvArgsOtherDims: same args but with any seed-related flags stripped; we will inject -s <seed>
        List<String> mvArgsOverworld = new ArrayList<>();
        List<String> mvArgsOtherDims = new ArrayList<>();

        // Parse flags after <WorldName>
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];

            // Plugin-specific flags (not forwarded to mv create)
            if (arg.startsWith("--allow-spectator-on-death=")) {
                String value = arg.substring("--allow-spectator-on-death=".length());
                allowSpectatorOverride = parseBooleanFlag(value);
                continue;
            }

            if (arg.startsWith("--allow-spectator=")) {
                String value = arg.substring("--allow-spectator=".length());
                allowSpectatorOverride = parseBooleanFlag(value);
                continue;
            }

            if (arg.startsWith("--allow-tp-after-death=")) {
                String value = arg.substring("--allow-tp-after-death=".length());
                allowTpAfterDeathOverride = parseBooleanFlag(value);
                continue;
            }

            // Seed flags: pass to overworld only (we inject real seed for nether/end)
            if (arg.equalsIgnoreCase("-s") && i + 1 < args.length) {
                String seedValue = args[++i]; // consume next token
                mvArgsOverworld.add("-s");
                mvArgsOverworld.add(seedValue);
                // Don't add to mvArgsOtherDims
                continue;
            }

            if (arg.startsWith("--seed=")) {
                mvArgsOverworld.add(arg);
                // Don't add to mvArgsOtherDims
                continue;
            }

            // Generator flags: handle NORMAL specially to avoid "Plugin 'NORMAL' does not exist"
            if (arg.equalsIgnoreCase("-g") && i + 1 < args.length) {
                String gen = args[++i]; // consume next token

                if (gen.equalsIgnoreCase("NORMAL")) {
                    // Ignore this – Multiverse "environment" already handles NORMAL.
                    sender.sendMessage(ChatColor.YELLOW +
                            "[PerWorldHardcore] Ignoring '-g NORMAL' (use -t LARGE_BIOMES/FLAT/etc for world type).");
                    continue;
                }

                // Custom generator plugin – forward to both overworld and other dims
                mvArgsOverworld.add("-g");
                mvArgsOverworld.add(gen);
                mvArgsOtherDims.add("-g");
                mvArgsOtherDims.add(gen);
                continue;
            }

            if (arg.startsWith("--generator=")) {
                String gen = arg.substring("--generator=".length());

                if (gen.equalsIgnoreCase("NORMAL")) {
                    sender.sendMessage(ChatColor.YELLOW +
                            "[PerWorldHardcore] Ignoring '--generator=NORMAL' (use -t LARGE_BIOMES/FLAT/etc for world type).");
                    continue;
                }

                mvArgsOverworld.add(arg);
                mvArgsOtherDims.add(arg);
                continue;
            }

            // All other mv args get forwarded to both overworld and other dimensions
            mvArgsOverworld.add(arg);
            mvArgsOtherDims.add(arg);
        }


        // Build dimension names for this hardcore world
        Map<HardcoreDimension, String> dimensionNames = new EnumMap<>(HardcoreDimension.class);
        for (HardcoreDimension dim : HardcoreDimension.values()) {
            String dimensionWorldName = dim.worldNameForWorld(worldId);
            dimensionNames.put(dim, dimensionWorldName);
        }

        // Ensure Multiverse-Core is present
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            sender.sendMessage("Multiverse-Core is not available. Cannot create worlds.");
            return;
        }

        // Create OVERWORLD first -----------------------------------------------
        String overworldName = dimensionNames.get(HardcoreDimension.OVERWORLD);
        String overworldEnv = HardcoreDimension.OVERWORLD.getMultiverseEnvironment();

        String overworldExtraArgs = mvArgsOverworld.isEmpty()
                ? ""
                : " " + String.join(" ", mvArgsOverworld);

        sender.sendMessage("Creating hardcore world '" + worldId + "' via Multiverse...");
        sender.sendMessage("  OVERWORLD: " + overworldName);

        dispatchConsole(sender, "mv create " + overworldName + " " + overworldEnv + overworldExtraArgs);

        Boolean finalAllowSpectatorOverride = allowSpectatorOverride;
        Boolean finalAllowTpAfterDeathOverride = allowTpAfterDeathOverride;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            World overworld = Bukkit.getWorld(overworldName);
            if (overworld == null) {
                sender.sendMessage(ChatColor.RED + "Could not find overworld '" + overworldName + "' after mv create. Aborting nether/end creation.");
                return;
            }

            long seed = overworld.getSeed();
            sender.sendMessage("  Using seed " + seed + " for NETHER and THE_END.");

            String otherDimsBaseArgs = mvArgsOtherDims.isEmpty()
                    ? ""
                    : " " + String.join(" ", mvArgsOtherDims);
            String seedArg = " -s " + seed;

            for (HardcoreDimension dim : HardcoreDimension.values()) {
                if (dim == HardcoreDimension.OVERWORLD) continue;

                String dimName = dimensionNames.get(dim);
                String env = dim.getMultiverseEnvironment();

                sender.sendMessage("  " + dim.name() + ": " + dimName);
                String cmd = "mv create " + dimName + " " + env + otherDimsBaseArgs + seedArg;
                dispatchConsole(sender, cmd);
            }

            plugin.addHardcoreWorld(
                    worldId,
                    dimensionNames,
                    finalAllowSpectatorOverride,
                    finalAllowTpAfterDeathOverride
            );

            plugin.enforceHardDifficultyForWorld(worldId);

            sender.sendMessage("Hardcore world '" + worldId + "' created and registered.");

        }, 1L); // 1 tick later
    }


    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private void dispatchConsole(CommandSender feedbackTarget, String command) {
        feedbackTarget.sendMessage(" > " + command);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        // If Multiverse confirm-mode is "enable" or similar, dangerous commands
        // (like mv create / mv delete) will require /mv confirm.
        // Calling it here is harmless if there is nothing pending.
        if (command.startsWith("mv ")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv confirm");
        }
    }

    private Boolean parseBooleanFlag(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        if (v.equals("true") || v.equals("yes") || v.equals("y")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("n")) return false;
        return null;
    }
}
