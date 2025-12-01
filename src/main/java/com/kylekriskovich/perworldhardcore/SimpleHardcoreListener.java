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

public class SimpleHardcoreListener implements Listener {

    private final PerWorldHardcorePlugin plugin;

    public SimpleHardcoreListener(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isHardcoreWorld(World world) {
        // Phase 1: treat ONLY 'world' as hardcore.
        // Later we’ll move this into config + per-world logic.
        return world != null && "world".equals(world.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();

        if (!isHardcoreWorld(world)) {
            return;
        }

        plugin.getLogger().info(player.getName() + " died in hardcore test world '" + world.getName() + "'.");

        // For now, just change the death message so you can see it's firing
        event.setDeathMessage("[HardcoreTest] " + player.getName() + " lost their life in " + world.getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // In Phase 1 you can leave this empty or log,
        // we’ll wire it properly once you’re happy the events fire.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Same here – we’ll evolve this later.
    }
}

