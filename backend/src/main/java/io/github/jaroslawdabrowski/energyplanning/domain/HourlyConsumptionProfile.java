package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * When in the day the house uses its energy, learned from hourly counter snapshots: the
 * mean load of each hour of the day over recent days. Only the shape is used - how a
 * period's consumption estimate spreads over its hours - so the morning kettle and the
 * evening cooking peak show up in the reserve. Until every hour of a period has enough
 * samples, the period is assumed flat (a recuperator's constant base load makes that a
 * fair first guess).
 */
public final class HourlyConsumptionProfile {

    public static final int MIN_SAMPLES = 3;

    private final Map<Integer, Double> meanKwhByHour;

    private HourlyConsumptionProfile(Map<Integer, Double> meanKwhByHour) {
        this.meanKwhByHour = Map.copyOf(meanKwhByHour);
    }

    /** Consecutive snapshots an hour apart (±5 min) on the same day give that hour's load. */
    public static HourlyConsumptionProfile learn(List<EnergySnapshot> snapshots) {
        var byDay = snapshots.stream().collect(Collectors.groupingBy(s -> s.takenAt().toLocalDate()));
        Map<Integer, List<Double>> samples = new HashMap<>();
        for (Map.Entry<LocalDate, List<EnergySnapshot>> day : byDay.entrySet()) {
            var sorted = new ArrayList<>(day.getValue());
            sorted.sort(Comparator.comparing(EnergySnapshot::takenAt));
            for (int i = 1; i < sorted.size(); i++) {
                var earlier = sorted.get(i - 1);
                var later = sorted.get(i);
                long minutes = Duration.between(earlier.takenAt(), later.takenAt()).toMinutes();
                if (Math.abs(minutes - 60) <= 5 && earlier.takenAt().getMinute() == 0) {
                    double load = later.counters().minus(earlier.counters()).loadKwh();
                    samples.computeIfAbsent(earlier.takenAt().getHour(), h -> new ArrayList<>()).add(load);
                }
            }
        }
        Map<Integer, Double> means = new HashMap<>();
        samples.forEach((hour, loads) -> {
            if (loads.size() >= MIN_SAMPLES) {
                means.put(hour, loads.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            }
        });
        return new HourlyConsumptionProfile(means);
    }

    public static HourlyConsumptionProfile none() {
        return new HourlyConsumptionProfile(Map.of());
    }

    public Optional<Double> meanKwh(int hourOfDay) {
        return Optional.ofNullable(meanKwhByHour.get(hourOfDay));
    }

    /**
     * Spreads {@code totalKwh} over the period's hour slots: by the learned shape when every
     * slot's hour has one, otherwise in proportion to slot length.
     */
    public List<Double> spread(double totalKwh, LocalDateTime from, List<Double> slotHours) {
        List<Double> weights = new ArrayList<>();
        boolean learned = true;
        for (int i = 0; i < slotHours.size(); i++) {
            var mean = meanKwh(from.plusHours(i).getHour());
            if (mean.isEmpty()) {
                learned = false;
                break;
            }
            weights.add(mean.get() * slotHours.get(i));
        }
        if (!learned || weights.stream().mapToDouble(Double::doubleValue).sum() <= 0) {
            weights = new ArrayList<>(slotHours);
        }
        double sum = weights.stream().mapToDouble(Double::doubleValue).sum();
        return weights.stream().map(w -> totalKwh * w / sum).toList();
    }

    /** Whether the period's slots are spread by a learned shape (for the reason params). */
    public boolean covers(LocalDateTime from, int slots) {
        for (int i = 0; i < slots; i++) {
            if (meanKwh(from.plusHours(i).getHour()).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
