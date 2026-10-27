package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionProfile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public interface ForecastPort {

    /** Expected PV for each hour of a period (today and tomorrow are available). */
    ProductionProfile productionProfile(ExpensivePeriod period, ForecastSettings settings);

    /**
     * What the same forecast had for each hour of past days [from, to] (kWh, keyed by the hour's
     * start) - compared with measured PV to learn how far off it runs at each hour of the day.
     */
    Map<LocalDateTime, Double> pastHourlyProduction(LocalDate from, LocalDate to, ForecastSettings settings);
}
