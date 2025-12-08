package com.kylekriskovich.perworldhardcore.listener;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.HardcoreWorldSettings;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class HardcorePlayerListener implements Listener {

    private final PerWorldHardcorePlugin plugin;

    public HardcorePlayerListener(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();

        HardcoreWorldSettings settings = getSettings(world);
        if (settings == null) {
            return;
        }

        // Track visit + death for this hardcore world (all its dimensions)
        UUID playerId = player.getUniqueId();
        plugin.markPlayerVisitedWorld(playerId, world);
        plugin.markPlayerDeadInWorld(playerId, world);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        World deathWorld = player.getWorld();

        HardcoreWorldSettings settings = getSettings(deathWorld);
        if (settings == null) {
            return;
        }

        if (plugin.hasDiedInWorld(playerId, deathWorld)) {
            World hub = plugin.getHubWorld();
            if (hub == null) {
                plugin.getLogger().warning("Hub world not found; cannot redirect respawn.");
                return;
            }

            if (settings.isAllowSpectatorOnDeath()) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.setGameMode(GameMode.SPECTATOR));
            } else {
                event.setRespawnLocation(hub.getSpawnLocation());
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.setGameMode(GameMode.SURVIVAL));
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        World joinWorld = player.getWorld();

        HardcoreWorldSettings settings = getSettings(joinWorld);
        if (settings == null) {
            return;
        }

        // Joining into a hardcore world counts as a visit
        plugin.markPlayerVisitedWorld(playerId, joinWorld);

        if (plugin.hasDiedInWorld(playerId, joinWorld)) {
            World hub = plugin.getHubWorld();
            if (hub == null) {
                plugin.getLogger().warning("Hub world not found; cannot redirect join.");
                return;
            }

            if (settings.isAllowSpectatorOnDeath()) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> player.setGameMode(GameMode.SPECTATOR));
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.teleport(hub.getSpawnLocation());
                    player.setGameMode(GameMode.SURVIVAL);
                });
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World toWorld = player.getWorld();

        HardcoreWorldSettings settings = getSettings(toWorld);
        if (settings != null) {
            // Any time a player enters a hardcore world, count it as a visit
            plugin.markPlayerVisitedWorld(player.getUniqueId(), toWorld);
        }
    }

    // ------------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------------

    private HardcoreWorldSettings getSettings(World world) {
        if (world == null) return null;
        return plugin.getHardcoreWorldSettings(world);
    }
}
