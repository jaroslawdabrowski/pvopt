package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One-off manual tool, NOT part of the normal test suite ({@code @Disabled} - run it
 * explicitly), that rewrites the inverter's 6 TOU slot start times to the boundaries
 * matching PGE G12 (see {@link TouSlots} for the boundary scheme and why). This is the
 * only place in the codebase that writes to registers 148-153 (touTimeBaseRegister) -
 * everything else only ever reads them or writes the enable/target registers for a
 * fixed slot. Does not touch Power (154-159); the existing 10000/slot is fine as-is.
 *
 * <p>Reads the current times first and prints old -> new for review. Only writes when
 * BOTH {@code -Dpvopt.setup.confirm=yes} AND {@code -Dpvopt.setup.writeEnabled=true} are
 * passed - belt and suspenders on top of the normal {@code -Dtest}/{@code @Disabled}
 * gates, since this is the one tool in the codebase that's meant to actually change the
 * inverter's configuration rather than just its runtime charge state.
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
    void rewriteTouSlotTimesToG12Boundaries() throws Exception {
        String host = require("pvopt.scan.host");
        int port = Integer.parseInt(System.getProperty("pvopt.scan.port", "8899"));
        long loggerSerial = Long.parseLong(require("pvopt.scan.loggerSerial"));
        int slaveAddress = Integer.parseInt(System.getProperty("pvopt.scan.slaveAddress", "1"));
        int timeBaseRegister = Integer.parseInt(System.getProperty("pvopt.scan.touTimeBaseRegister", "148"));

        System.out.println("Current TOU slot times:");
        int[] current = new int[TouSlots.SLOT_COUNT];
        for (int slot = 0; slot < TouSlots.SLOT_COUNT; slot++) {
            current[slot] = readRegister(host, port, loggerSerial, slaveAddress, timeBaseRegister + slot);
            System.out.printf("  slot %d: %04d -> %04d%n", slot, current[slot], TouSlots.START_TIMES_HHMM[slot]);
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

        System.out.println("Re-reading to confirm:");
        for (int slot = 0; slot < TouSlots.SLOT_COUNT; slot++) {
            int value = readRegister(host, port, loggerSerial, slaveAddress, timeBaseRegister + slot);
            System.out.printf("  slot %d: %04d (expected %04d)%n", slot, value, TouSlots.START_TIMES_HHMM[slot]);
        }
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
