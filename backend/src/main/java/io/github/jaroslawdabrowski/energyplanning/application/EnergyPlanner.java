package io.github.jaroslawdabrowski.energyplanning.application;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecisionPolicy;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
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
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private final ChargeDecisionPolicy decisionPolicy = new ChargeDecisionPolicy();

    public EnergyPlanner(InverterPort inverterPort, ForecastPort forecastPort, PlanningHistoryPort historyPort,
            ClockPort clockPort, ProcessSettingsPort settingsPort) {
        this.inverterPort = inverterPort;
        this.forecastPort = forecastPort;
        this.historyPort = historyPort;
        this.clockPort = clockPort;
        this.settingsPort = settingsPort;
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
        var forecast = forecastPort.productionBetween(period.from(), period.to(), settings.forecast());
        double requiredKwh = settings.planning().consumptionAfter(window)
                * settings.planning().forecastSafetyMarginRatio();

        var decision = decisionPolicy.decide(clockPort.now(), window, battery, forecast, requiredKwh,
                policyConfig(settings))
                .withReasonParams(Map.of(
                        "periodFromHour", hourOf(period.from()),
                        "periodToHour", hourOf(period.to())));

        inverterPort.applyChargeSchedule(
                new ChargeSchedule(window, decision.chargeFromGrid(), decision.targetSocPercent()),
                settings.inverterConnection());
        historyPort.record(decision);
        return decision;
    }

    private static double hourOf(LocalDateTime moment) {
        return moment.getHour() + moment.getMinute() / 60.0;
    }

    @Override
    public EnergyStatus currentStatus() {
        var settings = loadSettingsOrThrow();
        var battery = inverterPort.readBatteryStatus(settings.inverterConnection());
        var currentRate = tariffCalendar(settings).rateAt(clockPort.nowLocal());
        return new EnergyStatus(battery, currentRate, historyPort.findLatest());
    }

    @Override
    public List<ChargeDecision> history(Instant from, Instant to) {
        return historyPort.findBetween(from, to);
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
