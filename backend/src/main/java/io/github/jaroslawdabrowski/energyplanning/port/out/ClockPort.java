package io.github.jaroslawdabrowski.energyplanning.port.out;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Clock abstraction - so domain logic (TariffCalendar, ChargeDecisionPolicy) never
 * reads system time directly and stays deterministically testable.
 */
public interface ClockPort {

    Instant now();

    LocalDateTime nowLocal();
}
