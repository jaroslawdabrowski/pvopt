package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.RunIssue;
import io.github.jaroslawdabrowski.energyplanning.port.out.NotificationPort;
import io.github.jaroslawdabrowski.energyplanning.port.out.NotificationPort.Priority;
import io.github.jaroslawdabrowski.energyplanning.port.out.RunIssuePort;
import io.github.jaroslawdabrowski.preferences.domain.Language;
import io.github.jaroslawdabrowski.preferences.port.in.GetLanguagePreferenceUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Records a {@link RunIssue} for the dashboard and pushes it as a notification, worded in
 * the language the user picked in the UI (the frontend owns UI copy; these few sentences are
 * the only text the backend ever sends to a person).
 */
@ApplicationScoped
public class IssueReporter {

    private static final Logger LOG = Logger.getLogger(IssueReporter.class);

    private final RunIssuePort runIssuePort;
    private final NotificationPort notificationPort;
    private final GetLanguagePreferenceUseCase languagePreference;

    public IssueReporter(RunIssuePort runIssuePort, NotificationPort notificationPort,
            GetLanguagePreferenceUseCase languagePreference) {
        this.runIssuePort = runIssuePort;
        this.notificationPort = notificationPort;
        this.languagePreference = languagePreference;
    }

    public void report(RunIssue issue, NotificationSettings notifications) {
        LOG.warnf("Planning issue %s (%s): %s", issue.kind(), issue.window(), issue.detail());
        runIssuePort.record(issue);
        boolean polish = languagePreference.currentLanguage() == Language.PL;
        String window = windowName(issue.window(), polish);
        var priority = issue.kind() == RunIssue.Kind.FORECAST_UNAVAILABLE ? Priority.LOW : Priority.HIGH;
        String title;
        String message;
        switch (issue.kind()) {
            case DECISION_FAILED -> {
                title = polish ? "PV Opt: decyzja " + window + " nie powiodła się" : "PV Opt: " + window + " decision failed";
                message = polish
                        ? "Nie udało się podjąć ani zapisać decyzji, także po ponowieniach (" + issue.detail()
                                + "). Falownik zostaje przy poprzednim harmonogramie."
                        : "The decision couldn't be made or written, even after retries (" + issue.detail()
                                + "). The inverter keeps its previous schedule.";
            }
            case SCHEDULE_MISMATCH -> {
                title = polish ? "PV Opt: falownik nie przyjął harmonogramu" : "PV Opt: inverter didn't take the schedule";
                message = polish
                        ? "Po zapisie sloty okna " + window + " nie zgadzają się z decyzją, także po ponownym zapisie ("
                                + issue.detail() + "). Sprawdź harmonogram falownika w aplikacji."
                        : "Read back after writing, the " + window + " slots don't match the decision, even after "
                                + "rewriting them (" + issue.detail() + "). Check the inverter schedule in the app.";
            }
            default -> {
                title = polish ? "PV Opt: brak prognozy PV" : "PV Opt: no PV forecast";
                message = polish
                        ? "Decyzja " + window + " została policzona tak, jakby słońca nie było (" + issue.detail() + ")."
                        : "The " + window + " decision was made as if no sun was coming (" + issue.detail() + ").";
            }
        }
        notificationPort.send(title, message, priority, notifications);
    }

    public boolean sendTest(NotificationSettings notifications) {
        boolean polish = languagePreference.currentLanguage() == Language.PL;
        return notificationPort.send(polish ? "PV Opt: test powiadomień" : "PV Opt: test notification",
                polish ? "Działa. Tu przyjdą alerty, gdy decyzja się nie uda albo falownik nie przyjmie harmonogramu."
                        : "It works. Alerts land here when a decision fails or the inverter doesn't take the schedule.",
                Priority.LOW, notifications);
    }

    private static String windowName(ChargeWindow window, boolean polish) {
        return switch (window) {
            case OVERNIGHT -> polish ? "nocna" : "overnight";
            case AFTERNOON -> polish ? "popołudniowa" : "afternoon";
        };
    }
}
