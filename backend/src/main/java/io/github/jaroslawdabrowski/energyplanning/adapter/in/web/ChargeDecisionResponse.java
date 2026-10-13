package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.PeriodOutcome;
import io.github.jaroslawdabrowski.energyplanning.domain.PlannedDecision;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

/** {@code outcome} is null until the decision's expensive period has ended and been measured. */
public record ChargeDecisionResponse(
        Instant decidedAt,
        String window,
        boolean chargeFromGrid,
        int targetSocPercent,
        String reasonCode,
        Map<String, Double> reasonParams,
        OutcomeResponse outcome) {

    public record OutcomeResponse(
            LocalDateTime from,
            LocalDateTime to,
            double loadKwh,
            double pvKwh,
            double gridImportKwh,
            double gridExportKwh,
            int socAtStartPercent,
            int socAtEndPercent) {

        static OutcomeResponse from(PeriodOutcome o) {
            return new OutcomeResponse(o.from(), o.to(), o.loadKwh(), o.pvKwh(), o.gridImportKwh(), o.gridExportKwh(),
                    o.socAtStartPercent(), o.socAtEndPercent());
        }
    }

    static ChargeDecisionResponse from(ChargeDecision decision) {
        return new ChargeDecisionResponse(decision.decidedAt(), decision.window().name(), decision.chargeFromGrid(),
                decision.targetSocPercent(), decision.reason().name(), decision.reasonParams(), null);
    }

    static ChargeDecisionResponse from(PlannedDecision planned) {
        var d = planned.decision();
        return new ChargeDecisionResponse(d.decidedAt(), d.window().name(), d.chargeFromGrid(),
                d.targetSocPercent(), d.reason().name(), d.reasonParams(),
                planned.outcome().map(OutcomeResponse::from).orElse(null));
    }
}
