package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.YearMonth;
import java.util.List;

/**
 * A month's electricity in money, day by day - only days with measurements. Fixed fees are
 * kept apart: they're the same whatever the house does, so they'd only blur what changed.
 */
public record MonthlyCosts(YearMonth month, List<DailyCost> days, PricingSettings pricing, boolean marketPricesUnavailable) {

    public MonthlyCosts {
        days = List.copyOf(days);
    }
}
