package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.TouScheduleSlot;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    public SolarmanInverterAdapter(SolarmanInverterConfig config) {
        this.config = config;
    }

    @Override
    public BatteryStatus readBatteryStatus() {
        return new BatteryStatus(readRegisters(config.batterySocRegister(), 1)[0]);
    }

    @Override
    public void applyChargeSchedule(ChargeSchedule schedule) {
        int[] slots = TouSlots.slotsFor(schedule.window());

        if (!config.writeEnabled()) {
            LOG.infof("DRY RUN (pvopt.inverter.solarman.write-enabled=false) - would apply: window=%s, "
                            + "enabled=%s, targetSoc=%d%% (TOU slots %s not written)",
                    schedule.window(), schedule.gridChargeEnabled(), schedule.targetSocPercent(),
                    Arrays.toString(slots));
            return;
        }

        for (int slot : slots) {
            writeRegister(config.touGridChargeEnableBaseRegister() + slot, schedule.gridChargeEnabled() ? 1 : 0);
            writeRegister(config.touBattTargetBaseRegister() + slot, schedule.targetSocPercent());
        }
        LOG.infof("Applied charge schedule to inverter: window=%s, enabled=%s, targetSoc=%d%%, slots=%s",
                schedule.window(), schedule.gridChargeEnabled(), schedule.targetSocPercent(),
                Arrays.toString(slots));
    }

    @Override
    public List<TouScheduleSlot> readTouSchedule() {
        int[] times = readRegisters(config.touTimeBaseRegister(), TouSlots.SLOT_COUNT);
        int[] powers = readRegisters(config.touPowerBaseRegister(), TouSlots.SLOT_COUNT);
        int[] targets = readRegisters(config.touBattTargetBaseRegister(), TouSlots.SLOT_COUNT);
        int[] enabled = readRegisters(config.touGridChargeEnableBaseRegister(), TouSlots.SLOT_COUNT);

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

    private int[] readRegisters(int baseRegister, int count) {
        byte[] modbusRequest = ModbusRtuFrame.readHoldingRegisters(config.modbusSlaveAddress(), baseRegister, count);
        byte[] modbusResponse = sendAndReceive(modbusRequest);
        return ModbusRtuFrame.parseReadHoldingRegistersResponse(modbusResponse);
    }

    private void writeRegister(int register, int value) {
        // Function 0x06 (write single register) is silently rejected by this inverter (echoes a
        // non-standard 2-byte "05 00" response and the value never actually changes) - confirmed
        // against real hardware. Function 0x10 (write multiple registers, quantity=1) works.
        byte[] modbusRequest = ModbusRtuFrame.writeMultipleRegisters(config.modbusSlaveAddress(), register,
                new int[] {value});
        byte[] response = sendAndReceive(modbusRequest);
        ModbusRtuFrame.checkWriteMultipleRegistersResponse(response, register, 1);
    }

    private byte[] sendAndReceive(byte[] modbusRequest) {
        byte[] v5Request = SolarmanV5Frame.encodeRequest(
                config.loggerSerial(), sequenceNumber.getAndIncrement() & 0xFFFF, modbusRequest);

        try (Socket socket = new Socket()) {
            // connect() with an explicit timeout - the Socket(host, port) constructor connects
            // with no timeout at all and can hang for a very long time when the logger is unreachable.
            socket.connect(new InetSocketAddress(config.host(), config.port()), config.socketTimeoutMillis());
            socket.setSoTimeout(config.socketTimeoutMillis());
            socket.getOutputStream().write(v5Request);
            socket.getOutputStream().flush();

            byte[] responseBuffer = new byte[1024];
            int read = socket.getInputStream().read(responseBuffer);
            if (read <= 0) {
                throw new InverterCommunicationException("No response from Deye/Solarman logger at "
                        + config.host() + ":" + config.port());
            }
            byte[] response = new byte[read];
            System.arraycopy(responseBuffer, 0, response, 0, read);
            return SolarmanV5Frame.decodeResponse(response);
        } catch (IOException e) {
            throw new InverterCommunicationException(
                    "Failed to communicate with Deye/Solarman logger at " + config.host() + ":" + config.port(), e);
        }
    }
}
