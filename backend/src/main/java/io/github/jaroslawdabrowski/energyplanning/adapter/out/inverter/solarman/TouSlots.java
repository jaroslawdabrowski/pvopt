package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;

/**
 * The inverter's 6 Time-of-Use slots, fixed to boundaries matching the PGE G12 tariff
 * (see the one-off {@code TouSlotSetupManualTest}, which is what actually writes these
 * times to the device): slot 4 (21:00) is a deliberate filler - G12 has no boundary
 * there, it just exists so the device has 6 ascending slot start times as required.
 *
 * <pre>
 * slot  start  meaning
 * 0     00:00  cheap (night, continues from slot 5)
 * 1     06:00  expensive (day)
 * 2     13:00  cheap (short afternoon window) - governed by the AFTERNOON decision
 * 3     15:00  expensive (evening)
 * 4     21:00  expensive (filler, always OFF - not a real G12 boundary)
 * 5     22:00  cheap (night starts) - governed by the OVERNIGHT decision together with slot 0
 * </pre>
 */
final class TouSlots {

    static final int SLOT_COUNT = 6;

    /** Slot start times in HHmm format (e.g. 1700 = 17:00), matching {@code touTimeBaseRegister}'s encoding. */
    static final int[] START_TIMES_HHMM = {0, 600, 1300, 1500, 2100, 2200};

    private static final int SLOT_MIDNIGHT = 0;
    private static final int SLOT_AFTERNOON = 2;
    private static final int SLOT_NIGHT = 5;

    private TouSlots() {
    }

    /** Which device slot(s) a given decision window governs. OVERNIGHT spans midnight, so it's two slots. */
    static int[] slotsFor(ChargeWindow window) {
        return switch (window) {
            case OVERNIGHT -> new int[] {SLOT_NIGHT, SLOT_MIDNIGHT};
            case AFTERNOON -> new int[] {SLOT_AFTERNOON};
        };
    }

    /** Reverse of {@link #slotsFor} - which window (if any) governs a given device slot. */
    static ChargeWindow windowForSlot(int slot) {
        for (ChargeWindow window : ChargeWindow.values()) {
            for (int candidate : slotsFor(window)) {
                if (candidate == slot) {
                    return window;
                }
            }
        }
        return null;
    }

    /**
     * Grid-charge power cap (Watts) for a window's slot(s) - a static hardware setting (battery
     * 10kWh, 20% minimum reserve -> 8kWh max to charge), not something the daily decision
     * recomputes. Set once, alongside the slot times, by {@code TouSlotSetupManualTest}:
     * 4000W for the 2h afternoon window (finishes an 8kWh charge in ~2h), 2000W for the 8h
     * overnight window (finishes in ~4h, comfortable margin, gentler on the battery).
     */
    static int powerWattsFor(ChargeWindow window) {
        return switch (window) {
            case OVERNIGHT -> 2000;
            case AFTERNOON -> 4000;
        };
    }

    /**
     * Power cap (Watts) for the slots {@link #powerWattsFor} doesn't cover (1, 3, 4 - never
     * grid-charge-enabled) - defense in depth: if one of them were ever accidentally enabled
     * (a stray LCD change, a future bug), it caps the damage at 1kW instead of the factory
     * default 10kW.
     */
    static final int FALLBACK_POWER_WATTS = 1000;
}
