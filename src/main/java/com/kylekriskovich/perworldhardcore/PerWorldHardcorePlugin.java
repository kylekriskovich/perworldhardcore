package com.kylekriskovich.perworldhardcore;

import com.kylekriskovich.perworldhardcore.listener.SimpleHardcoreListener;
import com.kylekriskovich.perworldhardcore.command.HcpwCommand;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.World;

import java.util.*;
import java.util.Objects;

import java.io.File;
import java.io.IOException;


public class PerWorldHardcorePlugin extends JavaPlugin {

    private final Set<String> hardcoreWorlds = new HashSet<>();
    private final Map<UUID, Set<String>> deadWorlds = new HashMap<>();
    private final Map<UUID, Set<String>> visitedWorlds = new HashMap<>();


    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadHardcoreWorlds();
        loadPlayerData();

        // Register events
        getServer().getPluginManager().registerEvents(new SimpleHardcoreListener(this), this);

        // Register commands
        Objects.requireNonNull(getCommand("hcpw"))
                .setExecutor(new HcpwCommand(this));

        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreWorlds);
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

    public void markPlayerVisitedWorld(UUID uuid, String worldName) {
        if (uuid == null || worldName == null) return;
        visitedWorlds.computeIfAbsent(uuid, k -> new HashSet<>()).add(worldName);
    }

    public Set<String> findCullableWorlds() {
        Set<String> result = new HashSet<>();

        for (String worldName : hardcoreWorlds) {
            // Don't ever cull hub world, even if it's listed as hardcore
            if (worldName.equalsIgnoreCase(getHubWorldName())) continue;

            // Build visitor set for this world
            Set<UUID> visitors = new HashSet<>();
            for (Map.Entry<UUID, Set<String>> entry : visitedWorlds.entrySet()) {
                if (entry.getValue().contains(worldName)) {
                    visitors.add(entry.getKey());
                }
            }

            if (visitors.isEmpty()) {
                // No visits = nothing to cull yet
                continue;
            }

            boolean allDead = true;
            for (UUID uuid : visitors) {
                if (!isPlayerDeadInWorld(uuid, worldName)) {
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

    public void removeWorldData(String worldName) {
        // Remove from hardcore list
        hardcoreWorlds.remove(worldName);

        // Remove from player death/visit maps
        for (Set<String> worlds : deadWorlds.values()) {
            worlds.remove(worldName);
        }
        for (Set<String> worlds : visitedWorlds.values()) {
            worlds.remove(worldName);
        }

        // Also update config hardcore-worlds list
        List<String> current = getConfig().getStringList("hardcore-worlds");
        current.removeIf(w -> w.equalsIgnoreCase(worldName));
        getConfig().set("hardcore-worlds", current);
        saveConfig();
        savePlayerData();
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
        visitedWorlds.clear();

        if (dataConfig == null || !dataConfig.isConfigurationSection("players")) {
            return;
        }

        for (String uuidStr : dataConfig.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                List<String> dead = dataConfig.getStringList("players." + uuidStr + ".dead-worlds");
                List<String> visited = dataConfig.getStringList("players." + uuidStr + ".visited-worlds");

                deadWorlds.put(uuid, new HashSet<>(dead));
                visitedWorlds.put(uuid, new HashSet<>(visited));
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

            // ALSO save visited worlds
            Set<String> visited = visitedWorlds.getOrDefault(entry.getKey(), Collections.emptySet());
            dataConfig.set("players." + uuidStr + ".visited-worlds",
                    new ArrayList<>(visited));
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

    public Set<String> getHardcoreWorlds() {
        return new HashSet<>(hardcoreWorlds);
    }

    public void addHardcoreWorld(String worldName) {
        hardcoreWorlds.add(worldName);
        List<String> current = getConfig().getStringList("hardcore-worlds");
        if (!current.contains(worldName)) {
            current.add(worldName);
            getConfig().set("hardcore-worlds", current);
            saveConfig();
        }
    }

    public boolean isAllowSpectatorOnDeath() {
        return getConfig().getBoolean("allow-spectator-on-death", true);
    }

    public boolean isAllowTpAfterDeath() {
        return getConfig().getBoolean("allow-tp-after-death", false);
    }

    public int getMaxOpenHardcoreWorlds() {
        return getConfig().getInt("max-open-hardcore-worlds", 3);
    }

    public String getHubWorldName() {
        return getConfig().getString("hub-world", "world");
    }

    public World getHubWorld() {
        return Bukkit.getWorld(getHubWorldName());
    }

}
