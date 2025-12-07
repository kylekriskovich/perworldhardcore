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
     * Key used under hardcore-worlds.<group>.dimensions.<configKey>
     * e.g. "overworld", "nether", "end".
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Suffix appended to the groupName to form the actual Bukkit world name.
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
     * Compute the world name for this dimension given the group (base) name.
     */
    public String worldNameForGroup(String groupName) {
        return groupName + worldNameSuffix;
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
