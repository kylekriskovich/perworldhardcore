package com.kylekriskovich.perworldhardcore.command;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.HardcoreDimension;
import org.bukkit.Bukkit;
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
        // Now returns hardcore world ids (config keys)
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

        sender.sendMessage("Deleting worlds via Multiverse...");

        for (String worldId : worldCandidates) {
            // Ask plugin for the physical Bukkit world names (dimensions)
            List<String> dimensionNames = plugin.getDimensionNamesForWorld(worldId);

            for (String dimensionName : dimensionNames) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + dimensionName);
            }

            // Remove the hardcore world + its data
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

        String worldId = args[1];

        int maxWorlds = plugin.getMaxOpenHardcoreWorlds();
        int currentWorlds = plugin.getHardcoreWorldCount();
        if (currentWorlds >= maxWorlds) {
            sender.sendMessage("Cannot create more hardcore worlds. Limit is " + maxWorlds + " groups.");
            return;
        }

        // Ensure this hardcore world doesn't already exist in config
        if (plugin.hardcoreWorldExists(worldId)) {
            sender.sendMessage("Hardcore world '" + worldId + "' already exists.");
            return;
        }

        Boolean allowSpectatorOverride = null;
        Boolean allowTpAfterDeathOverride = null;

        List<String> mvArgs = new ArrayList<>();
        boolean hasSeedArg = false;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];

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

            if (arg.equalsIgnoreCase("-s") && i + 1 < args.length) {
                hasSeedArg = true;
                mvArgs.add(arg); // "-s"
                mvArgs.add(args[++i]); // seed value
                continue;
            }

            if (arg.startsWith("--seed=")) {
                hasSeedArg = true;
                mvArgs.add(arg);
                continue;
            }

            mvArgs.add(arg);
        }

        if (!hasSeedArg) {
            long seed = new Random().nextLong();
            mvArgs.add("-s");
            mvArgs.add(Long.toString(seed));
        }

        // Build the backing dimension names for this hardcore world
        Map<HardcoreDimension, String> dimensionNames = new EnumMap<>(HardcoreDimension.class);
        for (HardcoreDimension dim : HardcoreDimension.values()) {
            String dimensionWorldName = dim.worldNameForWorld(worldId); // was worldNameForGroup
            dimensionNames.put(dim, dimensionWorldName);
        }

        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            sender.sendMessage("Multiverse-Core is not available. Cannot create worlds.");
            return;
        }

        String extraArgs = "";
        if (!mvArgs.isEmpty()) {
            extraArgs = " " + String.join(" ", mvArgs);
        }

        sender.sendMessage("Creating hardcore world group '" + worldId + "' via Multiverse...");
        for (HardcoreDimension dim : HardcoreDimension.values()) {
            String dimensionWorldName = dimensionNames.get(dim);
            String env = dim.getMultiverseEnvironment();
            sender.sendMessage("  " + dim.name() + ": " + dimensionWorldName);
            dispatchConsole(sender, "mv create " + dimensionWorldName + " " + env + extraArgs);
        }

        // Register the hardcore world + config
        plugin.addHardcoreWorld(
                worldId,
                dimensionNames,
                allowSpectatorOverride,
                allowTpAfterDeathOverride
        );

        sender.sendMessage("Hardcore world group '" + worldId + "' created and registered.");
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private void dispatchConsole(CommandSender feedbackTarget, String command) {
        feedbackTarget.sendMessage(" > " + command);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private Boolean parseBooleanFlag(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase();
        if (v.equals("true") || v.equals("yes") || v.equals("y")) return true;
        if (v.equals("false") || v.equals("no") || v.equals("n")) return false;
        return null;
    }
}
