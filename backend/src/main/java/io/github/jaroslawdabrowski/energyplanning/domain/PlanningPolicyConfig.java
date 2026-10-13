package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Battery facts for the planning engine, supplied from configuration. How much forecast
 * is "required" to skip grid charging differs per {@link ChargeWindow} - that computation
 * is an application-layer concern, not part of this config.
 *
 * @param minSocPercent      absolute minimum SOC - below it we always charge from the grid,
 *                           regardless of forecast (power-supply safety net)
 * @param fullSocPercent     the highest SOC a grid charge may target
 * @param batteryCapacityKwh usable battery capacity; {@code 0} means unknown - then a grid charge
 *                           always targets {@code fullSocPercent} instead of just the missing energy
 */
public record PlanningPolicyConfig(int minSocPercent, int fullSocPercent, double batteryCapacityKwh) {

    public PlanningPolicyConfig {
        if (minSocPercent < 0 || minSocPercent > 100 || fullSocPercent < 0 || fullSocPercent > 100) {
            throw new IllegalArgumentException("SOC thresholds must be between 0 and 100");
        }
        if (minSocPercent > fullSocPercent) {
            throw new IllegalArgumentException("minSocPercent cannot be greater than fullSocPercent");
        }
        if (batteryCapacityKwh < 0) {
            throw new IllegalArgumentException("batteryCapacityKwh cannot be negative, was " + batteryCapacityKwh);
        }
    }

    public boolean knowsCapacity() {
        return batteryCapacityKwh > 0;
    }
}
