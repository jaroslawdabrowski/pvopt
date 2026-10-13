package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class HourlyConsumptionProfileTest {

    private final LocalDateTime six = LocalDateTime.of(2026, 1, 20, 6, 0);
    private final List<Double> threeHours = Collections.nCopies(3, 1.0);

    @Test
    void spreadsFlatUntilEveryHourHasEnoughSamples() {
        var profile = HourlyConsumptionProfile.learn(days(2, 1.2, 0.6, 0.3));

        assertThat(profile.spread(3.0, six, threeHours)).containsExactly(1.0, 1.0, 1.0);
        assertThat(profile.covers(six, 3)).isFalse();
    }

    @Test
    void spreadsByTheLearnedShape() {
        var profile = HourlyConsumptionProfile.learn(days(3, 1.2, 0.6, 0.3));

        assertThat(profile.covers(six, 3)).isTrue();
        var spread = profile.spread(3.0, six, threeHours);
        assertThat(spread.get(0)).isCloseTo(1.714, offset(1e-3));
        assertThat(spread.get(1)).isCloseTo(0.857, offset(1e-3));
        assertThat(spread.get(2)).isCloseTo(0.429, offset(1e-3));
    }

    @Test
    void ignoresSnapshotsThatArentAnHourApart() {
        var snapshots = List.of(snapshot(LocalDateTime.of(2026, 1, 20, 6, 0), 0),
                snapshot(LocalDateTime.of(2026, 1, 20, 8, 0), 2.0));

        assertThat(HourlyConsumptionProfile.learn(snapshots).meanKwh(6)).isEmpty();
    }

    /** {@code count} days of hourly snapshots from 06:00, loads per hour as given. */
    private static List<EnergySnapshot> days(int count, double... loads) {
        var snapshots = new ArrayList<EnergySnapshot>();
        for (int d = 0; d < count; d++) {
            LocalDate day = LocalDate.of(2026, 1, 10 + d);
            double cumulative = 3.0;
            snapshots.add(snapshot(day.atTime(6, 0), cumulative));
            for (int h = 0; h < loads.length; h++) {
                cumulative += loads[h];
                snapshots.add(snapshot(day.atTime(7 + h, 0), cumulative));
            }
        }
        return snapshots;
    }

    private static EnergySnapshot snapshot(LocalDateTime at, double loadKwh) {
        return new EnergySnapshot(at, new DailyEnergyCounters(0, loadKwh, 0, 0, 0, 0), 50);
    }
}
