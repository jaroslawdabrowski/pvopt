package io.github.jaroslawdabrowski.energyplanning.adapter.out.notification.ntfy;

import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;
import io.github.jaroslawdabrowski.energyplanning.port.out.NotificationPort.Priority;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NtfyNotificationAdapterTest {

    @Test
    void putsAPolishTitleAndThePriorityInTheQuery() {
        var uri = NtfyNotificationAdapter.requestUri("https://ntfy.sh/pvopt-abc", "PV Opt: decyzja nocna nie powiodła się",
                Priority.HIGH);

        assertThat(uri.toString()).isEqualTo("https://ntfy.sh/pvopt-abc"
                + "?title=PV+Opt%3A+decyzja+nocna+nie+powiod%C5%82a+si%C4%99&priority=high&tags=warning");
    }

    @Test
    void sendsNothingWhenNotificationsAreOff() {
        assertThat(new NtfyNotificationAdapter().send("t", "m", Priority.LOW, NotificationSettings.NONE)).isFalse();
    }
}
