package io.github.jaroslawdabrowski.energyplanning.domain;

/** Costs were asked for, but no electricity prices have been entered in Settings yet. */
public class PricingNotConfiguredException extends RuntimeException {

    public PricingNotConfiguredException() {
        super("Electricity prices are not configured");
    }
}
