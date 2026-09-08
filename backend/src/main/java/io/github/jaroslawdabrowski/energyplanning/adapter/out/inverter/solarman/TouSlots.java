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

    private static final int POWER_WATTS = 5000;

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
     * The per-slot "Power" register is NOT just a grid-charge speed cap - it also caps how
     * fast the battery is allowed to *discharge* during that slot. Confirmed by observation:
     * with this at 1000-2000W, an evening load of 3.5kW only pulled ~1kW from the battery
     * and the rest (2.5kW) from the grid, even with plenty of SOC available - the low cap
     * throttled discharge, not just charge. Set to {@link #POWER_WATTS} (5000W, the
     * inverter's practical max) on every slot for exactly this reason - a lower cap here
     * defeats the whole point of having a battery to shave peak load with.
     */
    static int powerWattsFor(ChargeWindow window) {
        return POWER_WATTS;
    }

    /** Same 5000W cap for the slots {@link #powerWattsFor} doesn't cover (1, 3, 4) - see above. */
    static final int FALLBACK_POWER_WATTS = POWER_WATTS;
}
