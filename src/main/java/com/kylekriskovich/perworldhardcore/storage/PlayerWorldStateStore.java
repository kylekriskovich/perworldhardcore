package com.kylekriskovich.perworldhardcore.storage;

import com.kylekriskovich.perworldhardcore.model.PlayerWorldState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerWorldStateStore {

    private final Map<UUID, PlayerWorldState> states = new HashMap<>();

    public PlayerWorldState getOrCreate(UUID playerId) {
        return states.computeIfAbsent(playerId, PlayerWorldState::new);
    }

    public PlayerWorldState get(UUID playerId) {
        return states.get(playerId);
    }

    public void clear(UUID playerId) {
        states.remove(playerId);
    }
}