package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * What a kWh costs, gross, as on the invoice - every per-kWh line of the zone added up
 * (energy, variable network fee, quality, OZE and cogeneration fees, VAT): for PGE G12 in
 * September 2026 that was 1.249 zł in the expensive zone and 0.611 zł in the cheap one.
 * {@code fixedMonthlyGross} is what's due regardless of use (fixed network fee, capacity fee,
 * subscription). Exported energy is valued at the hourly market price (RCE) times
 * {@code exportMultiplier} - since February 2025 the prosumer deposit is credited at 1.23x.
 */
public record PricingSettings(
        double cheapRateGross,
        double expensiveRateGross,
        double fixedMonthlyGross,
        double exportMultiplier) {

    public static final double DEFAULT_EXPORT_MULTIPLIER = 1.23;

    public PricingSettings {
        if (cheapRateGross <= 0 || expensiveRateGross <= 0) {
            throw new IllegalArgumentException("both zone rates must be positive");
        }
        if (fixedMonthlyGross < 0) {
            throw new IllegalArgumentException("fixedMonthlyGross cannot be negative");
        }
        if (exportMultiplier <= 0) {
            throw new IllegalArgumentException("exportMultiplier must be positive");
        }
    }

    public double rateFor(TariffRate rate) {
        return rate == TariffRate.CHEAP ? cheapRateGross : expensiveRateGross;
    }
}
