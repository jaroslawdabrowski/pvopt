package io.github.jaroslawdabrowski.energyplanning.port.in;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;

/** Keeps the unattended process honest: checks what was written, and tells someone when it fails. */
public interface GuardChargeScheduleUseCase {

    enum Verification { OK, FIXED, MISMATCH, SKIPPED }

    /** Reads the window's slots back and compares them with its latest decision; rewrites once on a mismatch. */
    Verification verify(ChargeWindow window);

    /** A decision that failed for good (after retries) - recorded and notified. */
    void reportFailure(ChargeWindow window, String detail);

    /** Sends a test notification to the given (possibly not yet saved) settings. */
    boolean sendTestNotification(NotificationSettings notifications);
}
