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

    /**
     * Map of Bukkit dimension names -> hardcore world settings.
     * (e.g. "hc-1", "hc-1_nether", "hc-1_the_end" all point to the same settings instance)
     */
    private final Map<String, HardcoreWorldSettings> hardcoreDimensions = new HashMap<>();

    private HardcoreDataStorage dataStorage;

    private boolean hasMultiverseInventories;
    private Plugin multiverseInventories;
    private MessageManager messageManager;

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    // Hardcore world registry / config
    // ------------------------------------------------------------------------

    /**
     * Load hardcore worlds (player-facing worlds) and their backing dimensions
     * from config into the in-memory dimension map.
     */
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
                // Backwards compatibility: treat the world name itself as a single dimension
                dimensionNames.add(worldName);
            }

            for (String dimensionName : dimensionNames) {
                hardcoreDimensions.put(dimensionName, settings);
            }
        }

        getLogger().info("PerWorldHardcore loaded hardcore worlds: " + hardcoreDimensions.keySet());
    }

    /**
     * Number of hardcore worlds (player-facing) defined in config.
     */
    public int getHardcoreWorldCount() {
        ConfigurationSection worldsSection = getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null) {
            return 0;
        }
        return worldsSection.getKeys(false).size();
    }

    /**
     * Add a new hardcore world (player-facing), with its backing dimensions and settings,
     * to config and reload the in-memory mappings.
     */
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

    /**
     * Remove a hardcore world (player-facing) and all its backing dimensions from
     * player state, in-memory registry, and config.
     */
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

    /**
     * Return the Bukkit dimension names for a given hardcore world (player-facing).
     * Exposed so command handlers can call Multiverse with the actual world names
     * without needing to know how they are derived.
     */
    public List<String> getDimensionNamesForWorld(String worldName) {
        List<String> result = new ArrayList<>();
        if (worldName == null || worldName.isBlank()) return result;

        ConfigurationSection worldsSection =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (worldsSection == null) return result;

        ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
        if (worldSection == null) {
            // Legacy: treat the world name itself as the only dimension
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
            // Legacy
            result.add(worldName);
        }

        return result;
    }

    /**
     * Find hardcore worlds (player-facing) that are cullable, based on dimension-level
     * cullable data from the data store.
     *
     * @return set of hardcore world ids (config keys under "hardcore-worlds")
     */
    public Set<String> findCullableWorlds() {
        if (dataStorage == null) {
            return Collections.emptySet();
        }

        // Dimension-level candidates
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

            // If ANY dimension in this world is cullable, treat the whole world as cullable
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

    /**
     * Does a hardcore world (player-facing) with this name exist in config?
     */
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

    // ------------------------------------------------------------------------
    // Player state helpers (world-based)
    // ------------------------------------------------------------------------

    /**
     * World-level (player-facing): interpret the given Bukkit world as part of a hardcore
     * world and check across all of that world's dimensions.
     */
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

    /**
     * Mark a player as dead in all dimensions of the hardcore world that this Bukkit world belongs to.
     */
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

    /**
     * Mark a player as having visited all dimensions of the hardcore world that this Bukkit world belongs to.
     */
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

    // ------------------------------------------------------------------------
    // Runtime query helpers / config access
    // ------------------------------------------------------------------------

    /**
     * Is this Bukkit world part of any hardcore world?
     */
    public boolean isHardcoreWorld(World bukkitWorld) {
        if (bukkitWorld == null) return false;
        return isHardcoreDimension(bukkitWorld.getName());
    }

    /**
     * Get settings for the hardcore world that this Bukkit world belongs to.
     */
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

    // ------------------------------------------------------------------------
    // Dependency / integration helpers
    // ------------------------------------------------------------------------

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

    // ------------------------------------------------------------------------
    // Private helpers (dimensions / storage)
    // ------------------------------------------------------------------------

    /**
     * All hardcore dimensions (Bukkit world names) currently registered.
     */
    private Set<String> getHardcoreDimensions() {
        return new HashSet<>(hardcoreDimensions.keySet());
    }

    /**
     * Given a dimension name (Bukkit world name), return the hardcore world name it belongs to.
     */
    private String getWorldNameForDimension(String dimensionName) {
        if (dimensionName == null) return null;
        HardcoreWorldSettings settings = hardcoreDimensions.get(dimensionName);
        if (settings == null) return null;

        // In the current design, HardcoreWorldSettings worldName == hardcore world id
        return settings.getWorldName();
    }

    /**
     * Remove all player data for a specific Bukkit dimension.
     */
    private void removeDimensionData(String dimensionName) {
        if (dataStorage != null) {
            dataStorage.removeWorldData(dimensionName);
        }
    }

    /**
     * Dimension-level check: is this Bukkit world name part of any hardcore world?
     */
    private boolean isHardcoreDimension(String dimensionName) {
        return dimensionName != null && hardcoreDimensions.containsKey(dimensionName);
    }

    /**
     * Dimension-level: get settings for the hardcore world this dimension belongs to.
     */
    private HardcoreWorldSettings getHardcoreWorldSettingsForDimension(String dimensionName) {
        return hardcoreDimensions.get(dimensionName);
    }
}
