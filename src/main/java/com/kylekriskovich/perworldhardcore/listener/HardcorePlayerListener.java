package com.kylekriskovich.perworldhardcore.listener;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.HardcoreWorldSettings;
import com.kylekriskovich.perworldhardcore.model.PlayerWorldState;
import com.kylekriskovich.perworldhardcore.storage.PlayerWorldStateStore;
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
    private final HardcoreWorldSettings hardcoreWorldSettings;
    private final PlayerWorldStateStore stateStore;

    public HardcorePlayerListener(PerWorldHardcorePlugin plugin,
                                  HardcoreWorldSettings hardcoreWorldSettings,
                                  PlayerWorldStateStore stateStore) {
        this.plugin = plugin;
        this.hardcoreWorldSettings = hardcoreWorldSettings;
        this.stateStore = stateStore;
    }

    // 1) Mark them dead in that hardcore world
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        String worldName = world.getName();

        if (!hardcoreWorldSettings.isHardcoreWorld(worldName)) {
            return;
        }

        PlayerWorldState state = stateStore.getOrCreate(player.getUniqueId());
        state.markDeadIn(worldName);
    }

    // 2) On respawn, send them to hub / spectator if they died in that world
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerWorldState state = stateStore.get(playerId);

        if (state == null) {
            return;
        }

        String deathWorldName = player.getWorld().getName();
        if (!state.isDeadIn(deathWorldName)) {
            return;
        }

        World hub = plugin.getServer().getWorld(hardcoreWorldSettings.getHubWorldName());
        if (hub == null) {
            plugin.getLogger().warning("Hub world not found; cannot redirect respawn.");
            return;
        }

        event.setRespawnLocation(hub.getSpawnLocation());

        // Respect your config flags here if you have them
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR); // or SURVIVAL if “teleport to hub” only
        });
    }

    // 3) On join (after logout on death), force them out of hardcore world
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerWorldState state = stateStore.get(playerId);

        if (state == null) {
            return;
        }

        String joinWorldName = player.getWorld().getName();
        if (!state.isDeadIn(joinWorldName)) {
            // they might join the hub directly, that’s fine
            return;
        }

        World hub = plugin.getServer().getWorld(hardcoreWorldSettings.getHubWorldName());
        if (hub == null) {
            plugin.getLogger().warning("Hub world not found; cannot redirect join.");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.teleport(hub.getSpawnLocation());
            player.setGameMode(GameMode.SPECTATOR); // or hub mode
        });
    }
}
