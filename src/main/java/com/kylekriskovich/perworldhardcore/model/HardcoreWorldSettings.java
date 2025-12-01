package com.kylekriskovich.perworldhardcore.model;

public class HardcoreWorldSettings {

    private final String worldName;

    private boolean allowSpectatorOnDeath;
    private boolean allowTpAfterDeath;

    public HardcoreWorldSettings(String worldName) {
        this.worldName = worldName;
    }

    public String getWorldName() {
        return worldName;
    }

    public boolean isAllowSpectatorOnDeath() {
        return allowSpectatorOnDeath;
    }

    public void setAllowSpectatorOnDeath(boolean allowSpectatorOnDeath) {
        this.allowSpectatorOnDeath = allowSpectatorOnDeath;
    }

    public boolean isAllowTpAfterDeath() {
        return allowTpAfterDeath;
    }

    public void setAllowTpAfterDeath(boolean allowTpAfterDeath) {
        this.allowTpAfterDeath = allowTpAfterDeath;
    }
}
