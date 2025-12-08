package com.kylekriskovich.perworldhardcore.model;

public enum HardcoreDimension {
    OVERWORLD("overworld", "", "normal"),
    NETHER("nether", "_nether", "nether"),
    END("end", "_the_end", "end");

    private final String configKey;
    private final String worldNameSuffix;
    private final String multiverseEnvironment;

    HardcoreDimension(String configKey, String worldNameSuffix, String multiverseEnvironment) {
        this.configKey = configKey;
        this.worldNameSuffix = worldNameSuffix;
        this.multiverseEnvironment = multiverseEnvironment;
    }

    /**
     * Key used under hardcore-worlds.<worldId>.dimensions.<configKey>
     * e.g. "overworld", "nether", "end".
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Suffix appended to the hardcore world id to form the actual Bukkit world name.
     * e.g. "", "_nether", "_the_end".
     */
    public String getWorldNameSuffix() {
        return worldNameSuffix;
    }

    /**
     * Environment string passed to Multiverse for mv create.
     * e.g. "normal", "nether", "end".
     */
    public String getMultiverseEnvironment() {
        return multiverseEnvironment;
    }

    /**
     * Compute the Bukkit world name for this dimension given the hardcore world id.
     * For example, worldId "hc-1" becomes "hc-1", "hc-1_nether", "hc-1_the_end".
     */
    public String worldNameForWorld(String worldId) {
        return worldId + worldNameSuffix;
    }

    /**
     * Resolve a dimension from a config key ("overworld", "nether", "end").
     */
    public static HardcoreDimension fromConfigKey(String key) {
        if (key == null) return null;
        String lower = key.toLowerCase();
        for (HardcoreDimension dim : values()) {
            if (dim.configKey.equals(lower)) {
                return dim;
            }
        }
        return null;
    }
}
