package io.github.jaroslawdabrowski.energyplanning.application;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecisionPolicy;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningPolicyConfig;
import io.github.jaroslawdabrowski.energyplanning.domain.TariffCalendar;
import io.github.jaroslawdabrowski.energyplanning.domain.TariffWindow;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetEnergyStatusUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetPlanningHistoryUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanEnergyUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * The single orchestration point for the planning cycle: calls outbound ports,
 * passes data to the pure domain logic (TariffCalendar, ChargeDecisionPolicy),
 * and holds no business logic of its own.
 */
@ApplicationScoped
public class EnergyPlanner implements PlanEnergyUseCase, GetEnergyStatusUseCase, GetPlanningHistoryUseCase {

    private final InverterPort inverterPort;
    private final ForecastPort forecastPort;
    private final PlanningHistoryPort historyPort;
    private final ClockPort clockPort;
    private final TariffCalendar tariffCalendar;
    private final PlanningPolicyConfig policyConfig;
    private final ChargeDecisionPolicy decisionPolicy = new ChargeDecisionPolicy();

    public EnergyPlanner(InverterPort inverterPort, ForecastPort forecastPort, PlanningHistoryPort historyPort,
            ClockPort clockPort, EnergyPlanningConfig config) {
        this.inverterPort = inverterPort;
        this.forecastPort = forecastPort;
        this.historyPort = historyPort;
        this.clockPort = clockPort;
        this.tariffCalendar = new TariffCalendar(TariffWindowConfigParser.parse(config.cheapTariffWindows()));
        this.policyConfig = new PlanningPolicyConfig(
                config.minSocPercent(),
                config.fullSocPercent(),
                config.dailyConsumptionEstimateKwh(),
                config.forecastSafetyMarginRatio());
    }

    @Override
    public ChargeDecision planAndApply() {
        var nowLocal = clockPort.nowLocal();
        var battery = inverterPort.readBatteryStatus();
        var currentRate = tariffCalendar.rateAt(nowLocal);
        var tomorrowForecast = forecastPort.forecastFor(nowLocal.toLocalDate().plusDays(1));

        var decision = decisionPolicy.decide(clockPort.now(), battery, currentRate, tomorrowForecast, policyConfig);

        var window = tariffCalendar.currentWindow(nowLocal);
        var start = window.map(TariffWindow::start).orElse(LocalTime.MIDNIGHT);
        var end = window.map(TariffWindow::end).orElse(LocalTime.MIDNIGHT);
        inverterPort.applyChargeSchedule(new ChargeSchedule(decision.chargeFromGrid(), start, end, decision.targetSocPercent()));

        historyPort.record(decision);
        return decision;
    }

    @Override
    public EnergyStatus currentStatus() {
        var battery = inverterPort.readBatteryStatus();
        var currentRate = tariffCalendar.rateAt(clockPort.nowLocal());
        return new EnergyStatus(battery, currentRate, historyPort.findLatest());
    }

    @Override
    public List<ChargeDecision> history(Instant from, Instant to) {
        return historyPort.findBetween(from, to);
    }
}
