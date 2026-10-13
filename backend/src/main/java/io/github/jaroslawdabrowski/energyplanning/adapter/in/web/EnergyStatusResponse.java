package io.github.jaroslawdabrowski.energyplanning.adapter.in.web;

import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;
import io.github.jaroslawdabrowski.energyplanning.port.in.GetEnergyStatusUseCase.EnergyStatus;

import java.time.Instant;

/** {@code latestIssue} is the most recent problem ever recorded - the dashboard decides whether it's still relevant. */
public record EnergyStatusResponse(int batterySocPercent, String tariffRate, ChargeDecisionResponse lastDecision,
        IssueResponse latestIssue) {

    public record IssueResponse(Instant occurredAt, String window, String kind, String detail) {

        static IssueResponse from(RunIssue issue) {
            return new IssueResponse(issue.occurredAt(), issue.window().name(), issue.kind().name(), issue.detail());
        }
    }

    static EnergyStatusResponse from(EnergyStatus status) {
        return new EnergyStatusResponse(
                status.battery().socPercent(),
                status.currentRate().name(),
                status.lastDecision().map(ChargeDecisionResponse::from).orElse(null),
                status.latestIssue().map(IssueResponse::from).orElse(null));
    }
}
