package com.kylekriskovich.perworldhardcore.listener;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
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

public class HardcorePlayerListener implements Listener {

    private final PerWorldHardcorePlugin plugin;

    public HardcorePlayerListener(PerWorldHardcorePlugin plugin) {
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
        World deathWorld = player.getWorld();

        if (!isHardcoreWorld(deathWorld)) {
            return;
        }

        if (plugin.isAllowSpectatorOnDeath()) {
            // stay in current world, become spectator
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("You died in hardcore world '" + deathWorld.getName() + "'. You are now in spectator mode.");
            });
        } else {
            // Send to hub-world as survival
            World hub = plugin.getHubWorld();
            if (hub != null) {
                event.setRespawnLocation(hub.getSpawnLocation());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage("You died in hardcore world '" + deathWorld.getName()
                            + "'. Returning you to hub '" + hub.getName() + "'.");
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() == null || event.getFrom() == null) return;

        World fromWorld = event.getFrom().getWorld();
        World targetWorld = event.getTo().getWorld();
        if (fromWorld == null || targetWorld == null) return;

        String targetName = targetWorld.getName();

        if (plugin.isHardcoreWorld(targetName)) {
            plugin.markPlayerVisitedWorld(player.getUniqueId(), targetName);
        }

        // Not a hardcore world? We don't care.
        if (!plugin.isHardcoreWorld(targetName)) {
            return;
        }

        // Only care about cross-world teleports (spectator zipping around same world is fine)
        boolean isWorldChange = !fromWorld.getName().equals(targetName);
        if (!isWorldChange) {
            return;
        }

        // If they haven't died in this world, TP is fine.
        if (!plugin.isPlayerDeadInWorld(player.getUniqueId(), targetName)) {
            return;
        }

        // They DID die in this hardcore world.
        if (plugin.isAllowTpAfterDeath()) {
            // Config says we allow it – maybe they're a ghost spectator, so do nothing.
            return;
        }

        // Config says: NO TP after death → send to hub.
        World hub = plugin.getHubWorld();
        event.setCancelled(true);

        if (hub != null && !hub.getName().equals(targetName)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(hub.getSpawnLocation());
                player.sendMessage("You already died in hardcore world '" + targetName
                        + "'. Sending you to hub '" + hub.getName() + "'.");
            });
        } else {
            player.sendMessage("You already died in hardcore world '" + targetName + "' and cannot return.");
        }
    }


}

