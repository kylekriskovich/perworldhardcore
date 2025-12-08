package com.kylekriskovich.perworldhardcore;

import com.kylekriskovich.perworldhardcore.command.HardcoreCommands;
import com.kylekriskovich.perworldhardcore.listener.HardcorePlayerListener;
import com.kylekriskovich.perworldhardcore.model.HardcoreDimension;
import com.kylekriskovich.perworldhardcore.model.HardcoreWorldSettings;
import com.kylekriskovich.perworldhardcore.storage.HardcoreDataStorage;
import com.kylekriskovich.perworldhardcore.util.MessageManager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PerWorldHardcorePlugin extends JavaPlugin {

    private final Map<String, HardcoreWorldSettings> hardcoreDimensions = new HashMap<>();

    private HardcoreDataStorage dataStorage;

    private boolean hasMultiverseInventories;
    private Plugin multiverseInventories;
    private MessageManager messageManager;


    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);

        if (!checkHardDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setHasMultiverseInventories();

        saveDefaultConfig();

        loadHardcoreWorlds();

        dataStorage = new HardcoreDataStorage(this);
        dataStorage.init();

        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreDimensions.keySet());

        getServer().getPluginManager().registerEvents(
                new HardcorePlayerListener(this),
                this
        );

        Objects.requireNonNull(getCommand("hardcore"))
                .setExecutor(new HardcoreCommands(this));
    }

    @Override
    public void onDisable() {
        if (dataStorage != null) {
            dataStorage.shutdown();
        }
        getLogger().info("PerWorldHardcore disabled.");
    }

    // Hardcore world registry

    public void loadHardcoreWorlds() {
        hardcoreDimensions.clear();

        ConfigurationSection worldsSection = getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null || worldsSection.getKeys(false).isEmpty()) {
            getLogger().warning("No hardcore-worlds defined in config.yml");
            return;
        }

        for (String worldName : worldsSection.getKeys(false)) {
            if (worldName == null || worldName.isBlank()) {
                continue;
            }

            ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
            if (worldSection == null) {
                continue;
            }

            HardcoreWorldSettings settings = new HardcoreWorldSettings(worldName, getConfig());

            List<String> dimensionNames = new ArrayList<>();

            ConfigurationSection dimSection = worldSection.getConfigurationSection("dimensions");
            if (dimSection != null && !dimSection.getKeys(false).isEmpty()) {
                for (String dimKey : dimSection.getKeys(false)) {
                    String dimensionName = dimSection.getString(dimKey);
                    if (dimensionName != null && !dimensionName.isBlank()) {
                        dimensionNames.add(dimensionName);
                    }
                }
            } else {
                dimensionNames.add(worldName);
            }

            for (String dimensionName : dimensionNames) {
                hardcoreDimensions.put(dimensionName, settings);
            }
        }

        getLogger().info("PerWorldHardcore loaded hardcore worlds: " + hardcoreDimensions.keySet());
    }

    public int getHardcoreWorldCount() {
        ConfigurationSection worldsSection = getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null) {
            return 0;
        }
        return worldsSection.getKeys(false).size();
    }

    public void addHardcoreWorld(String worldName,
                                 Map<HardcoreDimension, String> dimensionNames,
                                 Boolean allowSpectatorOverride,
                                 Boolean allowTpAfterDeathOverride) {
        if (worldName == null || worldName.isBlank()) return;

        ConfigurationSection worldsSection =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null) {
            worldsSection = getConfig().createSection("hardcore-worlds");
        }

        ConfigurationSection worldSection =
                worldsSection.getConfigurationSection(worldName);
        if (worldSection == null) {
            worldSection = worldsSection.createSection(worldName);
        }

        ConfigurationSection dimSection =
                worldSection.getConfigurationSection("dimensions");
        if (dimSection == null) {
            dimSection = worldSection.createSection("dimensions");
        }

        for (HardcoreDimension dim : HardcoreDimension.values()) {
            String dimensionName = dimensionNames.get(dim);
            if (dimensionName != null && !dimensionName.isBlank()) {
                dimSection.set(dim.getConfigKey(), dimensionName);
            }
        }

        ConfigurationSection defaults =
                getConfig().getConfigurationSection("defaults_settings");
        boolean defaultSpectator =
                defaults != null && defaults.getBoolean("allow-spectator-on-death", true);
        boolean defaultTp =
                defaults != null && defaults.getBoolean("allow-tp-after-death", false);

        boolean spectator =
                allowSpectatorOverride != null ? allowSpectatorOverride : defaultSpectator;
        boolean tpAfterDeath =
                allowTpAfterDeathOverride != null ? allowTpAfterDeathOverride : defaultTp;

        ConfigurationSection settingsSection =
                worldSection.getConfigurationSection("settings");
        if (settingsSection == null) {
            settingsSection = worldSection.createSection("settings");
        }
        settingsSection.set("allow-spectator-on-death", spectator);
        settingsSection.set("allow-tp-after-death", tpAfterDeath);

        saveConfig();

        loadHardcoreWorlds();
    }

    public void removeHardcoreWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return;

        List<String> dimensionNames = getDimensionNamesForWorld(worldName);

        for (String dimensionName : dimensionNames) {
            removeDimensionData(dimensionName);
            hardcoreDimensions.remove(dimensionName);
        }

        ConfigurationSection worldsSection =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection != null) {
            worldsSection.set(worldName, null);
        } else {
            getConfig().set("hardcore-worlds." + worldName, null);
        }

        saveConfig();

        loadHardcoreWorlds();
    }

    public List<String> getDimensionNamesForWorld(String worldName) {
        List<String> result = new ArrayList<>();
        if (worldName == null || worldName.isBlank()) return result;

        ConfigurationSection worldsSection =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null) return result;

        ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
        if (worldSection == null) {
            result.add(worldName);
            return result;
        }

        ConfigurationSection dimSection = worldSection.getConfigurationSection("dimensions");
        if (dimSection != null && !dimSection.getKeys(false).isEmpty()) {
            for (String key : dimSection.getKeys(false)) {
                String dimensionName = dimSection.getString(key);
                if (dimensionName != null && !dimensionName.isBlank()) {
                    result.add(dimensionName);
                }
            }
        } else {
            result.add(worldName);
        }

        return result;
    }

    public Set<String> findCullableWorlds() {
        if (dataStorage == null) {
            return Collections.emptySet();
        }

        Set<String> dimensionNames = getHardcoreDimensions();
        Set<String> dimensionCandidates =
                dataStorage.findCullableWorlds(dimensionNames, getHubWorldName());

        ConfigurationSection worldsSection =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();

        for (String worldName : worldsSection.getKeys(false)) {
            if (worldName == null || worldName.isBlank()) continue;

            List<String> dimensionsInWorld = getDimensionNamesForWorld(worldName);
            if (dimensionsInWorld.isEmpty()) continue;

            boolean anyCullable = false;
            for (String dimensionName : dimensionsInWorld) {
                if (dimensionCandidates.contains(dimensionName)) {
                    anyCullable = true;
                    break;
                }
            }

            if (anyCullable) {
                result.add(worldName);
            }
        }

        return result;
    }

    public boolean hardcoreWorldExists(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }

        ConfigurationSection worldsSection = getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null) {
            return false;
        }

        return worldsSection.isConfigurationSection(worldName);
    }

    // Player state helpers

    public boolean hasDiedInWorld(UUID playerId, World bukkitWorld) {
        if (dataStorage == null || bukkitWorld == null) return false;

        String dimensionName = bukkitWorld.getName();
        String worldName = getWorldNameForDimension(dimensionName);
        if (worldName == null) return false;

        List<String> dimensionsInWorld = getDimensionNamesForWorld(worldName);
        for (String dimName : dimensionsInWorld) {
            if (dataStorage.isPlayerDeadInWorld(playerId, dimName)) {
                return true;
            }
        }
        return false;
    }

    public void markPlayerDeadInWorld(UUID playerId, World bukkitWorld) {
        if (dataStorage == null || bukkitWorld == null) return;

        String dimensionName = bukkitWorld.getName();
        String worldName = getWorldNameForDimension(dimensionName);
        if (worldName == null) return;

        List<String> dimensionsInWorld = getDimensionNamesForWorld(worldName);
        for (String dimName : dimensionsInWorld) {
            dataStorage.markPlayerDeadInWorld(playerId, dimName);
        }
    }

    public void markPlayerVisitedWorld(UUID playerId, World bukkitWorld) {
        if (dataStorage == null || bukkitWorld == null) return;

        String dimensionName = bukkitWorld.getName();
        String worldName = getWorldNameForDimension(dimensionName);
        if (worldName == null) return;

        List<String> dimensionsInWorld = getDimensionNamesForWorld(worldName);
        for (String dimName : dimensionsInWorld) {
            dataStorage.markPlayerVisitedWorld(playerId, dimName);
        }
    }

    // Config access

    public boolean isHardcoreWorld(World bukkitWorld) {
        if (bukkitWorld == null) return false;
        return isHardcoreDimension(bukkitWorld.getName());
    }

    public HardcoreWorldSettings getHardcoreWorldSettings(World bukkitWorld) {
        if (bukkitWorld == null) return null;
        return getHardcoreWorldSettingsForDimension(bukkitWorld.getName());
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

    // Dependency / integration helpers

    public boolean checkHardDependencies() {
        PluginManager pm = getServer().getPluginManager();

        if (pm.getPlugin("Multiverse-Core") == null) {
            getLogger().severe("Multiverse-Core not found. Disabling plugin.");
            return false;
        }

        if (pm.getPlugin("Multiverse-NetherPortals") == null) {
            getLogger().severe("Multiverse-NetherPortals not found. Disabling plugin.");
            return false;
        }
        return true;
    }

    public void setHasMultiverseInventories() {
        PluginManager pm = getServer().getPluginManager();

        Plugin inventoriesPlugin = pm.getPlugin("Multiverse-Inventories");
        if (inventoriesPlugin != null && inventoriesPlugin.isEnabled()) {
            this.multiverseInventories = inventoriesPlugin;
            this.hasMultiverseInventories = true;
            getLogger().info("Hooked into Multiverse-Inventories.");
        } else {
            this.hasMultiverseInventories = false;
            getLogger().info("Multiverse-Inventories not found; inventory features disabled.");
        }
    }

    @SuppressWarnings("unused")
    public boolean hasMultiverseInventories() {
        return hasMultiverseInventories;
    }

    @SuppressWarnings("unused")
    public Plugin getMultiverseInventories() {
        return multiverseInventories;
    }

    @SuppressWarnings("unused")
    public MessageManager getMessageManager() {
        return messageManager;
    }

    // Private helpers (dimensions / storage)

    private Set<String> getHardcoreDimensions() {
        return new HashSet<>(hardcoreDimensions.keySet());
    }

    private String getWorldNameForDimension(String dimensionName) {
        if (dimensionName == null) return null;
        HardcoreWorldSettings settings = hardcoreDimensions.get(dimensionName);
        if (settings == null) return null;

        return settings.getWorldName();
    }

    private void removeDimensionData(String dimensionName) {
        if (dataStorage != null) {
            dataStorage.removeWorldData(dimensionName);
        }
    }

    private boolean isHardcoreDimension(String dimensionName) {
        return dimensionName != null && hardcoreDimensions.containsKey(dimensionName);
    }

    private HardcoreWorldSettings getHardcoreWorldSettingsForDimension(String dimensionName) {
        return hardcoreDimensions.get(dimensionName);
    }
}
