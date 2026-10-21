package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.DailyCost;
import io.github.jaroslawdabrowski.energyplanning.domain.EnergyCostCalculator;
import io.github.jaroslawdabrowski.energyplanning.domain.MonthlyCosts;
import io.github.jaroslawdabrowski.energyplanning.domain.PricingNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.domain.TariffCalendar;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetMonthlyCostsUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.EnergyRecordPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.MarketPricePort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prices the hourly counter snapshots: a month at a time, each day on its own. Market prices
 * come from PSE; if they can't be fetched the month is still shown, exports valued at 0 and
 * flagged, rather than the whole page failing.
 */
@ApplicationScoped
public class CostReporter implements GetMonthlyCostsUseCase {

    private static final Logger LOG = Logger.getLogger(CostReporter.class);

    private final ProcessSettingsPort settingsPort;
    private final EnergyRecordPort energyRecordPort;
    private final MarketPricePort marketPricePort;
    private final ClockPort clockPort;

    public CostReporter(ProcessSettingsPort settingsPort, EnergyRecordPort energyRecordPort,
            MarketPricePort marketPricePort, ClockPort clockPort) {
        this.settingsPort = settingsPort;
        this.energyRecordPort = energyRecordPort;
        this.marketPricePort = marketPricePort;
        this.clockPort = clockPort;
    }

    @Override
    public MonthlyCosts monthlyCosts(YearMonth month) {
        var settings = settingsPort.load().orElseThrow(ProcessSettingsNotConfiguredException::new);
        var pricing = settings.pricingSettings().orElseThrow(PricingNotConfiguredException::new);
        var calendar = new TariffCalendar(TariffWindowConfigParser.parse(settings.planning().cheapTariffWindows()));

        LocalDate today = clockPort.nowLocal().toLocalDate();
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth().isAfter(today) ? today : month.atEndOfMonth();
        if (last.isBefore(first)) {
            return new MonthlyCosts(month, List.of(), pricing, false);
        }
        var snapshotsByDay = energyRecordPort.findSnapshotsBetween(first.atStartOfDay(), last.plusDays(1).atStartOfDay())
                .stream().collect(Collectors.groupingBy(s -> s.takenAt().toLocalDate()));

        Map<LocalDateTime, Double> prices;
        boolean pricesUnavailable = false;
        try {
            prices = snapshotsByDay.isEmpty() ? Map.of() : marketPricePort.hourlyExportPrices(first, last);
        } catch (RuntimeException e) {
            LOG.warnf("Market prices unavailable for %s: %s", month, e.getMessage());
            prices = Map.of();
            pricesUnavailable = true;
        }

        var days = new ArrayList<DailyCost>();
        for (var day = first; !day.isAfter(last); day = day.plusDays(1)) {
            var snapshots = snapshotsByDay.get(day);
            if (snapshots != null && !snapshots.isEmpty()) {
                days.add(EnergyCostCalculator.dailyCost(day, snapshots, calendar, pricing,
                        EnergyCostCalculator.lookup(prices)));
            }
        }
        return new MonthlyCosts(month, days, pricing, pricesUnavailable);
    }
}
