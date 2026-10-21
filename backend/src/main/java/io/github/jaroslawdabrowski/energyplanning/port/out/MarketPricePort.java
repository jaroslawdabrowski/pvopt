package io.github.jaroslawdabrowski.energyplanning.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Hourly market prices (RCE) - what exported energy is credited at under net-billing. */
public interface MarketPricePort {

    /** Hour start (local time) -> price in zł/kWh, net, for every hour published in [from, to]. */
    Map<LocalDateTime, Double> hourlyExportPrices(LocalDate from, LocalDate to);
}
