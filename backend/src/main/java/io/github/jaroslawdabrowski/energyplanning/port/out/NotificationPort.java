package io.github.jaroslawdabrowski.energyplanning.port.out;

import io.github.jaroslawdabrowski.energyplanning.domain.NotificationSettings;

public interface NotificationPort {

    enum Priority { LOW, HIGH }

    /**
     * Best effort - a notification that can't be delivered is logged, never thrown.
     *
     * @return whether it was delivered (false when notifications are off)
     */
    boolean send(String title, String message, Priority priority, NotificationSettings settings);
}
