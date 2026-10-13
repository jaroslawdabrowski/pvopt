package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * Where to push alerts about the planning process (a failed decision, an inverter that
 * didn't take the schedule, no forecast). {@code ntfyUrl} is a full ntfy topic URL, e.g.
 * {@code https://ntfy.sh/some-unguessable-topic} or a self-hosted server - blank turns
 * notifications off. Settings saved before this existed have none.
 */
public record NotificationSettings(String ntfyUrl) {

    public static final NotificationSettings NONE = new NotificationSettings("");

    public NotificationSettings {
        ntfyUrl = ntfyUrl == null ? "" : ntfyUrl.trim();
        if (!ntfyUrl.isEmpty() && !ntfyUrl.startsWith("https://") && !ntfyUrl.startsWith("http://")) {
            throw new IllegalArgumentException("ntfyUrl must be an http(s) URL, was " + ntfyUrl);
        }
    }

    public boolean enabled() {
        return !ntfyUrl.isEmpty();
    }
}
