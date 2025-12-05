package com.kylekriskovich.perworldhardcore.storage;

import com.kylekriskovich.perworldhardcore.PerWorldHardcorePlugin;
import com.kylekriskovich.perworldhardcore.model.PlayerWorldState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class HardcoreDataStorage {

    private final PerWorldHardcorePlugin plugin;

    private File dataFile;
    private FileConfiguration dataConfig;

    // All per-player state lives here
    private final Map<UUID, PlayerWorldState> players = new HashMap<>();

    public HardcoreDataStorage(PerWorldHardcorePlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void init() {
        setupDataFile();
        loadPlayerData();
    }

    public void shutdown() {
        savePlayerData();
    }

    // -----------------------------------------------------------------------
    // Public API used by plugin / listeners / commands
    // -----------------------------------------------------------------------

    private PlayerWorldState getOrCreateState(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerWorldState::new);
    }

    public boolean isPlayerDeadInWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return false;
        PlayerWorldState state = players.get(uuid);
        return state != null && state.isDeadIn(worldName);
    }

    public void markPlayerDeadInWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return;
        PlayerWorldState state = getOrCreateState(uuid);
        state.markDeadIn(worldName);
        plugin.getLogger().info("Marked " + uuid + " as dead in hardcore world '" + worldName + "'.");
        savePlayerData(); // fine for a small plugin
    }

    public void markPlayerVisitedWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return;
        PlayerWorldState state = getOrCreateState(uuid);
        state.markVisited(worldName);
        savePlayerData();
    }

    /**
     * Returns all hardcore worlds where:
     * - At least one player has visited, AND
     * - Every visitor is dead in that world.
     */
    public Set<String> findCullableWorlds(Set<String> hardcoreWorlds, String hubWorldName) {
        Set<String> result = new HashSet<>();

        for (String worldName : hardcoreWorlds) {
            if (worldName.equalsIgnoreCase(hubWorldName)) {
                // Never auto-cull the hub
                continue;
            }

            // Collect all players who have visited this world
            List<PlayerWorldState> visitors = new ArrayList<>();
            for (PlayerWorldState state : players.values()) {
                if (state.hasVisited(worldName)) {
                    visitors.add(state);
                }
            }

            if (visitors.isEmpty()) {
                // Nobody has been there → can't say "everyone died"
                continue;
            }

            boolean allDead = true;
            for (PlayerWorldState state : visitors) {
                if (!state.isDeadIn(worldName)) {
                    allDead = false;
                    break;
                }
            }

            if (allDead) {
                result.add(worldName);
            }
        }

        return result;
    }

    /**
     * Remove all stored state for a world (after it has been deleted).
     * Does NOT touch the hardcore-worlds config list – plugin handles that.
     */
    public void removeWorldData(String worldName) {
        if (worldName == null) return;

        for (PlayerWorldState state : players.values()) {
            state.removeWorld(worldName);
        }
        savePlayerData();
    }

    // -----------------------------------------------------------------------
    // File / YAML internals
    // -----------------------------------------------------------------------

    private void setupDataFile() {
        if (!plugin.getDataFolder().exists()) {
            boolean createdDir = plugin.getDataFolder().mkdirs();
            if (!createdDir) {
                plugin.getLogger().warning("Could not create plugin data folder: "
                        + plugin.getDataFolder().getAbsolutePath());
            }
        }

        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                boolean createdFile = dataFile.createNewFile();
                if (!createdFile) {
                    plugin.getLogger().warning("data.yml file already existed or could not be created.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create data.yml", e);
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadPlayerData() {
        players.clear();

        if (dataConfig == null) {
            return;
        }

        ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                PlayerWorldState state = new PlayerWorldState(uuid);

                List<String> dead = dataConfig.getStringList("players." + uuidStr + ".dead-worlds");
                List<String> visited = dataConfig.getStringList("players." + uuidStr + ".visited-worlds");

                for (String w : dead) {
                    state.markDeadIn(w);
                }
                for (String w : visited) {
                    state.markVisited(w);
                }

                players.put(uuid, state);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in data.yml: " + uuidStr);
            }
        }

        plugin.getLogger().info("Loaded dead/visited data for " + players.size() + " players.");
    }

    private void savePlayerData() {
        if (dataConfig == null || dataFile == null) return;

        dataConfig.set("players", null); // clear section

        for (Map.Entry<UUID, PlayerWorldState> entry : players.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerWorldState state = entry.getValue();

            dataConfig.set("players." + uuidStr + ".dead-worlds",
                    new ArrayList<>(state.getDeadWorlds()));
            dataConfig.set("players." + uuidStr + ".visited-worlds",
                    new ArrayList<>(state.getVisitedWorlds()));
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }
}
