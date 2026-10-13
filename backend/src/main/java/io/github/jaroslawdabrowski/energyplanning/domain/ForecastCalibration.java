package io.github.jaroslawdabrowski.energyplanning.domain;

import java.util.List;
import java.util.Optional;

/**
 * How far off the PV forecast has been, learned from measured periods: the ratio of PV
 * actually produced to PV forecast, over recent periods of the same window (morning and
 * evening are calibrated separately - with east/west planes they can be off differently).
 *
 * The ratio of sums, not the mean of ratios, so a dark day with 0.2 kWh forecast doesn't
 * swing it. It needs {@link #MIN_SAMPLES} periods and at least {@link #MIN_FORECAST_KWH} of
 * forecast PV in total, and is clamped to [{@link #MIN_FACTOR}, {@link #MAX_FACTOR}] - a
 * calibration that would erase the forecast or double it is more likely a broken
 * measurement than a lesson.
 */
public final class ForecastCalibration {

    public static final int MIN_SAMPLES = 3;
    static final double MIN_FORECAST_KWH = 1.0;
    static final double MIN_FACTOR = 0.3;
    static final double MAX_FACTOR = 1.5;

    private ForecastCalibration() {
    }

    /** One measured period: what was forecast for it (before any calibration) and what PV made. */
    public record Sample(double forecastKwh, double actualKwh) {
    }

    public record Factor(double value, int samples) {
    }

    public static Optional<Factor> learn(List<Sample> samples) {
        if (samples.size() < MIN_SAMPLES) {
            return Optional.empty();
        }
        double forecast = samples.stream().mapToDouble(Sample::forecastKwh).sum();
        double actual = samples.stream().mapToDouble(Sample::actualKwh).sum();
        if (forecast < MIN_FORECAST_KWH) {
            return Optional.empty();
        }
        double factor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, actual / forecast));
        return Optional.of(new Factor(Math.round(factor * 100) / 100.0, samples.size()));
    }
}
