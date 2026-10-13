package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Expected PV per hour slot of a period, starting at {@code from}: slot i covers
 * {@code from + i hours} for {@code slotHours.get(i)} hours (only the last can be shorter).
 */
public record ProductionProfile(LocalDateTime from, List<Double> kwhPerSlot, List<Double> slotHours) {

    public ProductionProfile {
        if (kwhPerSlot.size() != slotHours.size()) {
            throw new IllegalArgumentException("one length per slot");
        }
        kwhPerSlot = List.copyOf(kwhPerSlot);
        slotHours = List.copyOf(slotHours);
    }

    public double totalKwh() {
        return kwhPerSlot.stream().mapToDouble(Double::doubleValue).sum();
    }

    public ProductionProfile scaledBy(double factor) {
        return new ProductionProfile(from, kwhPerSlot.stream().map(kwh -> kwh * factor).toList(), slotHours);
    }

    /** Slot boundaries for a period, one hour at a time. */
    public static List<Double> slotHoursFor(ExpensivePeriod period) {
        var hours = new ArrayList<Double>();
        double remaining = period.hours();
        while (remaining > 1e-9) {
            double slot = Math.min(1.0, remaining);
            hours.add(slot);
            remaining -= slot;
        }
        return hours;
    }
}
