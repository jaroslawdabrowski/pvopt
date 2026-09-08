package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

/**
 * {@code configured=false} means nothing has been saved yet - {@code settings} is null
 * and the frontend renders a blank form instead of trying to prefill one.
 */
public record ProcessSettingsStatusResponse(boolean configured, ProcessSettingsPayload settings) {
}
