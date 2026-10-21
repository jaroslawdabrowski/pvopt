package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.DailyEnergyCounters;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import io.github.jaroslawdabrowski.energyplanning.domain.LivePower;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The only adapter that knows the Solarman V5 / Modbus RTU protocol and the
 * Deye inverter's register numbers. Outward (through InverterPort) only
 * BatteryStatus/ChargeSchedule are exposed - no byte or register leaks into the domain.
 */
@ApplicationScoped
public class SolarmanInverterAdapter implements InverterPort {

    private static final Logger LOG = Logger.getLogger(SolarmanInverterAdapter.class);

    private final SolarmanInverterConfig config;
    private final AtomicInteger sequenceNumber = new AtomicInteger();
    /** Fair, so a waiting decision isn't starved by a burst of snapshot reads. */
    private final ReentrantLock exchangeLock = new ReentrantLock(true);

    private static final int READ_ATTEMPTS = 3;
    private static final long READ_RETRY_PAUSE_MILLIS = 500;
    private static final int MAX_FRAME_BYTES = 1024;

    public SolarmanInverterAdapter(SolarmanInverterConfig config) {
        this.config = config;
    }

    @Override
    public BatteryStatus readBatteryStatus(InverterConnectionSettings connection) {
        return new BatteryStatus(readRegisters(connection, config.batterySocRegister(), 1)[0]);
    }

    @Override
    public void applyChargeSchedule(ChargeSchedule schedule, InverterConnectionSettings connection) {
        int[] slots = TouSlots.slotsFor(schedule.window());

        if (!config.writeEnabled()) {
            LOG.infof("DRY RUN (pvopt.inverter.solarman.write-enabled=false) - would apply: window=%s, "
                            + "enabled=%s, targetSoc=%d%% (TOU slots %s not written)",
                    schedule.window(), schedule.gridChargeEnabled(), schedule.targetSocPercent(),
                    Arrays.toString(slots));
            return;
        }

        for (int slot : slots) {
            writeRegister(connection, config.touGridChargeEnableBaseRegister() + slot,
                    schedule.gridChargeEnabled() ? 1 : 0);
            writeRegister(connection, config.touBattTargetBaseRegister() + slot, schedule.targetSocPercent());
        }
        LOG.infof("Applied charge schedule to inverter: window=%s, enabled=%s, targetSoc=%d%%, slots=%s",
                schedule.window(), schedule.gridChargeEnabled(), schedule.targetSocPercent(),
                Arrays.toString(slots));
    }

    @Override
    public boolean appliesWrites() {
        return config.writeEnabled();
    }

    @Override
    public DailyEnergyCounters readDailyEnergy(InverterConnectionSettings connection) {
        int[] registers = {config.dailyPvRegister(), config.dailyLoadRegister(), config.dailyGridImportRegister(),
                config.dailyGridExportRegister(), config.dailyBatteryChargeRegister(),
                config.dailyBatteryDischargeRegister()};
        int first = Arrays.stream(registers).min().orElseThrow();
        int last = Arrays.stream(registers).max().orElseThrow();
        int[] block = readRegisters(connection, first, last - first + 1);
        return new DailyEnergyCounters(
                tenthsOfKwh(block, config.dailyPvRegister() - first),
                tenthsOfKwh(block, config.dailyLoadRegister() - first),
                tenthsOfKwh(block, config.dailyGridImportRegister() - first),
                tenthsOfKwh(block, config.dailyGridExportRegister() - first),
                tenthsOfKwh(block, config.dailyBatteryChargeRegister() - first),
                tenthsOfKwh(block, config.dailyBatteryDischargeRegister() - first));
    }

    /** Two reads: SOC to grid power (588-625), then load to the last PV input (653-673). */
    @Override
    public LivePower readLivePower(InverterConnectionSettings connection) {
        int batteryFirst = config.batterySocRegister();
        int[] battery = readRegisters(connection, batteryFirst, config.gridPowerRegister() - batteryFirst + 1);
        int loadFirst = config.loadPowerRegister();
        int lastPv = config.pvPowerBaseRegister() + config.pvInputCount() - 1;
        int[] load = readRegisters(connection, loadFirst, lastPv - loadFirst + 1);

        var pv = new ArrayList<Integer>(config.pvInputCount());
        for (int input = 0; input < config.pvInputCount(); input++) {
            pv.add(load[config.pvPowerBaseRegister() - loadFirst + input]);
        }
        return new LivePower(
                pv,
                load[0],
                signed(battery[config.batteryPowerRegister() - batteryFirst]),
                signed(battery[config.gridPowerRegister() - batteryFirst]),
                battery[0]);
    }

    private static int signed(int register) {
        return (short) register;
    }

    private static double tenthsOfKwh(int[] block, int index) {
        return block[index] / 10.0;
    }

    @Override
    public List<TouScheduleSlot> readTouSchedule(InverterConnectionSettings connection) {
        int[] times = readRegisters(connection, config.touTimeBaseRegister(), TouSlots.SLOT_COUNT);
        int[] powers = readRegisters(connection, config.touPowerBaseRegister(), TouSlots.SLOT_COUNT);
        int[] targets = readRegisters(connection, config.touBattTargetBaseRegister(), TouSlots.SLOT_COUNT);
        int[] enabled = readRegisters(connection, config.touGridChargeEnableBaseRegister(), TouSlots.SLOT_COUNT);

        var slots = new ArrayList<TouScheduleSlot>(TouSlots.SLOT_COUNT);
        for (int slot = 0; slot < TouSlots.SLOT_COUNT; slot++) {
            int nextSlot = (slot + 1) % TouSlots.SLOT_COUNT;
            slots.add(new TouScheduleSlot(
                    slot,
                    LocalTime.of(times[slot] / 100, times[slot] % 100),
                    LocalTime.of(times[nextSlot] / 100, times[nextSlot] % 100),
                    powers[slot],
                    targets[slot],
                    enabled[slot] != 0,
                    TouSlots.windowForSlot(slot)));
        }
        return slots;
    }

    /**
     * A read is retried a couple of times on a bad or foreign response - reads are harmless to
     * repeat, and a garbled frame (the logger was busy, a response crossed with another one) is
     * usually fine a moment later. Writes are not retried here: each one is echo-checked, and a
     * failed decision is retried as a whole by the scheduler.
     */
    private int[] readRegisters(InverterConnectionSettings connection, int baseRegister, int count) {
        byte[] modbusRequest = ModbusRtuFrame.readHoldingRegisters(config.modbusSlaveAddress(), baseRegister, count);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= READ_ATTEMPTS; attempt++) {
            try {
                return ModbusRtuFrame.parseReadHoldingRegistersResponse(sendAndReceive(connection, modbusRequest), count);
            } catch (InverterCommunicationException | IllegalArgumentException e) {
                last = e;
                LOG.warnf("Reading %d register(s) from %d failed (attempt %d of %d): %s", count, baseRegister, attempt,
                        READ_ATTEMPTS, e.getMessage());
                pause(READ_RETRY_PAUSE_MILLIS);
            }
        }
        throw new InverterCommunicationException("Reading " + count + " register(s) from " + baseRegister
                + " failed " + READ_ATTEMPTS + " times: " + last.getMessage(), last);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeRegister(InverterConnectionSettings connection, int register, int value) {
        // Function 0x06 (write single register) is silently rejected by this inverter (echoes a
        // non-standard 2-byte "05 00" response and the value never actually changes) - confirmed
        // against real hardware. Function 0x10 (write multiple registers, quantity=1) works.
        byte[] modbusRequest = ModbusRtuFrame.writeMultipleRegisters(config.modbusSlaveAddress(), register,
                new int[] {value});
        byte[] response = sendAndReceive(connection, modbusRequest);
        ModbusRtuFrame.checkWriteMultipleRegistersResponse(response, register, 1);
    }

    /**
     * One request at a time, whole frames only. The stick logger serves a single TCP client:
     * two jobs firing together (after the Mac woke up, the 04:28 decision and a snapshot) made
     * their responses cross - a failed parse, a 1-register answer to a 16-register read, and a
     * snapshot that stored SOC 2%. So every exchange holds {@link #exchangeLock}, the response is
     * read until the length its header declares, and its sequence number must echo the request's.
     */
    private byte[] sendAndReceive(InverterConnectionSettings connection, byte[] modbusRequest) {
        exchangeLock.lock();
        try {
            int sequence = sequenceNumber.getAndIncrement() & 0xFFFF;
            byte[] v5Request = SolarmanV5Frame.encodeRequest(connection.loggerSerial(), sequence, modbusRequest);
            try (Socket socket = new Socket()) {
                // connect() with an explicit timeout - the Socket(host, port) constructor connects
                // with no timeout at all and can hang for a very long time when the logger is unreachable.
                socket.connect(new InetSocketAddress(connection.host(), connection.port()), config.socketTimeoutMillis());
                socket.setSoTimeout(config.socketTimeoutMillis());
                socket.getOutputStream().write(v5Request);
                socket.getOutputStream().flush();

                byte[] response = readFrame(socket.getInputStream(), connection);
                SolarmanV5Frame.checkSequence(response, sequence);
                return SolarmanV5Frame.decodeResponse(response);
            } catch (IOException e) {
                throw new InverterCommunicationException(
                        "Failed to communicate with Deye/Solarman logger at " + connection.host() + ":"
                                + connection.port(), e);
            }
        } finally {
            exchangeLock.unlock();
        }
    }

    /** Reads exactly one V5 frame: the 3-byte start tells its total length, the rest is read until complete. */
    private static byte[] readFrame(InputStream in, InverterConnectionSettings connection) throws IOException {
        byte[] header = in.readNBytes(3);
        if (header.length < 3) {
            throw new InverterCommunicationException("No response from Deye/Solarman logger at "
                    + connection.host() + ":" + connection.port());
        }
        int total = SolarmanV5Frame.totalLength(header);
        if (total > MAX_FRAME_BYTES) {
            throw new InverterCommunicationException("Implausible frame length " + total + " from the logger");
        }
        byte[] rest = in.readNBytes(total - 3);
        if (rest.length < total - 3) {
            throw new InverterCommunicationException("Logger response cut short: " + (3 + rest.length) + " of "
                    + total + " bytes");
        }
        byte[] frame = new byte[total];
        System.arraycopy(header, 0, frame, 0, 3);
        System.arraycopy(rest, 0, frame, 3, rest.length);
        return frame;
    }
}
