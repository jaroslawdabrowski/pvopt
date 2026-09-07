package io.github.jaroslawdabrowski.energyplanning.application;

import io.github.jaroslawdabrowski.energyplanning.domain.TariffWindow;

import java.time.LocalTime;
import java.util.List;

/**
 * Converts the textual configuration ("HH:mm-HH:mm") into domain TariffWindow values.
 * Parsing the configuration format is an application/adapter-layer concern,
 * not the domain's - the domain only knows LocalTime.
 */
final class TariffWindowConfigParser {

    private TariffWindowConfigParser() {
    }

    static List<TariffWindow> parse(List<String> rawWindows) {
        return rawWindows.stream().map(TariffWindowConfigParser::parseOne).toList();
    }

    private static TariffWindow parseOne(String raw) {
        var parts = raw.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid tariff window format, expected HH:mm-HH:mm: " + raw);
        }
        return new TariffWindow(LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim()));
    }
}
