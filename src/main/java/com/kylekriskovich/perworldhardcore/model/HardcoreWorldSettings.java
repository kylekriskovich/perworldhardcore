package com.kylekriskovich.perworldhardcore.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class HardcoreWorldSettings {

    private final String worldName;

    private boolean allowSpectatorOnDeath;
    private boolean allowTpAfterDeath;

    public HardcoreWorldSettings(String worldName, FileConfiguration config) {
        this.worldName = worldName;

        ConfigurationSection defaults =
                config.getConfigurationSection("defaults_settings");
        if (defaults == null) {
            defaults = config.getConfigurationSection("defaults_Settings");
        }

        boolean defaultSpectator =
                defaults != null && defaults.getBoolean("allow-spectator-on-death", true);
        boolean defaultTp =
                defaults != null && defaults.getBoolean("allow-tp-after-death", false);

        // World-specific settings under hardcore-worlds.<worldName>
        ConfigurationSection worldsSection = config.getConfigurationSection("hardcore-worlds");
        ConfigurationSection worldSection =
                worldsSection != null ? worldsSection.getConfigurationSection(worldName) : null;

        ConfigurationSection settingsSection = null;
        if (worldSection != null) {
            settingsSection = worldSection.getConfigurationSection("settings");
            if (settingsSection == null) {
                settingsSection = worldSection;
            }
        }

        this.allowSpectatorOnDeath =
                settingsSection != null
                        ? settingsSection.getBoolean("allow-spectator-on-death", defaultSpectator)
                        : defaultSpectator;

        this.allowTpAfterDeath =
                settingsSection != null
                        ? settingsSection.getBoolean("allow-tp-after-death", defaultTp)
                        : defaultTp;
    }

    @SuppressWarnings("unused")
    public String getWorldName() {
        return worldName;
    }

    public boolean isAllowSpectatorOnDeath() {
        return allowSpectatorOnDeath;
    }

    @SuppressWarnings("unused")
    public boolean isAllowTpAfterDeath() {
        return allowTpAfterDeath;
    }

    public void setAllowSpectatorOnDeath(boolean allowSpectatorOnDeath) {
        this.allowSpectatorOnDeath = allowSpectatorOnDeath;
    }

    public void setAllowTpAfterDeath(boolean allowTpAfterDeath) {
        this.allowTpAfterDeath = allowTpAfterDeath;
    }
}
