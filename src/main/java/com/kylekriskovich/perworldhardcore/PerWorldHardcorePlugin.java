package com.kylekriskovich.perworldhardcore;

import com.kylekriskovich.perworldhardcore.listener.HardcorePlayerListener;
import com.kylekriskovich.perworldhardcore.command.HcpwCommand;
import com.kylekriskovich.perworldhardcore.storage.HardcoreDataStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.World;

import java.util.*;
import java.util.Objects;

import java.io.File;


public class PerWorldHardcorePlugin extends JavaPlugin {

    private final Set<String> hardcoreWorlds = new HashSet<>();
    private HardcoreDataStorage dataStorage;

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataStorage = new HardcoreDataStorage(this);
        dataStorage.init();

        loadHardcoreWorlds();
        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreWorlds);

        getServer().getPluginManager().registerEvents(
                new HardcorePlayerListener(this),
                this
        );

        Objects.requireNonNull(getCommand("hcpw"))
                .setExecutor(new HcpwCommand(this));
    }

    @Override
    public void onDisable() {
        if (dataStorage != null) {
            dataStorage.shutdown();
        }
        getLogger().info("PerWorldHardcore disabled.");
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

    // Called by listener / commands
    public boolean isPlayerDeadInWorld(UUID uuid, String worldName) {
        return dataStorage != null && dataStorage.isPlayerDeadInWorld(uuid, worldName);
    }

    public void markPlayerDeadInWorld(UUID uuid, String worldName) {
        if (dataStorage != null) {
            dataStorage.markPlayerDeadInWorld(uuid, worldName);
        }
    }

    public void markPlayerVisitedWorld(UUID uuid, String worldName) {
        if (dataStorage != null) {
            dataStorage.markPlayerVisitedWorld(uuid, worldName);
        }
    }

    public Set<String> findCullableWorlds() {
        if (dataStorage == null) return Collections.emptySet();
        return dataStorage.findCullableWorlds(hardcoreWorlds, getHubWorldName());
    }

    public void removeWorldData(String worldName) {
        if (dataStorage != null) {
            dataStorage.removeWorldData(worldName);
        }

        // Also keep hardcore list + config in sync
        hardcoreWorlds.remove(worldName);
        List<String> current = getConfig().getStringList("hardcore-worlds");
        current.removeIf(w -> w.equalsIgnoreCase(worldName));
        getConfig().set("hardcore-worlds", current);
        saveConfig();
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

    public boolean isHardcoreWorld(String worldName) {
        return worldName != null && hardcoreWorlds.contains(worldName);
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
