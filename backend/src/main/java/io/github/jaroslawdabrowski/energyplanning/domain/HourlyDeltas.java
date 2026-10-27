package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** What the counters measured in each full hour, from consecutive snapshots an hour apart (±5 min) on the same day. */
final class HourlyDeltas {

    private HourlyDeltas() {
    }

    /** Keyed by the hour's start. Hours with a missing snapshot at either end are absent. */
    static Map<LocalDateTime, DailyEnergyCounters> of(List<EnergySnapshot> snapshots) {
        var byDay = snapshots.stream().collect(Collectors.groupingBy(s -> s.takenAt().toLocalDate()));
        var result = new TreeMap<LocalDateTime, DailyEnergyCounters>();
        for (Map.Entry<LocalDate, List<EnergySnapshot>> day : byDay.entrySet()) {
            var sorted = new ArrayList<>(day.getValue());
            sorted.sort(Comparator.comparing(EnergySnapshot::takenAt));
            for (int i = 1; i < sorted.size(); i++) {
                var earlier = sorted.get(i - 1);
                var later = sorted.get(i);
                long minutes = Duration.between(earlier.takenAt(), later.takenAt()).toMinutes();
                if (Math.abs(minutes - 60) <= 5 && earlier.takenAt().getMinute() == 0) {
                    result.put(earlier.takenAt().withSecond(0).withNano(0), later.counters().minus(earlier.counters()));
                }
            }
        }
        return result;
    }
}
