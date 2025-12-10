package com.kylekriskovich.perworldhardcore.model;

public enum HardcoreDimension {
    OVERWORLD("overworld", "", "NORMAL"),
    NETHER("nether", "_nether", "NETHER"),
    END("end", "_the_end", "THE_END");

    private final String configKey;
    private final String worldNameSuffix;
    private final String multiverseEnvironment;

    HardcoreDimension(String configKey, String worldNameSuffix, String multiverseEnvironment) {
        this.configKey = configKey;
        this.worldNameSuffix = worldNameSuffix;
        this.multiverseEnvironment = multiverseEnvironment;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getWorldNameSuffix() {
        return worldNameSuffix;
    }

    public String getMultiverseEnvironment() {
        return multiverseEnvironment;
    }

    public String worldNameForWorld(String worldId) {
        return worldId + worldNameSuffix;
    }
}
