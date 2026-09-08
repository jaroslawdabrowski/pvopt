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
import java.util.List;

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
        var settings = loadSettingsOrThrow();
        var nowLocal = clockPort.nowLocal();
        var battery = inverterPort.readBatteryStatus(settings.inverterConnection());
        var tomorrowForecast = forecastPort.forecastFor(nowLocal.toLocalDate().plusDays(1), settings.forecast());
        double requiredKwh = settings.planning().dailyConsumptionEstimateKwh()
                * settings.planning().forecastSafetyMarginRatio();

        var decision = decisionPolicy.decide(clockPort.now(), ChargeWindow.OVERNIGHT, battery, tomorrowForecast,
                requiredKwh, policyConfig(settings));

        inverterPort.applyChargeSchedule(
                new ChargeSchedule(ChargeWindow.OVERNIGHT, decision.chargeFromGrid(), decision.targetSocPercent()),
                settings.inverterConnection());
        historyPort.record(decision);
        return decision;
    }

    @Override
    public ChargeDecision planAfternoonTopUp() {
        var settings = loadSettingsOrThrow();
        var battery = inverterPort.readBatteryStatus(settings.inverterConnection());
        var remainingForecast = forecastPort.remainingToday(clockPort.nowLocal(), settings.forecast());
        double requiredKwh = settings.planning().afternoonConsumptionEstimateKwh();

        var decision = decisionPolicy.decide(clockPort.now(), ChargeWindow.AFTERNOON, battery, remainingForecast,
                requiredKwh, policyConfig(settings));

        inverterPort.applyChargeSchedule(
                new ChargeSchedule(ChargeWindow.AFTERNOON, decision.chargeFromGrid(), decision.targetSocPercent()),
                settings.inverterConnection());
        historyPort.record(decision);
        return decision;
    }

    @Override
    public EnergyStatus currentStatus() {
        var settings = loadSettingsOrThrow();
        var battery = inverterPort.readBatteryStatus(settings.inverterConnection());
        var tariffCalendar = new TariffCalendar(TariffWindowConfigParser.parse(settings.planning().cheapTariffWindows()));
        var currentRate = tariffCalendar.rateAt(clockPort.nowLocal());
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

    private static PlanningPolicyConfig policyConfig(ProcessSettings settings) {
        return new PlanningPolicyConfig(settings.planning().minSocPercent(), settings.planning().fullSocPercent());
    }
}
