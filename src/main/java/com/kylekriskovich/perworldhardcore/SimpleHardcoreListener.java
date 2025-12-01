package com.kylekriskovich.perworldhardcore;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.GameMode;

public class SimpleHardcoreListener implements Listener {

    private final PerWorldHardcorePlugin plugin;

    public SimpleHardcoreListener(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isHardcoreWorld(World world) {
        world.isHardcore();
        return world != null && plugin.isHardcoreWorld(world.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();

        if (!isHardcoreWorld(world)) {
            return;
        }

        // Mark player as dead in THIS hardcore world
        plugin.markPlayerDeadInWorld(player.getUniqueId(), world.getName());

        plugin.getLogger().info(player.getName() + " died in hardcore world '" + world.getName() + "'.");
        event.setDeathMessage("[HardcoreTest] " + player.getName() + " lost their life in " + world.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // At this point, the player's "current world" is still the world they died in
        World deathWorld = player.getWorld();
        if (!isHardcoreWorld(deathWorld)) {
            return;
        }

        // Optional: you can also change respawn location here if you want:
        // event.setRespawnLocation(deathWorld.getSpawnLocation());

        // Set them to spectator on the next tick so vanilla doesn't override it
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage("You died in hardcore world '" + deathWorld.getName() + "'. You are now in spectator mode.");
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {

        Player player = event.getPlayer();
        if (event.getTo() == null) return;

        World targetWorld = event.getTo().getWorld();
        if (targetWorld == null) return;

        String targetName = targetWorld.getName();

        if (!plugin.isHardcoreWorld(targetName) && player.getGameMode() != GameMode.SURVIVAL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage("Welcome to '" + targetName + "'. Your Gamemode has been updated to survival mode.");
            });
            return;
        }

        // If player already died in this hardcore world, block re-entry
        if (plugin.isPlayerDeadInWorld(player.getUniqueId(), targetName)) {
            event.setCancelled(true);
            player.sendMessage("You already died in hardcore world '" + targetName + "' and cannot return.");
        }
    }


}

