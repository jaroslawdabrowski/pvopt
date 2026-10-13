package io.github.jaroslawdabrowski.energyplanning.adapter.out.notification.ntfy;

import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;
import io.github.jaroslawdabrowski.energyplanning.port.out.NotificationPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Publishes to an ntfy topic (ntfy.sh or self-hosted): the message is the body, title and
 * priority go in query parameters - ntfy's headers would need RFC 2047 encoding for Polish
 * characters, query parameters are plain URL-encoded UTF-8.
 */
@ApplicationScoped
public class NtfyNotificationAdapter implements NotificationPort {

    private static final Logger LOG = Logger.getLogger(NtfyNotificationAdapter.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public boolean send(String title, String message, Priority priority, NotificationSettings settings) {
        if (!settings.enabled()) {
            return false;
        }
        try {
            var request = HttpRequest.newBuilder(requestUri(settings.ntfyUrl(), title, priority))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                LOG.warnf("ntfy answered %d - notification '%s' not delivered", response.statusCode(), title);
                return false;
            }
            return true;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warnf(e, "Could not deliver notification '%s'", title);
            return false;
        }
    }

    static URI requestUri(String topicUrl, String title, Priority priority) {
        String separator = topicUrl.contains("?") ? "&" : "?";
        return URI.create(topicUrl + separator
                + "title=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + "&priority=" + (priority == Priority.HIGH ? "high" : "low")
                + "&tags=" + (priority == Priority.HIGH ? "warning" : "information_source"));
    }
}
