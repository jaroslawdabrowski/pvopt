package io.github.jaroslawdabrowski.energyplanning.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanningSettingsTest {

    private static final List<String> G12 = List.of("22:00-06:00", "13:00-15:00");
    private static final Duration TWO_WEEKS = Duration.ofDays(14);
    private final LocalDateTime heatingOn = LocalDateTime.of(2026, 10, 15, 11, 20);

    @Test
    void marksTheEstimateThatChanged() {
        var before = new PlanningSettings(20, 100, 10, 4.7, 5.1, 1.2, G12);
        var after = new PlanningSettings(20, 100, 10, 5.7, 5.1, 1.2, G12).withChangesSince(before, heatingOn);

        assertThat(after.consumptionChangedAt(ChargeWindow.OVERNIGHT)).contains(heatingOn);
        assertThat(after.consumptionChangedAt(ChargeWindow.AFTERNOON)).isEmpty();
    }

    @Test
    void keepsTheDateOfAnEarlierChangeWhenSavedAgainUnchanged() {
        var changed = new PlanningSettings(20, 100, 10, 5.7, 6.1, 1.2, G12)
                .withChangesSince(new PlanningSettings(20, 100, 10, 4.7, 5.1, 1.2, G12), heatingOn);

        var savedAgain = new PlanningSettings(20, 90, 10, 5.7, 6.1, 1.2, G12)
                .withChangesSince(changed, heatingOn.plusDays(3));

        assertThat(savedAgain.consumptionChangedAt(ChargeWindow.OVERNIGHT)).contains(heatingOn);
        assertThat(savedAgain.consumptionChangedAt(ChargeWindow.AFTERNOON)).contains(heatingOn);
    }

    @Test
    void learnsOnlyFromPeriodsAfterARecentChange() {
        var settings = new PlanningSettings(20, 100, 10, 5.7, 6.1, 1.2, G12)
                .withChangesSince(new PlanningSettings(20, 100, 10, 4.7, 6.1, 1.2, G12), heatingOn);
        var now = heatingOn.plusDays(5);

        assertThat(settings.learnFrom(ChargeWindow.OVERNIGHT, now, TWO_WEEKS)).isEqualTo(heatingOn);
        assertThat(settings.learnFrom(ChargeWindow.AFTERNOON, now, TWO_WEEKS)).isEqualTo(now.minus(TWO_WEEKS));
    }

    @Test
    void anOldChangeNoLongerLimitsLearning() {
        var settings = new PlanningSettings(20, 100, 10, 5.7, 6.1, 1.2, G12)
                .withChangesSince(new PlanningSettings(20, 100, 10, 4.7, 6.1, 1.2, G12), heatingOn);
        var now = heatingOn.plusDays(30);

        assertThat(settings.learnFrom(ChargeWindow.OVERNIGHT, now, TWO_WEEKS)).isEqualTo(now.minus(TWO_WEEKS));
    }
}
