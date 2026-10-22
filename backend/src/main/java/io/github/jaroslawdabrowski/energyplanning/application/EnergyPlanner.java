package io.github.jaroslawdabrowski.energyplanning.application;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecisionPolicy;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionEstimator;
import io.github.jaroslawdabrowski.energyplanning.domain.ConsumptionEstimator.LearnedConsumption;
import io.github.jaroslawdabrowski.energyplanning.domain.ExpensivePeriod;
import io.github.jaroslawdabrowski.energyplanning.domain.ForecastCalibration;
import io.github.jaroslawdabrowski.energyplanning.domain.HourlyConsumptionProfile;
import io.github.jaroslawdabrowski.energyplanning.domain.PeriodBalance;
import io.github.jaroslawdabrowski.energyplanning.domain.ProductionProfile;
import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;
import io.github.jaroslawdabrowski.energyplanning.domain.PlannedDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningPolicyConfig;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettingsNotConfiguredException;
import io.github.jaroslawdabrowski.energyplanning.domain.TariffCalendar;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetEnergyStatusUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetPlanningHistoryUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetTouScheduleUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanAfternoonTopUpUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanOvernightChargeUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.EnergyRecordPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.RunIssuePort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single orchestration point for both daily planning decisions: calls outbound
 * ports, passes data to the pure domain logic (TariffCalendar, ChargeDecisionPolicy),
 * and holds no business logic of its own.
 *
 * Every use case here reads {@link ProcessSettings} fresh on each call (never cached) -
 * they're user-editable at any time - and throws {@link ProcessSettingsNotConfiguredException}
 * if nothing has been saved yet. Callers decide what that means: the scheduled jobs just
 * log it and skip the run, the web layer maps it to a 409.
 */
@ApplicationScoped
public class EnergyPlanner implements PlanOvernightChargeUseCase, PlanAfternoonTopUpUseCase,
        GetEnergyStatusUseCase, GetPlanningHistoryUseCase, GetTouScheduleUseCase {

    private final InverterPort inverterPort;
    private final ForecastPort forecastPort;
    private final PlanningHistoryPort historyPort;
    private final ClockPort clockPort;
    private final ProcessSettingsPort settingsPort;
    private final EnergyRecordPort energyRecordPort;
    private final IssueReporter issueReporter;
    private final RunIssuePort runIssuePort;
    private final ChargeDecisionPolicy decisionPolicy = new ChargeDecisionPolicy();

    public EnergyPlanner(InverterPort inverterPort, ForecastPort forecastPort, PlanningHistoryPort historyPort,
            ClockPort clockPort, ProcessSettingsPort settingsPort, EnergyRecordPort energyRecordPort,
            IssueReporter issueReporter, RunIssuePort runIssuePort) {
        this.inverterPort = inverterPort;
        this.forecastPort = forecastPort;
        this.historyPort = historyPort;
        this.clockPort = clockPort;
        this.settingsPort = settingsPort;
        this.energyRecordPort = energyRecordPort;
        this.issueReporter = issueReporter;
        this.runIssuePort = runIssuePort;
    }

    @Override
    public ChargeDecision planOvernightCharge() {
        return plan(ChargeWindow.OVERNIGHT);
    }

    @Override
    public ChargeDecision planAfternoonTopUp() {
        return plan(ChargeWindow.AFTERNOON);
    }

    /**
     * Both decisions weigh the same thing: the expensive period right after their cheap
     * window (G12: 06-13 after the night, 15-22 after the afternoon) - its consumption
     * estimate against the PV forecast for exactly those hours.
     */
    private ChargeDecision plan(ChargeWindow window) {
        var settings = loadSettingsOrThrow();
        var calendar = tariffCalendar(settings);
        var period = calendar.expensivePeriodAfter(calendar.windowFor(window), clockPort.nowLocal());
        var battery = inverterPort.readBatteryStatus(settings.inverterConnection());
        var forecast = forecastOrNone(window, period, settings);
        var learned = learnedConsumption(window, settings);
        double consumptionKwh = learned.map(LearnedConsumption::kwh)
                .orElse(settings.planning().consumptionAfter(window));
        double requiredKwh = consumptionKwh * settings.planning().forecastSafetyMarginRatio();

        // Hour by hour: consumption spread by the learned daily shape (flat until there is one),
        // against PV per hour - the reserve is the largest running deficit, not the period total.
        var slotHours = ProductionProfile.slotHoursFor(period);
        var rawProduction = forecast.orElseGet(() -> new ProductionProfile(period.from(),
                slotHours.stream().map(h -> 0.0).toList(), slotHours));
        // Corrected by how far the forecast has been off for this window lately (1.0 until known).
        var calibration = forecastCalibration(window);
        var production = rawProduction.scaledBy(calibration.map(ForecastCalibration.Factor::value).orElse(1.0));
        var shape = HourlyConsumptionProfile.learn(
                energyRecordPort.findSnapshotsSince(clockPort.nowLocal().minus(EnergyRecorder.LEARNING_WINDOW)));
        var balance = PeriodBalance.of(shape.spread(requiredKwh, period.from(), slotHours),
                production.kwhPerSlot(), slotHours);

        var decision = decisionPolicy.decide(clockPort.now(), window, battery, balance, policyConfig(settings))
                .withReasonParams(Map.of(
                        "periodFromHour", hourOf(period.from()),
                        "periodToHour", hourOf(period.to()),
                        "consumptionKwh", consumptionKwh,
                        "learnedSamples", (double) learned.map(LearnedConsumption::samples).orElse(0),
                        "forecastUnavailable", forecast.isPresent() ? 0.0 : 1.0,
                        "reservePeakHour", hourOf(period.from().plusMinutes(Math.round(balance.peakHourOffset() * 60))),
                        "hourlyShape", shape.covers(period.from(), slotHours.size()) ? 1.0 : 0.0,
                        "forecastRawKwh", Math.round(rawProduction.totalKwh() * 10) / 10.0,
                        "calibrationFactor", calibration.map(ForecastCalibration.Factor::value).orElse(1.0),
                        "calibrationSamples", (double) calibration.map(ForecastCalibration.Factor::samples).orElse(0)))
                .withReasonParams(Map.of("forecastModel", (double) forecastPort.modelVersion()));

        inverterPort.applyChargeSchedule(
                new ChargeSchedule(window, decision.chargeFromGrid(), decision.targetSocPercent()),
                settings.inverterConnection());
        historyPort.record(decision);
        return decision;
    }

    /**
     * Without a forecast the decision is made as if no sun were coming - a conservative grid
     * charge beats facing the expensive hours with an empty battery. Someone gets told.
     */
    private Optional<ProductionProfile> forecastOrNone(ChargeWindow window, ExpensivePeriod period,
            ProcessSettings settings) {
        try {
            return Optional.of(forecastPort.productionProfile(period, settings.forecast()));
        } catch (RuntimeException e) {
            issueReporter.report(new RunIssue(clockPort.now(), window, RunIssue.Kind.FORECAST_UNAVAILABLE,
                    String.valueOf(e.getMessage())), settings.notifications());
            return Optional.empty();
        }
    }

    private static double hourOf(LocalDateTime moment) {
        return moment.getHour() + moment.getMinute() / 60.0;
    }

    @Override
    public EnergyStatus currentStatus() {
        var settings = loadSettingsOrThrow();
        var battery = inverterPort.readBatteryStatus(settings.inverterConnection());
        var currentRate = tariffCalendar(settings).rateAt(clockPort.nowLocal());
        return new EnergyStatus(battery, currentRate, historyPort.findLatest(), runIssuePort.findLatest());
    }

    @Override
    public List<PlannedDecision> history(Instant from, Instant to) {
        var decisions = historyPort.findBetween(from, to);
        var settings = settingsPort.load();
        if (settings.isEmpty() || decisions.isEmpty()) {
            return decisions.stream().map(d -> new PlannedDecision(d, Optional.empty())).toList();
        }
        // Re-derive each decision's period from its time and window; an outcome matches when its
        // period starts there (it won't if the tariff windows were changed since).
        var calendar = tariffCalendar(settings.get());
        var outcomes = energyRecordPort.findOutcomesSince(clockPort.toLocal(from));
        return decisions.stream().map(decision -> {
            var period = calendar.expensivePeriodAfter(calendar.windowFor(decision.window()),
                    clockPort.toLocal(decision.decidedAt()));
            var outcome = outcomes.stream()
                    .filter(o -> o.window() == decision.window() && o.from().equals(period.from()))
                    .findFirst();
            return new PlannedDecision(decision, outcome);
        }).toList();
    }

    /**
     * Pairs recent measured periods of this window with the forecast the last decision for
     * each of them used - the raw, uncalibrated one (otherwise the correction would feed on
     * itself). Decisions without a forecast, from before forecasts were per period, or from another
     * forecast model (Forecast.Solar's decisions carry none) don't count.
     */
    private Optional<ForecastCalibration.Factor> forecastCalibration(ChargeWindow window) {
        var now = clockPort.now();
        var latestPerPeriod = new LinkedHashMap<LocalDateTime, ForecastCalibration.Sample>();
        for (var planned : history(now.minus(EnergyRecorder.LEARNING_WINDOW), now)) {
            var decision = planned.decision();
            var params = decision.reasonParams();
            if (decision.window() != window || planned.outcome().isEmpty()
                    || params.getOrDefault("forecastUnavailable", 0.0) == 1.0 || !params.containsKey("periodFromHour")
                    || params.getOrDefault("forecastModel", 1.0) != forecastPort.modelVersion()) {
                continue;
            }
            double forecastKwh = params.getOrDefault("forecastRawKwh", params.getOrDefault("forecastKwh", 0.0));
            var outcome = planned.outcome().get();
            // history() is oldest first - a later decision for the same period replaces an earlier one.
            latestPerPeriod.put(outcome.from(), new ForecastCalibration.Sample(forecastKwh, outcome.pvKwh()));
        }
        return ForecastCalibration.learn(List.copyOf(latestPerPeriod.values()));
    }

    /** Measured consumption of this window's period over recent days, when there's enough of it. */
    private Optional<LearnedConsumption> learnedConsumption(ChargeWindow window, ProcessSettings settings) {
        var from = settings.planning().learnFrom(window, clockPort.nowLocal(), EnergyRecorder.LEARNING_WINDOW);
        return ConsumptionEstimator.learn(energyRecordPort.findOutcomes(window, from));
    }

    @Override
    public List<TouScheduleSlot> currentTouSchedule() {
        var settings = loadSettingsOrThrow();
        return inverterPort.readTouSchedule(settings.inverterConnection());
    }

    private ProcessSettings loadSettingsOrThrow() {
        return settingsPort.load().orElseThrow(ProcessSettingsNotConfiguredException::new);
    }

    private static TariffCalendar tariffCalendar(ProcessSettings settings) {
        return new TariffCalendar(TariffWindowConfigParser.parse(settings.planning().cheapTariffWindows()));
    }

    private static PlanningPolicyConfig policyConfig(ProcessSettings settings) {
        return new PlanningPolicyConfig(settings.planning().minSocPercent(), settings.planning().fullSocPercent(),
                settings.planning().batteryCapacityKwh());
    }
}
