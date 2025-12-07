package com.kylekriskovich.perworldhardcore.command;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.HardcoreDimension;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.*;

import org.jetbrains.annotations.NotNull;

public class HardcoreCommands implements CommandExecutor {

    private final PerWorldHardcorePlugin plugin;

    public HardcoreCommands(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;

        // Optional sanity check – since plugin.yml has depend: [Multiverse-Core],
        // this *should* always exist, but we’ll log if not.
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            plugin.getLogger().severe("Multiverse-Core Dependency not found or not enabled!");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args)
    {
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
                    sender.sendMessage("Usage: /hardcore create <worldName>");
                    return true;
                }
                createHardcoreWorld(sender, new String[]{args[1]});
                return true;

            default:
                sender.sendMessage("/hardcore reload | status <world> | cull [delete] | create <name>");
                return true;
        }
    }

    private void statusWorld(CommandSender sender, String worldName) {
        World world = Bukkit.getWorld(worldName);
        boolean exists = world != null;
        boolean hardcore = plugin.isHardcoreWorld(worldName);

        sender.sendMessage("World: " + worldName);
        sender.sendMessage("  Exists: " + exists);
        sender.sendMessage("  Hardcore: " + hardcore);
    }

    private void cullWorlds(CommandSender sender, boolean delete) {
        // Now we work with group names, not individual dimension worlds
        java.util.Set<String> groupCandidates = plugin.findCullableGroups();

        if (groupCandidates.isEmpty()) {
            sender.sendMessage("No hardcore worlds are fully dead.");
            return;
        }

        sender.sendMessage("Cullable hardcore worlds: " + String.join(", ", groupCandidates));

        if (!delete) {
            sender.sendMessage("Run /hardcore cull delete to actually delete them via Multiverse.");
            return;
        }

        sender.sendMessage("Deleting worlds via Multiverse...");

        for (String groupName : groupCandidates) {
            // All dimension worlds for this hardcore world
            java.util.List<String> worldNames = plugin.getWorldNamesForGroup(groupName);

            for (String worldName : worldNames) {
                // Use Multiverse via its command interface
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
            }

            // Remove group data + config + in-memory mappings
            plugin.removeWorldGroup(groupName);
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

        String groupName = args[1];

        // --------------------------------------------------------------------
        // Check max-open-hardcore-worlds based on group count, not dimensions
        // --------------------------------------------------------------------
        int maxGroups = plugin.getMaxOpenHardcoreWorlds();
        int currentGroups = plugin.getHardcoreWorldCount();
        if (currentGroups >= maxGroups) {
            sender.sendMessage("Cannot create more hardcore worlds. Limit is " + maxGroups + " groups.");
            return;
        }

        // Ensure this group doesn't already exist in config
//        ConfigurationSection groupsSection =
//                plugin.getConfig().getConfigurationSection("hardcore-worlds");
//        if (groupsSection != null && groupsSection.isConfigurationSection(groupName)) {
//            sender.sendMessage("Hardcore world '" + groupName + "' already exists.");
//            return;
//        }

        // --------------------------------------------------------------------
        // Parse args: plugin flags vs Multiverse args
        // --------------------------------------------------------------------
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

            // Seed flags: recognise but keep them as mv args
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

            // Everything else is passed straight to mv create
            mvArgs.add(arg);
        }

        // If user didn't give a seed, generate one and add "-s <seed>"
        if (!hasSeedArg) {
            long seed = new Random().nextLong();
            mvArgs.add("-s");
            mvArgs.add(Long.toString(seed));
        }

        // --------------------------------------------------------------------
        // Build world names per dimension using the enum
        // --------------------------------------------------------------------
        Map<HardcoreDimension, String> dimensionNames = new EnumMap<>(HardcoreDimension.class);
        for (HardcoreDimension dim : HardcoreDimension.values()) {
            String worldName = dim.worldNameForGroup(groupName);
            dimensionNames.put(dim, worldName);
        }

        // Ensure Multiverse-Core is available
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            sender.sendMessage("Multiverse-Core is not available. Cannot create worlds.");
            return;
        }

        String extraArgs = "";
        if (!mvArgs.isEmpty()) {
            extraArgs = " " + String.join(" ", mvArgs);
        }

        sender.sendMessage("Creating hardcore world group '" + groupName + "' via Multiverse...");
        for (HardcoreDimension dim : HardcoreDimension.values()) {
            String worldName = dimensionNames.get(dim);
            String env = dim.getMultiverseEnvironment();
            sender.sendMessage("  " + dim.name() + ": " + worldName);
            dispatchConsole(sender, "mv create " + worldName + " " + env + extraArgs);
        }

        // --------------------------------------------------------------------
        // Register group in config + reload in-memory settings
        // --------------------------------------------------------------------
        plugin.addHardcoreWorld(
                groupName,
                dimensionNames,
                allowSpectatorOverride,
                allowTpAfterDeathOverride
        );

        sender.sendMessage("Hardcore world group '" + groupName + "' created and registered.");
    }

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
