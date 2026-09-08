package io.github.jaroslawdabrowski.energyplanning.domain;

/**
 * How to reach the Deye/Solarman logger on the LAN - the one part of the settings panel
 * that is personal/hardware-specific (reveals LAN topology + device serial). It lives in
 * the database (gitignored, like the rest of the app's data) rather than in
 * application.properties precisely so it's user-editable without touching source control.
 *
 * @param host         Solarman logger IP/hostname on the LAN
 * @param port         Solarman V5 TCP port (almost always 8899)
 * @param loggerSerial logger serial number (not the inverter's!), required for the Solarman V5 frame
 */
public record InverterConnectionSettings(String host, int port, long loggerSerial) {

    public InverterConnectionSettings {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, was " + port);
        }
        if (loggerSerial <= 0) {
            throw new IllegalArgumentException("loggerSerial must be positive, was " + loggerSerial);
        }
    }
}
