package com.kylekriskovich.perworldhardcore.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerWorldState {

    private final UUID playerId;

    /**
     * In the storage layer, "worldName" refers to a Bukkit world name
     * (i.e. a single dimension such as "hc-1" or "hc-1_nether").
     * The plugin layer groups these into hardcore worlds.
     */
    private final Set<String> deadWorlds = new HashSet<>();
    private final Set<String> visitedWorlds = new HashSet<>();

    public PlayerWorldState(UUID playerId) {
        this.playerId = playerId;
    }

    @SuppressWarnings("unused")
    public UUID getPlayerId() {
        return playerId;
    }

    // --- Dead worlds --------------------------------------------------------

    public boolean isDeadIn(String worldName) {
        return deadWorlds.contains(worldName);
    }

    public void markDeadIn(String worldName) {
        if (worldName != null) {
            deadWorlds.add(worldName);
        }
    }

    public Set<String> getDeadWorlds() {
        return Collections.unmodifiableSet(deadWorlds);
    }

    // --- Visited worlds -----------------------------------------------------

    public boolean hasVisited(String worldName) {
        return visitedWorlds.contains(worldName);
    }

    public void markVisited(String worldName) {
        if (worldName != null) {
            visitedWorlds.add(worldName);
        }
    }

    public Set<String> getVisitedWorlds() {
        return Collections.unmodifiableSet(visitedWorlds);
    }

    // --- Helpers for cleanup ------------------------------------------------

    public void removeWorld(String worldName) {
        if (worldName == null) return;
        deadWorlds.remove(worldName);
        visitedWorlds.remove(worldName);
    }
}
