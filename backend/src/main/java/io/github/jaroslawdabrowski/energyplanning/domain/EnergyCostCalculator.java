package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns one day's hourly counter snapshots into money, the way the invoice does it: energy
 * bought priced by the tariff zone of the hour it was bought in, energy sold credited at that
 * hour's market price (RCE) times the deposit multiplier. Checked against the September 2026
 * invoice: its "RCEm 0,37477" is the export-weighted average of hourly RCE (midday hours, when
 * PV exports), not the plain monthly mean (657 zł/MWh).
 *
 * The inverter's counters reset at midnight, so the day starts from an implicit zero snapshot
 * at 00:00. A gap between snapshots longer than an hour is spread evenly over its hours and
 * the day is marked approximate.
 */
public final class EnergyCostCalculator {

    private EnergyCostCalculator() {
    }

    /**
     * @param snapshots      snapshots of that day (any order)
     * @param exportPricePln hour start -> market price in zł/kWh (net, before the multiplier); absent = unknown
     */
    public static DailyCost dailyCost(LocalDate day, List<EnergySnapshot> snapshots, TariffCalendar calendar,
            PricingSettings pricing, Function<LocalDateTime, Double> exportPricePln) {
        var points = new ArrayList<EnergySnapshot>();
        points.add(new EnergySnapshot(day.atStartOfDay(), new DailyEnergyCounters(0, 0, 0, 0, 0, 0), 0));
        snapshots.stream()
                .filter(s -> s.takenAt().toLocalDate().equals(day) && s.takenAt().isAfter(day.atStartOfDay()))
                .sorted(Comparator.comparing(EnergySnapshot::takenAt))
                .forEach(points::add);

        double importCheap = 0, importExpensive = 0, importCost = 0, export = 0, exportValue = 0, load = 0;
        double baseline = 0;
        boolean approximate = false, priceGaps = false;
        for (int i = 1; i < points.size(); i++) {
            var from = points.get(i - 1);
            var to = points.get(i);
            var delta = to.counters().minus(from.counters());
            long minutes = Duration.between(from.takenAt(), to.takenAt()).toMinutes();
            if (minutes <= 0) {
                continue;
            }
            approximate |= minutes > 65;
            for (var piece : hourPieces(from.takenAt(), to.takenAt())) {
                double share = piece.minutes() / (double) minutes;
                double rate = pricing.rateFor(calendar.rateAt(piece.start()));
                double bought = delta.gridImportKwh() * share;
                double sold = delta.gridExportKwh() * share;
                double used = delta.loadKwh() * share;
                if (calendar.rateAt(piece.start()) == TariffRate.CHEAP) {
                    importCheap += bought;
                } else {
                    importExpensive += bought;
                }
                importCost += bought * rate;
                baseline += used * rate;
                load += used;
                export += sold;
                Double price = exportPricePln.apply(piece.start().truncatedTo(ChronoUnit.HOURS));
                if (price == null && sold > 0) {
                    priceGaps = true;
                }
                // A negative market price is taken as 0 for the prosumer deposit - an assumption,
                // not checked against an invoice yet (none of the months seen had negative hours exported).
                exportValue += sold * Math.max(0, price == null ? 0 : price) * pricing.exportMultiplier();
            }
        }
        double covered = Duration.between(day.atStartOfDay(), points.getLast().takenAt()).toMinutes() / 60.0;
        // A snapshot just before midnight closes the day.
        if (points.getLast().takenAt().toLocalTime().isAfter(LocalTime.of(23, 54))) {
            covered = 24;
        }
        return new DailyCost(day, importCheap, importExpensive, importCost, export, exportValue, load, baseline,
                covered, approximate, priceGaps);
    }

    private record Piece(LocalDateTime start, long minutes) {
    }

    /** [from, to) cut at every full hour. */
    private static List<Piece> hourPieces(LocalDateTime from, LocalDateTime to) {
        var pieces = new ArrayList<Piece>();
        var cursor = from;
        while (cursor.isBefore(to)) {
            var nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            var end = nextHour.isBefore(to) ? nextHour : to;
            pieces.add(new Piece(cursor, Duration.between(cursor, end).toMinutes()));
            cursor = end;
        }
        return pieces;
    }

    /** Convenience for a fixed price table. */
    public static Function<LocalDateTime, Double> lookup(Map<LocalDateTime, Double> prices) {
        return prices::get;
    }
}
