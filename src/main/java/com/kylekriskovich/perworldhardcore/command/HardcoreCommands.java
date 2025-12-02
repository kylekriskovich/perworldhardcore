package com.kylekriskovich.perworldhardcore.command;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

public class HardcoreCommands implements CommandExecutor {

    private final PerWorldHardcorePlugin plugin;

    public HardcoreCommands(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;

        // Optional sanity check – since plugin.yml has depend: [Multiverse-Core],
        // this *should* always exist, but we’ll log if not.
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            plugin.getLogger().severe("Multiverse-Core not found or not enabled! hardcore commands will be limited.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
                createHardcoreWorld(sender, args[1]);
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
        Set<String> candidates = plugin.findCullableWorlds();

        if (candidates.isEmpty()) {
            sender.sendMessage("No hardcore worlds are fully dead.");
            return;
        }

        sender.sendMessage("Cullable hardcore worlds: " + String.join(", ", candidates));

        if (!delete) {
            sender.sendMessage("Run /hardcore cull delete to actually delete them via Multiverse.");
            return;
        }

        sender.sendMessage("Deleting worlds via Multiverse...");
        for (String worldName : candidates) {
            // Use Multiverse via its command interface
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv delete " + worldName);
            plugin.removeWorldData(worldName);
        }

        sender.sendMessage("Cull completed.");
    }

    private void createHardcoreWorld(CommandSender sender, String worldName) {
        int max = plugin.getMaxOpenHardcoreWorlds();
        if (plugin.getHardcoreWorlds().size() >= max) {
            sender.sendMessage("Cannot create more hardcore worlds. Limit is " + max + ".");
            return;
        }

        // Make sure Multiverse-Core is actually loaded
        Plugin mv = Bukkit.getPluginManager().getPlugin("Multiverse-Core");
        if (mv == null || !mv.isEnabled()) {
            sender.sendMessage("Multiverse-Core is not available. Cannot create worlds.");
            return;
        }

        sender.sendMessage("Creating hardcore world '" + worldName + "' via Multiverse...");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + worldName + " normal");

        plugin.addHardcoreWorld(worldName);
        sender.sendMessage("World '" + worldName + "' added as hardcore.");
    }
}
