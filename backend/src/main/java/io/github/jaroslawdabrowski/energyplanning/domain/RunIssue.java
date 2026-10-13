package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.Instant;

/**
 * Something that went wrong in the unattended planning process and that a person should
 * know about - recorded for the dashboard and pushed as a notification. {@code detail} is a
 * technical message for the log/notification, not shown as UI copy.
 */
public record RunIssue(Instant occurredAt, ChargeWindow window, Kind kind, String detail) {

    public enum Kind {
        /** The decision couldn't be made or written, even after retries - the inverter keeps its previous schedule. */
        DECISION_FAILED,
        /** Read back after writing, the inverter's slots didn't match the decision - even after rewriting once. */
        SCHEDULE_MISMATCH,
        /** No PV forecast - the decision was made as if no sun was coming (a conservative grid charge). */
        FORECAST_UNAVAILABLE
    }
}
