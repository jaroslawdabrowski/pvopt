package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The adapter against a fake stick logger on localhost that misbehaves the way the real one
 * did when two jobs fired at once: with two connections open, it answers the second one with
 * the first one's response. Every SOC read must still get its own answer.
 */
class SolarmanInverterAdapterExchangeTest {

    private final FakeLogger logger = new FakeLogger();

    @AfterEach
    void stop() throws IOException {
        logger.close();
    }

    @Test
    void concurrentReadsNeverOverlapAndEachGetsItsOwnAnswer() throws Exception {
        var adapter = new SolarmanInverterAdapter(new TestConfig(1000));
        var connection = new InverterConnectionSettings("127.0.0.1", logger.port(), 1234L);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> reads = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                reads.add(pool.submit((Callable<Integer>) () -> adapter.readBatteryStatus(connection).socPercent()));
            }
            for (Future<Integer> read : reads) {
                assertThat(read.get()).isEqualTo(FakeLogger.SOC);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(logger.maxConcurrentConnections.get()).isEqualTo(1);
    }

    @Test
    void readsAResponseThatArrivesInPieces() {
        logger.splitResponses = true;
        var adapter = new SolarmanInverterAdapter(new TestConfig(1000));

        var status = adapter.readBatteryStatus(new InverterConnectionSettings("127.0.0.1", logger.port(), 1234L));

        assertThat(status.socPercent()).isEqualTo(FakeLogger.SOC);
    }

    @Test
    void readsLivePowerWithSignedBatteryAndGrid() {
        var adapter = new SolarmanInverterAdapter(new TestConfig(1000));

        var live = adapter.readLivePower(new InverterConnectionSettings("127.0.0.1", logger.port(), 1234L));

        assertThat(live.pvInputWatts()).containsExactly(1800, 650);
        assertThat(live.pvWatts()).isEqualTo(2450);
        assertThat(live.loadWatts()).isEqualTo(900);
        assertThat(live.batteryWatts()).isEqualTo(-1200);
        assertThat(live.gridWatts()).isEqualTo(-350);
        assertThat(live.batterySocPercent()).isEqualTo(FakeLogger.SOC);
    }

    /** Answers "read N registers" from {@link #REGISTERS} (0 elsewhere), V5-framed like the real logger. */
    private static final class FakeLogger implements AutoCloseable {
        static final int SOC = 82;
        /** Midday: PV charges the battery and exports the rest. Negative values as the device sends them. */
        static final java.util.Map<Integer, Integer> REGISTERS = java.util.Map.of(
                588, SOC, 590, -1200 & 0xFFFF, 625, -350 & 0xFFFF, 653, 900, 672, 1800, 673, 650);

        private final ServerSocket server;
        final AtomicInteger active = new AtomicInteger();
        final AtomicInteger maxConcurrentConnections = new AtomicInteger();
        private final AtomicReference<byte[]> lastResponse = new AtomicReference<>();
        volatile boolean splitResponses;

        FakeLogger() {
            try {
                server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            Thread.ofPlatform().daemon().start(this::acceptLoop);
        }

        int port() {
            return server.getLocalPort();
        }

        private void acceptLoop() {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    Thread.ofPlatform().daemon().start(() -> serve(client));
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void serve(Socket client) {
            int now = active.incrementAndGet();
            maxConcurrentConnections.accumulateAndGet(now, Math::max);
            try (client) {
                InputStream in = client.getInputStream();
                byte[] header = in.readNBytes(3);
                int total = SolarmanV5Frame.totalLength(header);
                byte[] rest = in.readNBytes(total - 3);
                byte[] request = new byte[total];
                System.arraycopy(header, 0, request, 0, 3);
                System.arraycopy(rest, 0, request, 3, rest.length);
                Thread.sleep(30);
                // The misbehaviour: a second, overlapping client gets the first one's response.
                byte[] response = now > 1 && lastResponse.get() != null ? lastResponse.get() : respondTo(request);
                lastResponse.set(response);
                if (splitResponses) {
                    client.getOutputStream().write(response, 0, 10);
                    client.getOutputStream().flush();
                    Thread.sleep(20);
                    client.getOutputStream().write(response, 10, response.length - 10);
                } else {
                    client.getOutputStream().write(response);
                }
                client.getOutputStream().flush();
            } catch (Exception ignored) {
                // the test sees it as a failed read
            } finally {
                active.decrementAndGet();
            }
        }

        private static byte[] respondTo(byte[] request) {
            // Request: Modbus frame at offset 26 (slave, fn, reg hi, reg lo, count hi, count lo, crc).
            int register = ((request[28] & 0xFF) << 8) | (request[29] & 0xFF);
            int count = ((request[30] & 0xFF) << 8) | (request[31] & 0xFF);
            var modbus = new ByteArrayOutputStream();
            modbus.write(1);
            modbus.write(3);
            modbus.write(count * 2);
            for (int i = 0; i < count; i++) {
                int value = REGISTERS.getOrDefault(register + i, 0);
                modbus.write(value >> 8);
                modbus.write(value & 0xFF);
            }
            byte[] pdu = modbus.toByteArray();
            modbus.writeBytes(ModbusCrc16.computeLittleEndian(pdu, 0, pdu.length));
            byte[] modbusFrame = modbus.toByteArray();

            // Response: 25 bytes before the Modbus frame, then checksum and end marker.
            var frame = new ByteArrayOutputStream();
            frame.write(0xA5);
            int declared = 25 + modbusFrame.length + 2 - 13;
            frame.write(declared & 0xFF);
            frame.write(declared >> 8);
            frame.write(0x10);
            frame.write(0x15);
            frame.write(request[5]); // echoes the request's sequence low byte
            frame.write(0x77); // its own counter
            frame.writeBytes(new byte[25 - 7]);
            frame.writeBytes(modbusFrame);
            byte[] body = frame.toByteArray();
            int sum = 0;
            for (int i = 1; i < body.length; i++) {
                sum += body[i] & 0xFF;
            }
            frame.write(sum & 0xFF);
            frame.write(0x15);
            return frame.toByteArray();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    private record TestConfig(int socketTimeoutMillis) implements SolarmanInverterConfig {
        public int modbusSlaveAddress() { return 1; }
        public int batterySocRegister() { return 588; }
        public int touTimeBaseRegister() { return 148; }
        public int touPowerBaseRegister() { return 154; }
        public int touBattTargetBaseRegister() { return 166; }
        public int touGridChargeEnableBaseRegister() { return 172; }
        public int dailyBatteryChargeRegister() { return 514; }
        public int dailyBatteryDischargeRegister() { return 515; }
        public int dailyGridImportRegister() { return 520; }
        public int dailyGridExportRegister() { return 521; }
        public int dailyLoadRegister() { return 526; }
        public int dailyPvRegister() { return 529; }
        public int batteryPowerRegister() { return 590; }
        public int gridPowerRegister() { return 625; }
        public int loadPowerRegister() { return 653; }
        public int pvPowerBaseRegister() { return 672; }
        public int pvInputCount() { return 2; }
        public boolean writeEnabled() { return false; }
    }
}
