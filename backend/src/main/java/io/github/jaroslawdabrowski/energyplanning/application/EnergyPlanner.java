package io.github.jaroslawdabrowski.energyplanning.application;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecisionPolicy;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.PlanningPolicyConfig;
import io.github.jaroslawdabrowski.energyplanning.domain.TariffCalendar;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetEnergyStatusUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetPlanningHistoryUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanAfternoonTopUpUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.in.PlanOvernightChargeUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ForecastPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;

import java.time.Instant;
import java.util.List;

/**
 * The single orchestration point for both daily planning decisions: calls outbound
 * ports, passes data to the pure domain logic (TariffCalendar, ChargeDecisionPolicy),
 * and holds no business logic of its own.
 */
@ApplicationScoped
public class EnergyPlanner implements PlanOvernightChargeUseCase, PlanAfternoonTopUpUseCase,
        GetEnergyStatusUseCase, GetPlanningHistoryUseCase {

    private final InverterPort inverterPort;
    private final ForecastPort forecastPort;
    private final PlanningHistoryPort historyPort;
    private final ClockPort clockPort;
    private final TariffCalendar tariffCalendar;
    private final PlanningPolicyConfig policyConfig;
    private final EnergyPlanningConfig config;
    private final ChargeDecisionPolicy decisionPolicy = new ChargeDecisionPolicy();

    public EnergyPlanner(InverterPort inverterPort, ForecastPort forecastPort, PlanningHistoryPort historyPort,
            ClockPort clockPort, EnergyPlanningConfig config) {
        this.inverterPort = inverterPort;
        this.forecastPort = forecastPort;
        this.historyPort = historyPort;
        this.clockPort = clockPort;
        this.config = config;
        this.tariffCalendar = new TariffCalendar(TariffWindowConfigParser.parse(config.cheapTariffWindows()));
        this.policyConfig = new PlanningPolicyConfig(config.minSocPercent(), config.fullSocPercent());
    }

    @Override
    public ChargeDecision planOvernightCharge() {
        var nowLocal = clockPort.nowLocal();
        var battery = inverterPort.readBatteryStatus();
        var tomorrowForecast = forecastPort.forecastFor(nowLocal.toLocalDate().plusDays(1));
        double requiredKwh = config.dailyConsumptionEstimateKwh() * config.forecastSafetyMarginRatio();

        var decision = decisionPolicy.decide(clockPort.now(), ChargeWindow.OVERNIGHT, battery, tomorrowForecast,
                requiredKwh, policyConfig);

        inverterPort.applyChargeSchedule(
                new ChargeSchedule(ChargeWindow.OVERNIGHT, decision.chargeFromGrid(), decision.targetSocPercent()));
        historyPort.record(decision);
        return decision;
    }

    @Override
    public ChargeDecision planAfternoonTopUp() {
        var battery = inverterPort.readBatteryStatus();
        var remainingForecast = forecastPort.remainingToday(clockPort.nowLocal());
        double requiredKwh = config.afternoonConsumptionEstimateKwh();

        var decision = decisionPolicy.decide(clockPort.now(), ChargeWindow.AFTERNOON, battery, remainingForecast,
                requiredKwh, policyConfig);

        inverterPort.applyChargeSchedule(
                new ChargeSchedule(ChargeWindow.AFTERNOON, decision.chargeFromGrid(), decision.targetSocPercent()));
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
