package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * SOC thresholds for the planning engine, supplied from configuration. How much forecast
 * is "required" to skip grid charging differs per {@link ChargeWindow} (a full day's
 * consumption estimate for the overnight decision, a partial-day one for the afternoon
 * decision) - that computation is an application-layer concern, not part of this config.
 *
 * @param minSocPercent  absolute minimum SOC - below it we always charge from the grid,
 *                       regardless of forecast (power-supply safety net)
 * @param fullSocPercent target SOC level for a full grid charge
 */
public record PlanningPolicyConfig(int minSocPercent, int fullSocPercent) {

    public PlanningPolicyConfig {
        if (minSocPercent < 0 || minSocPercent > 100 || fullSocPercent < 0 || fullSocPercent > 100) {
            throw new IllegalArgumentException("SOC thresholds must be between 0 and 100");
        }
        if (minSocPercent > fullSocPercent) {
            throw new IllegalArgumentException("minSocPercent cannot be greater than fullSocPercent");
        }
    }
}
