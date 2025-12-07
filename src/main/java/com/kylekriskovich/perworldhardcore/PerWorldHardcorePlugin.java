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

    private final Map<String, HardcoreWorldSettings> hardcoreWorlds = new HashMap<>();

    private HardcoreDataStorage dataStorage;

    private boolean hasMultiverseInventories;
    private Plugin multiverseInventories;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        this.messageManager = new MessageManager(this);

        // Check hard deps (Core + NetherPortals)
        if (!checkHardDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setHasMultiverseInventories();

        // Make sure config.yml exists
        saveDefaultConfig();

        // Load hardcore worlds + their settings from config
        loadHardcoreWorlds();

        // Init storage (data.yml)
        dataStorage = new HardcoreDataStorage(this);
        dataStorage.init();

        getLogger().info("PerWorldHardcore enabled. Hardcore worlds: " + hardcoreWorlds.keySet());

        // Register listeners
        getServer().getPluginManager().registerEvents(
                new HardcorePlayerListener(this),
                this
        );

        // Register command
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

    public void loadHardcoreWorlds() {
        hardcoreWorlds.clear();

        ConfigurationSection groups = getConfig().getConfigurationSection("hardcore-worlds");
        if (groups == null || groups.getKeys(false).isEmpty()) {
            getLogger().warning("No hardcore-worlds defined in config.yml");
            return;
        }

        for (String groupName : groups.getKeys(false)) {
            if (groupName == null || groupName.isBlank()) {
                continue;
            }

            ConfigurationSection groupSection = groups.getConfigurationSection(groupName);
            if (groupSection == null) {
                continue;
            }

            // Build settings for this logical hardcore world (group)
            HardcoreWorldSettings settings = new HardcoreWorldSettings(groupName, getConfig());

            // Collect all backing world names for this group
            // Prefer hardcore-worlds.<group>.dimensions.*, but fall back to just the group name.
            List<String> worldNames = new ArrayList<>();

            ConfigurationSection dimSection = groupSection.getConfigurationSection("dimensions");
            if (dimSection != null && !dimSection.getKeys(false).isEmpty()) {
                for (String dimKey : dimSection.getKeys(false)) {
                    String worldName = dimSection.getString(dimKey);
                    if (worldName != null && !worldName.isBlank()) {
                        worldNames.add(worldName);
                    }
                }
            } else {
                // Backwards compatibility: treat the group name itself as the only hardcore world
                worldNames.add(groupName);
            }

            // Map each physical world name (overworld, nether, end, etc.) to the same settings instance
            for (String worldName : worldNames) {
                hardcoreWorlds.put(worldName, settings);
            }
        }

        getLogger().info("PerWorldHardcore loaded hardcore worlds: " + hardcoreWorlds.keySet());
    }

    public int getHardcoreWorldCount() {
        ConfigurationSection groups = getConfig().getConfigurationSection("hardcore-worlds");
        if (groups == null) {
            return 0;
        }
        return groups.getKeys(false).size();
    }

    public void addHardcoreWorld(String groupName,
                                      Map<HardcoreDimension, String> dimensionWorldNames,
                                      Boolean allowSpectatorOverride,
                                      Boolean allowTpAfterDeathOverride) {
        if (groupName == null || groupName.isBlank()) return;

        ConfigurationSection groups =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (groups == null) {
            groups = getConfig().createSection("hardcore-worlds");
        }

        ConfigurationSection groupSection =
                groups.getConfigurationSection(groupName);
        if (groupSection == null) {
            groupSection = groups.createSection(groupName);
        }

        ConfigurationSection dimSection =
                groupSection.getConfigurationSection("dimensions");
        if (dimSection == null) {
            dimSection = groupSection.createSection("dimensions");
        }

        for (HardcoreDimension dim : HardcoreDimension.values()) {
            String worldName = dimensionWorldNames.get(dim);
            if (worldName != null && !worldName.isBlank()) {
                dimSection.set(dim.getConfigKey(), worldName);
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
                groupSection.getConfigurationSection("settings");
        if (settingsSection == null) {
            settingsSection = groupSection.createSection("settings");
        }
        settingsSection.set("allow-spectator-on-death", spectator);
        settingsSection.set("allow-tp-after-death", tpAfterDeath);

        saveConfig();

        // Rebuild in-memory map so listeners immediately see the new worlds
        loadHardcoreWorlds();
    }

    public void removeWorldGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) return;

        java.util.List<String> worlds = getWorldNamesForGroup(groupName);

        // Remove player state for each physical world
        if (dataStorage != null) {
            for (String worldName : worlds) {
                dataStorage.removeWorldData(worldName);
            }
        }

        // Remove from in-memory hardcoreWorlds map
        for (String worldName : worlds) {
            hardcoreWorlds.remove(worldName);
        }

        // Remove group from config
        ConfigurationSection groups =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (groups != null) {
            groups.set(groupName, null);
        } else {
            // Fallback in case there is no section for some reason
            getConfig().set("hardcore-worlds." + groupName, null);
        }

        saveConfig();

        // Rebuild map from config to keep everything consistent
        loadHardcoreWorlds();
    }

    public java.util.List<String> getWorldNamesForGroup(String groupName) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (groupName == null || groupName.isBlank()) return result;

        ConfigurationSection groups =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (groups == null) return result;

        ConfigurationSection groupSection = groups.getConfigurationSection(groupName);
        if (groupSection == null) {
            // Fallback: just treat the group name as the only world
            result.add(groupName);
            return result;
        }

        ConfigurationSection dimSection = groupSection.getConfigurationSection("dimensions");
        if (dimSection != null && !dimSection.getKeys(false).isEmpty()) {
            for (String key : dimSection.getKeys(false)) {
                String worldName = dimSection.getString(key);
                if (worldName != null && !worldName.isBlank()) {
                    result.add(worldName);
                }
            }
        } else {
            // No dimensions block – fallback to group name
            result.add(groupName);
        }

        return result;
    }

    // ------------------------------------------------------------------------
    // Player state helpers (proxy to HardcoreDataStorage)
    // ------------------------------------------------------------------------

    public boolean hasDiedInWorld(UUID uuid, String worldName) {
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

    public java.util.Set<String> findCullableGroups() {
        if (dataStorage == null) {
            return java.util.Collections.emptySet();
        }

        // Current world-level candidates
        java.util.Set<String> hardcoreWorldNames = getHardcoreWorlds();
        java.util.Set<String> worldCandidates =
                dataStorage.findCullableWorlds(hardcoreWorldNames, getHubWorldName());

        ConfigurationSection groups =
                getConfig().getConfigurationSection("hardcore-worlds");
        if (groups == null) {
            return java.util.Collections.emptySet();
        }

        java.util.Set<String> result = new java.util.HashSet<>();

        for (String groupName : groups.getKeys(false)) {
            if (groupName == null || groupName.isBlank()) continue;

            java.util.List<String> worldsInGroup = getWorldNamesForGroup(groupName);
            if (worldsInGroup.isEmpty()) continue;

            // If ANY world in this group is cullable, treat the whole group as cullable
            boolean anyCullable = false;
            for (String worldName : worldsInGroup) {
                if (worldCandidates.contains(worldName)) {
                    anyCullable = true;
                    break;
                }
            }

            if (anyCullable) {
                result.add(groupName);
            }
        }

        return result;
    }


    public void removeWorldData(String worldName) {
        if (dataStorage != null) {
            dataStorage.removeWorldData(worldName);
        }

        // Also keep the hardcore list and config in sync
        hardcoreWorlds.remove(worldName);

        List<String> current = getConfig().getStringList("hardcore-worlds");
        current.removeIf(w -> w.equalsIgnoreCase(worldName));
        getConfig().set("hardcore-worlds", current);
        saveConfig();
    }


    public Set<String> getHardcoreWorlds() {
        return new HashSet<>(hardcoreWorlds.keySet());
    }

    // ------------------------------------------------------------------------
    // Dependency related functions
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

    @SuppressWarnings( "unused")
    public MessageManager getMessageManager() {
        return messageManager;
    }

    public boolean isHardcoreWorld(String worldName) {
        return worldName != null && hardcoreWorlds.containsKey(worldName);
    }

    public HardcoreWorldSettings getHardcoreWorldSettings(String worldName) {
        return hardcoreWorlds.get(worldName);
    }

    // Global defaults (still used for now, and for new worlds)
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
