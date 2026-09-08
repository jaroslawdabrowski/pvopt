package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manual diagnostic tool, NOT part of the normal test suite ({@code @Disabled} - run it
 * explicitly), for finding which Modbus register actually holds a given live value (e.g.
 * battery SOC) on real inverter hardware, by reading a range of registers via both
 * function 0x03 (holding registers) and 0x04 (input registers) and printing every value.
 *
 * <p>Deye inverters commonly expose live status values (SOC, voltages, temperatures) as
 * <i>input</i> registers rather than <i>holding</i> registers, so scanning both function
 * codes matters - {@code SolarmanInverterConfig.batterySocRegister} currently assumes
 * holding register 184, which reads back an implausible "1" on real hardware; the actual
 * register/function is still unknown for this inverter model.
 *
 * <p>Run against real hardware with, e.g.:
 * <pre>
 * ./mvnw test -Dtest=RegisterScannerManualTest -DfailIfNoTests=false \
 *   -Dpvopt.scan.host=192.168.68.60 -Dpvopt.scan.loggerSerial=2965414814 \
 *   -Dpvopt.scan.from=0 -Dpvopt.scan.to=200 -Dpvopt.scan.expectedValue=48
 * </pre>
 * then look through the printed output for HOLDING/INPUT lines whose value matches
 * {@code pvopt.scan.expectedValue} (they get a "&lt;-- MATCH" marker) - cross-check a
 * couple of candidates against the Solarman/Deye app (e.g. unplug/replug a small load
 * and see which register's value actually moves).
 */
class RegisterScannerManualTest {

    private static final int REGISTERS_PER_REQUEST = 10;

    @Test
    @Disabled("manual hardware diagnostic - pass -Dpvopt.scan.host=... and run explicitly, see class javadoc")
    void scanRegisterRangeOnRealHardware() throws Exception {
        String host = require("pvopt.scan.host");
        int port = Integer.parseInt(System.getProperty("pvopt.scan.port", "8899"));
        long loggerSerial = Long.parseLong(require("pvopt.scan.loggerSerial"));
        int slaveAddress = Integer.parseInt(System.getProperty("pvopt.scan.slaveAddress", "1"));
        int fromRegister = Integer.parseInt(System.getProperty("pvopt.scan.from", "0"));
        int toRegisterExclusive = Integer.parseInt(System.getProperty("pvopt.scan.to", "200"));
        Integer expectedValue = System.getProperty("pvopt.scan.expectedValue") != null
                ? Integer.valueOf(System.getProperty("pvopt.scan.expectedValue"))
                : null;

        var sequenceNumber = new AtomicInteger();

        System.out.println("Scanning holding registers [" + fromRegister + ", " + toRegisterExclusive + ")...");
        scanFunction(host, port, loggerSerial, slaveAddress, fromRegister, toRegisterExclusive, sequenceNumber,
                expectedValue, "HOLDING",
                (slave, start, count) -> ModbusRtuFrame.readHoldingRegisters(slave, start, count),
                ModbusRtuFrame::parseReadHoldingRegistersResponse);

        System.out.println("Scanning input registers [" + fromRegister + ", " + toRegisterExclusive + ")...");
        scanFunction(host, port, loggerSerial, slaveAddress, fromRegister, toRegisterExclusive, sequenceNumber,
                expectedValue, "INPUT",
                (slave, start, count) -> ModbusRtuFrame.readInputRegisters(slave, start, count),
                ModbusRtuFrame::parseReadInputRegistersResponse);
    }

    private interface RequestBuilder {
        byte[] build(int slaveAddress, int startRegister, int registerCount);
    }

    private interface ResponseParser {
        int[] parse(byte[] modbusFrame);
    }

    private void scanFunction(String host, int port, long loggerSerial, int slaveAddress, int fromRegister,
            int toRegisterExclusive, AtomicInteger sequenceNumber, Integer expectedValue, String label,
            RequestBuilder requestBuilder, ResponseParser responseParser) {
        for (int start = fromRegister; start < toRegisterExclusive; start += REGISTERS_PER_REQUEST) {
            int count = Math.min(REGISTERS_PER_REQUEST, toRegisterExclusive - start);
            try {
                byte[] modbusRequest = requestBuilder.build(slaveAddress, start, count);
                byte[] modbusResponse = sendAndReceive(host, port, loggerSerial, sequenceNumber, modbusRequest);
                int[] values = responseParser.parse(modbusResponse);
                for (int i = 0; i < values.length; i++) {
                    int register = start + i;
                    int value = values[i];
                    boolean match = expectedValue != null && expectedValue == value;
                    System.out.printf("%s[%d] = %d (0x%04x)%s%n", label, register, value, value,
                            match ? "  <-- MATCH" : "");
                }
            } catch (Exception e) {
                System.out.printf("%s[%d..%d]: %s%n", label, start, start + count - 1, e.getMessage());
            }
        }
    }

    private byte[] sendAndReceive(String host, int port, long loggerSerial, AtomicInteger sequenceNumber,
            byte[] modbusRequest) throws IOException {
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
