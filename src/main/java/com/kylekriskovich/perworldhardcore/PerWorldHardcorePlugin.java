package com.kylekriskovich.perworldhardcore;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

import java.io.File;
import java.io.IOException;


public class PerWorldHardcorePlugin extends JavaPlugin {

    private final Set<String> hardcoreWorlds = new HashSet<>();
    private final Map<UUID, Set<String>> deadWorlds = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataFile();
        loadPlayerData();

        loadHardcoreWorlds();
        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreWorlds);

        Bukkit.getPluginManager().registerEvents(
                new SimpleHardcoreListener(this),
                this
        );
    }

    public void loadHardcoreWorlds() {
        hardcoreWorlds.clear();
        List<String> worldsFromConfig = getConfig().getStringList("hardcore-worlds");
        getLogger().info("Config hardcore-worlds raw: " + worldsFromConfig);

        if (worldsFromConfig == null || worldsFromConfig.isEmpty()) {
            // Fallback so you're never stuck with an empty set while testing
            //hardcoreWorlds.add("world");
            getLogger().warning("No hardcore-worlds defined in config.yml");
            return;
        }

        for (String name : worldsFromConfig) {
            if (name != null && !name.isBlank()) {
                hardcoreWorlds.add(name);
            }
        }
    }

    public boolean isHardcoreWorld(String worldName) {
        return worldName != null && hardcoreWorlds.contains(worldName);
    }

    public boolean isPlayerDeadInWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return false;
        Set<String> worlds = deadWorlds.get(uuid);
        return worlds != null && worlds.contains(worldName);
    }

    public void markPlayerDeadInWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return;
        deadWorlds.computeIfAbsent(uuid, k -> new HashSet<>()).add(worldName);
        getLogger().info("Marked " + uuid + " as dead in hardcore world '" + worldName + "'.");
        savePlayerData(); // small plugin; fine to save eagerly
    }

    private void setupDataFile() {
        if (!getDataFolder().exists()) {
            // plugins/PerWorldHardcore
            getDataFolder().mkdirs();
        }

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml");
                e.printStackTrace();
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadPlayerData() {
        deadWorlds.clear();

        if (dataConfig == null || !dataConfig.isConfigurationSection("players")) {
            return;
        }

        for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                var worlds = dataConfig.getStringList("players." + uuidStr + ".dead-worlds");
                deadWorlds.put(uuid, new HashSet<>(worlds));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Invalid UUID in data.yml: " + uuidStr);
            }
        }

        getLogger().info("Loaded dead player data for " + deadWorlds.size() + " players.");
    }

    public void savePlayerData() {
        if (dataConfig == null) return;

        dataConfig.set("players", null); // clear

        for (Map.Entry<UUID, Set<String>> entry : deadWorlds.entrySet()) {
            String uuidStr = entry.getKey().toString();
            dataConfig.set("players." + uuidStr + ".dead-worlds",
                    new ArrayList<>(entry.getValue()));
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Could not save data.yml");
            e.printStackTrace();
        }
    }


    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info("PerWorldHardcore disabled.");
    }
}
