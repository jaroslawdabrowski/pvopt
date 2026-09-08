package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-off manual tool, NOT part of the normal test suite ({@code @Disabled} - run it
 * explicitly), that rewrites the inverter's TOU slot start times and grid-charge power
 * caps for the two decision windows to match PGE G12 and the battery's usable capacity
 * (see {@link TouSlots} for the values and why). Everything else (Batt%-target,
 * grid-charge-enable) is written per-run by the normal decision path; these two are
 * static hardware config, set once here.
 *
 * <p>Reads the current values first and prints old -> new for review. Only writes when
 * BOTH {@code -Dpvopt.setup.confirm=yes} AND {@code -Dpvopt.setup.writeEnabled=true} are
 * passed - belt and suspenders on top of the normal {@code -Dtest}/{@code @Disabled}
 * gates, since this is the one tool in the codebase that's meant to actually change the
 * inverter's configuration rather than just its runtime charge state. Idempotent - safe
 * to re-run if the device is ever reset/reconfigured.
 *
 * <pre>
 * ./mvnw test -Dtest=TouSlotSetupManualTest -DfailIfNoTests=false \
 *   -Dpvopt.scan.host=... -Dpvopt.scan.loggerSerial=... \
 *   -Dpvopt.setup.confirm=yes -Dpvopt.setup.writeEnabled=true
 * </pre>
 */
class TouSlotSetupManualTest {

    private final AtomicInteger sequenceNumber = new AtomicInteger();

    @Test
    @Disabled("one-off hardware setup - pass -Dpvopt.setup.confirm=yes -Dpvopt.setup.writeEnabled=true and run explicitly, see class javadoc")
    void rewriteTouSlotTimesAndPowerCaps() throws Exception {
        String host = require("pvopt.scan.host");
        int port = Integer.parseInt(System.getProperty("pvopt.scan.port", "8899"));
        long loggerSerial = Long.parseLong(require("pvopt.scan.loggerSerial"));
        int slaveAddress = Integer.parseInt(System.getProperty("pvopt.scan.slaveAddress", "1"));
        int timeBaseRegister = Integer.parseInt(System.getProperty("pvopt.scan.touTimeBaseRegister", "148"));
        int powerBaseRegister = Integer.parseInt(System.getProperty("pvopt.scan.touPowerBaseRegister", "154"));

        Map<Integer, Integer> targetPowerBySlot = targetPowerBySlot();

        System.out.println("Current TOU slot times:");
        for (int slot = 0; slot < TouSlots.SLOT_COUNT; slot++) {
            int value = readRegister(host, port, loggerSerial, slaveAddress, timeBaseRegister + slot);
            System.out.printf("  slot %d time: %04d -> %04d%n", slot, value, TouSlots.START_TIMES_HHMM[slot]);
        }
        System.out.println("Current TOU slot power caps (charging slots get their real cap, others a low fallback):");
        for (var entry : targetPowerBySlot.entrySet()) {
            int value = readRegister(host, port, loggerSerial, slaveAddress, powerBaseRegister + entry.getKey());
            System.out.printf("  slot %d power: %d -> %d%n", entry.getKey(), value, entry.getValue());
        }

        boolean confirmed = "yes".equals(System.getProperty("pvopt.setup.confirm"));
        boolean writeEnabled = Boolean.parseBoolean(System.getProperty("pvopt.setup.writeEnabled", "false"));
        if (!confirmed || !writeEnabled) {
            System.out.println("DRY RUN - not writing (need both -Dpvopt.setup.confirm=yes and "
                    + "-Dpvopt.setup.writeEnabled=true to actually write).");
            return;
        }

        for (int slot = 0; slot < TouSlots.SLOT_COUNT; slot++) {
            writeRegister(host, port, loggerSerial, slaveAddress, timeBaseRegister + slot,
                    TouSlots.START_TIMES_HHMM[slot]);
        }
        for (var entry : targetPowerBySlot.entrySet()) {
            writeRegister(host, port, loggerSerial, slaveAddress, powerBaseRegister + entry.getKey(),
                    entry.getValue());
        }

        System.out.println("Re-reading to confirm:");
        for (int slot = 0; slot < TouSlots.SLOT_COUNT; slot++) {
            int value = readRegister(host, port, loggerSerial, slaveAddress, timeBaseRegister + slot);
            System.out.printf("  slot %d time: %04d (expected %04d)%n", slot, value, TouSlots.START_TIMES_HHMM[slot]);
        }
        for (var entry : targetPowerBySlot.entrySet()) {
            int value = readRegister(host, port, loggerSerial, slaveAddress, powerBaseRegister + entry.getKey());
            System.out.printf("  slot %d power: %d (expected %d)%n", entry.getKey(), value, entry.getValue());
        }
    }

    /**
     * All 6 slots, not just the ones this app charges from: the others get
     * {@link TouSlots#FALLBACK_POWER_WATTS} instead of the factory 10kW default, as
     * defense in depth in case one of them is ever accidentally enabled.
     */
    private static Map<Integer, Integer> targetPowerBySlot() {
        var result = new java.util.TreeMap<Integer, Integer>();
        for (int slot = 0; slot < TouSlots.SLOT_COUNT; slot++) {
            result.put(slot, TouSlots.FALLBACK_POWER_WATTS);
        }
        for (ChargeWindow window : ChargeWindow.values()) {
            for (int slot : TouSlots.slotsFor(window)) {
                result.put(slot, TouSlots.powerWattsFor(window));
            }
        }
        return result;
    }

    private int readRegister(String host, int port, long loggerSerial, int slaveAddress, int register)
            throws IOException {
        byte[] request = ModbusRtuFrame.readHoldingRegisters(slaveAddress, register, 1);
        byte[] response = sendAndReceive(host, port, loggerSerial, request);
        return ModbusRtuFrame.parseReadHoldingRegistersResponse(response)[0];
    }

    private void writeRegister(String host, int port, long loggerSerial, int slaveAddress, int register, int value)
            throws IOException {
        byte[] request = ModbusRtuFrame.writeMultipleRegisters(slaveAddress, register, new int[] {value});
        byte[] response = sendAndReceive(host, port, loggerSerial, request);
        ModbusRtuFrame.checkWriteMultipleRegistersResponse(response, register, 1);
        System.out.printf("  write register %d = %d -> ok%n", register, value);
    }

    private byte[] sendAndReceive(String host, int port, long loggerSerial, byte[] modbusRequest)
            throws IOException {
        byte[] v5Request = SolarmanV5Frame.encodeRequest(loggerSerial, sequenceNumber.getAndIncrement() & 0xFFFF,
                modbusRequest);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(v5Request);
            socket.getOutputStream().flush();

            byte[] responseBuffer = new byte[1024];
            int read = socket.getInputStream().read(responseBuffer);
            if (read <= 0) {
                throw new IOException("No response from logger at " + host + ":" + port);
            }
            byte[] response = new byte[read];
            System.arraycopy(responseBuffer, 0, response, 0, read);
            return SolarmanV5Frame.decodeResponse(response);
        }
    }

    private static String require(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: -D" + property + "=...");
        }
        return value;
    }
}
