package com.kylekriskovich.perworldhardcore.listener;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.HardcoreWorldSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HardcorePlayerListener implements Listener {

    private final PerWorldHardcorePlugin plugin;

    /**
     * Tracks the Bukkit world name (dimension) in which the player last died.
     * Used so respawn logic can reason about the correct hardcore world even if
     * the respawn happens in another dimension (e.g. die in nether, respawn in overworld).
     */
    private final Map<UUID, String> lastDeathDimension = new ConcurrentHashMap<>();

    public HardcorePlayerListener(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------
    // Death
    // ------------------------------------------------------------------------

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        if (world == null) return;

        // Only care if this dimension belongs to a hardcore world
        if (!plugin.isHardcoreWorld(world)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        String dimensionName = plugin.getHardcoreWorldId(world);

        // Remember which dimension they died in for respawn logic
        lastDeathDimension.put(playerId, dimensionName);

        // Mark death & visit at hardcore-world level (plugin handles dimension fan-out)
        plugin.markPlayerDeadInWorld(playerId, world);
        plugin.markPlayerVisitedWorld(playerId, world);
    }

    // ------------------------------------------------------------------------
    // Respawn
    // ------------------------------------------------------------------------


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Prefer the dimension they actually died in
        String deathDimensionName = lastDeathDimension.remove(playerId);
        World basisWorld = null;

        if (deathDimensionName != null) {
            basisWorld = Bukkit.getWorld(deathDimensionName);
        }

        // Fallback: use the world they are respawning into if we couldn't resolve death world
        if (basisWorld == null) {
            basisWorld = event.getRespawnLocation().getWorld();
        }

        if (basisWorld == null || !plugin.isHardcoreWorld(basisWorld)) {
            // Not in any hardcore world -> normal behaviour
            return;
        }

        HardcoreWorldSettings settings = plugin.getHardcoreWorldSettings(basisWorld);
        if (settings == null) {
            return;
        }

        // Has this player died in this hardcore world (across all its dimensions)?
        if (!plugin.hasDiedInWorld(playerId, basisWorld)) {
            return;
        }

        World hub = plugin.getHubWorld();
        if (hub == null) {
            plugin.getLogger().warning("Hub world not found; cannot redirect respawn.");
            return;
        }

        String hardcoreName = plugin.getHardcoreWorldId(basisWorld);

        if (settings.isAllowSpectatorOnDeath()) {
            // Respawn inside the hardcore world, but as spectator.
            // If respawn location is outside a hardcore world for some reason,
            // nudge them into the basisWorld spawn.
            World respawnWorld = event.getRespawnLocation().getWorld();
            if (respawnWorld == null || !plugin.isHardcoreWorld(respawnWorld)) {
                event.setRespawnLocation(basisWorld.getSpawnLocation());
            }

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.RED + "You have died in hardcore world "
                        + ChatColor.GOLD + hardcoreName
                        + ChatColor.RED + ". You may now only spectate this world.");
            }, 2L); // 2 ticks later

        } else {
            // No spectator allowed: force respawn at hub in survival
            event.setRespawnLocation(hub.getSpawnLocation());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage(ChatColor.RED + "You have died in hardcore world "
                        + ChatColor.GOLD + hardcoreName
                        + ChatColor.RED + ". You have been returned to the hub.");
            }, 2L);
        }
    }

    // ------------------------------------------------------------------------
    // Join / World change → enforce restrictions + track visits
    // ------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleEnterWorld(player, player.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleEnterWorld(player, player.getWorld());
    }

    /**
     * Common logic when a player is in some world after join/world-change.
     * Marks visit and enforces post-death restrictions if applicable.
     */
    private void handleEnterWorld(Player player, World world) {
        if (world == null) return;

        // Ignore non-hardcore worlds
        if (!plugin.isHardcoreWorld(world)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        HardcoreWorldSettings settings = plugin.getHardcoreWorldSettings(world);
        if (settings == null) {
            return;
        }

        // Mark visited at hardcore-world level
        plugin.markPlayerVisitedWorld(playerId, world);

        // If they've died in this hardcore world, enforce settings
        if (plugin.hasDiedInWorld(playerId, world)) {
            World hub = plugin.getHubWorld();
            if (hub == null) {
                plugin.getLogger().warning("Hub world not found; cannot redirect join/world-change.");
                return;
            }
            String hardcoreName = plugin.getHardcoreWorldId(world);

            if (settings.isAllowSpectatorOnDeath()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage(ChatColor.RED + "You have died in hardcore world "
                            + ChatColor.GOLD + hardcoreName
                            + ChatColor.RED + ". You may now only spectate this world.");
                }, 2L);

            } else {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.teleport(hub.getSpawnLocation());
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage(ChatColor.RED + "You cannot re-enter hardcore world "
                            + ChatColor.GOLD + hardcoreName
                            + ChatColor.RED + " because you have already died there.");
                }, 2L);

            }
        }
    }
}