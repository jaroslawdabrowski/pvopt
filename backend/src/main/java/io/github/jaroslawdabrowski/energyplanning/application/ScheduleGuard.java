package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeDecision;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.ProcessSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.in.GuardChargeScheduleUseCase;
import io.github.jaroslawdabrowski.energyplanning.port.out.ClockPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.PlanningHistoryPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.ProcessSettingsPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * The inverter has been seen to revert a register shortly after a confirmed-good write (see
 * CLAUDE.md, register 159), so "the write was acknowledged" isn't proof. A few minutes after
 * each decision this reads the governed slots back; one rewrite fixes a transient revert,
 * anything beyond that is for a person.
 */
@ApplicationScoped
public class ScheduleGuard implements GuardChargeScheduleUseCase {

    private static final Logger LOG = Logger.getLogger(ScheduleGuard.class);

    private final InverterPort inverterPort;
    private final PlanningHistoryPort historyPort;
    private final ProcessSettingsPort settingsPort;
    private final ClockPort clockPort;
    private final IssueReporter issueReporter;

    public ScheduleGuard(InverterPort inverterPort, PlanningHistoryPort historyPort, ProcessSettingsPort settingsPort,
            ClockPort clockPort, IssueReporter issueReporter) {
        this.inverterPort = inverterPort;
        this.historyPort = historyPort;
        this.settingsPort = settingsPort;
        this.clockPort = clockPort;
        this.issueReporter = issueReporter;
    }

    @Override
    public Verification verify(ChargeWindow window) {
        Optional<ProcessSettings> settings = settingsPort.load();
        Optional<ChargeDecision> decision = historyPort.findLatest(window);
        if (settings.isEmpty() || decision.isEmpty() || !inverterPort.appliesWrites()) {
            return Verification.SKIPPED;
        }
        var connection = settings.get().inverterConnection();
        if (matches(inverterPort.readTouSchedule(connection), decision.get())) {
            return Verification.OK;
        }
        LOG.warnf("%s slots don't match the latest decision - rewriting once", window);
        inverterPort.applyChargeSchedule(scheduleOf(decision.get()), connection);
        var slots = inverterPort.readTouSchedule(connection);
        if (matches(slots, decision.get())) {
            LOG.infof("%s slots fixed by rewriting", window);
            return Verification.FIXED;
        }
        issueReporter.report(new RunIssue(clockPort.now(), window, RunIssue.Kind.SCHEDULE_MISMATCH,
                describe(slots, decision.get())), settings.get().notifications());
        return Verification.MISMATCH;
    }

    @Override
    public void reportFailure(ChargeWindow window, String detail) {
        var notifications = settingsPort.load().map(ProcessSettings::notifications).orElse(NotificationSettings.NONE);
        issueReporter.report(new RunIssue(clockPort.now(), window, RunIssue.Kind.DECISION_FAILED, detail),
                notifications);
    }

    @Override
    public boolean sendTestNotification(NotificationSettings notifications) {
        return issueReporter.sendTest(notifications);
    }

    static boolean matches(List<TouScheduleSlot> slots, ChargeDecision decision) {
        var governed = governedSlots(slots, decision.window());
        return !governed.isEmpty() && governed.stream().allMatch(slot ->
                slot.gridChargeEnabled() == decision.chargeFromGrid()
                        && slot.targetSocPercent() == decision.targetSocPercent());
    }

    private static List<TouScheduleSlot> governedSlots(List<TouScheduleSlot> slots, ChargeWindow window) {
        return slots.stream().filter(slot -> slot.governedBy() == window).toList();
    }

    private static ChargeSchedule scheduleOf(ChargeDecision decision) {
        return new ChargeSchedule(decision.window(), decision.chargeFromGrid(), decision.targetSocPercent());
    }

    private static String describe(List<TouScheduleSlot> slots, ChargeDecision decision) {
        var actual = governedSlots(slots, decision.window()).stream()
                .map(s -> "slot " + s.slotIndex() + ": grid=" + s.gridChargeEnabled() + " target=" + s.targetSocPercent() + "%")
                .toList();
        return "expected grid=" + decision.chargeFromGrid() + " target=" + decision.targetSocPercent() + "%, read " + actual;
    }
}
