package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How far off the PV forecast has been at each hour of the day, learned by comparing what the
 * panels measured in each hour (hourly counter snapshots) with what the forecast had for that
 * same hour. Per hour, not one factor per period: in late October the forecast had the
 * east plane producing from 08:00, but measured PV was 19% of it at 07-08, 54% at 08-09, 73% at
 * 09-10 and above 100% after noon - morning fog and low cloud the models miss, or shade. A single
 * factor nearly cancelled that out over the morning, while the battery ran empty by 09:00.
 *
 * Per hour: the ratio of sums (measured / forecast) over the days that have both, once there are
 * {@link #MIN_DAYS} of them and at least {@link #MIN_FORECAST_KWH} of forecast in total (a dark
 * hour's ratio says nothing); clamped to [{@link #MIN_FACTOR}, {@link #MAX_FACTOR}]. Any other
 * hour keeps the forecast as it is.
 */
public final class HourlyForecastCalibration {

    public static final int MIN_DAYS = 3;
    static final double MIN_FORECAST_KWH = 0.3;
    static final double MIN_FACTOR = 0.1;
    static final double MAX_FACTOR = 2.0;

    private final Map<Integer, Double> factorByHour;
    private final Map<Integer, Integer> daysByHour;

    private HourlyForecastCalibration(Map<Integer, Double> factorByHour, Map<Integer, Integer> daysByHour) {
        this.factorByHour = Map.copyOf(factorByHour);
        this.daysByHour = Map.copyOf(daysByHour);
    }

    /**
     * @param snapshots       hourly counter snapshots of recent days
     * @param forecastByHour  forecast PV (kWh) for those days, keyed by each hour's start
     */
    public static HourlyForecastCalibration learn(List<EnergySnapshot> snapshots,
            Map<LocalDateTime, Double> forecastByHour) {
        Map<Integer, double[]> sums = new HashMap<>();
        Map<Integer, Set<LocalDate>> days = new HashMap<>();
        HourlyDeltas.of(snapshots).forEach((hourStart, delta) -> {
            Double forecast = forecastByHour.get(hourStart);
            if (forecast != null) {
                double[] sum = sums.computeIfAbsent(hourStart.getHour(), h -> new double[2]);
                sum[0] += delta.pvKwh();
                sum[1] += forecast;
                days.computeIfAbsent(hourStart.getHour(), h -> new HashSet<>()).add(hourStart.toLocalDate());
            }
        });
        Map<Integer, Double> factors = new HashMap<>();
        Map<Integer, Integer> dayCounts = new HashMap<>();
        sums.forEach((hour, sum) -> {
            int dayCount = days.get(hour).size();
            if (dayCount >= MIN_DAYS && sum[1] >= MIN_FORECAST_KWH) {
                factors.put(hour, Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, sum[0] / sum[1])));
                dayCounts.put(hour, dayCount);
            }
        });
        return new HourlyForecastCalibration(factors, dayCounts);
    }

    public static HourlyForecastCalibration none() {
        return new HourlyForecastCalibration(Map.of(), Map.of());
    }

    /** The correction for an hour of the day - 1.0 where nothing has been learned. */
    public double factor(int hourOfDay) {
        return factorByHour.getOrDefault(hourOfDay, 1.0);
    }

    /** Each slot scaled by the factor of the hour it starts in. */
    public ProductionProfile apply(ProductionProfile profile) {
        var scaled = new ArrayList<Double>(profile.kwhPerSlot().size());
        for (int i = 0; i < profile.kwhPerSlot().size(); i++) {
            scaled.add(profile.kwhPerSlot().get(i) * factor(profile.from().plusHours(i).getHour()));
        }
        return new ProductionProfile(profile.from(), scaled, profile.slotHours());
    }

    /** The fewest measured days behind any calibrated hour of the profile - 0 when none is calibrated. */
    public int days(ProductionProfile profile) {
        List<Integer> counts = new ArrayList<>();
        for (int i = 0; i < profile.kwhPerSlot().size(); i++) {
            Integer count = daysByHour.get(profile.from().plusHours(i).getHour());
            if (count != null) {
                counts.add(count);
            }
        }
        return counts.stream().mapToInt(Integer::intValue).min().orElse(0);
    }
}
