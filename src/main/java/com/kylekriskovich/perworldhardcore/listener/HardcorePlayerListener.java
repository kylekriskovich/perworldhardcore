package com.kylekriskovich.perworldhardcore.listener;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.HardcoreWorldSettings;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class HardcorePlayerListener implements Listener {

    private final PerWorldHardcorePlugin plugin;

    public HardcorePlayerListener(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    private HardcoreWorldSettings getSettings(World world) {
        if (world == null) return null;
        return plugin.getHardcoreWorldSettings(world.getName());
    }

    // 1) Mark them dead in that hardcore world
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        String worldName = world.getName();

        HardcoreWorldSettings settings = getSettings(world);
        if (settings == null) {
            // Not a configured hardcore world
            return;
        }

        // Persist "dead in this world" via HardcoreDataStorage
        plugin.markPlayerDeadInWorld(player.getUniqueId(), worldName);
        // Also track they’ve visited it (helps culling)
        plugin.markPlayerVisitedWorld(player.getUniqueId(), worldName);
    }

    // 2) On respawn, send them to hub / spectator if they died in that world
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        World deathWorld = player.getWorld(); // world they died in

        HardcoreWorldSettings settings = getSettings(deathWorld);
        if (settings == null) {
            // Not a hardcore world
            return;
        }

        if (plugin.hasDiedInWorld(playerId, deathWorld.getName())) {
            World hub = plugin.getHubWorld();
            if (hub == null) {
                plugin.getLogger().warning("Hub world not found; cannot redirect respawn.");
                return;
            }

            if (settings.isAllowSpectatorOnDeath()) {
                // Stay in hardcore world, become spectator
                plugin.getServer().getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
            } else {
                // Respawn in hub as survival
                event.setRespawnLocation(hub.getSpawnLocation());
                plugin.getServer().getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SURVIVAL));
            }
        }
    }

    // 3) On join (after logout on death), apply same rules as respawn
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        World joinWorld = player.getWorld();
        String joinWorldName = joinWorld.getName();

        HardcoreWorldSettings settings = getSettings(joinWorld);
        if (settings == null) {
            // Not a hardcore world
            return;
        }

        if (plugin.hasDiedInWorld(playerId, joinWorldName)) {
            World hub = plugin.getHubWorld();
            if (hub == null) {
                plugin.getLogger().warning("Hub world not found; cannot redirect join.");
                return;
            }

            if (settings.isAllowSpectatorOnDeath()) {
                // Let them stay in the hardcore world but as spectator
                plugin.getServer().getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
            } else {
                // Kick them to hub as survival
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.teleport(hub.getSpawnLocation());
                    player.setGameMode(GameMode.SURVIVAL);
                });
            }
        }
    }
}
