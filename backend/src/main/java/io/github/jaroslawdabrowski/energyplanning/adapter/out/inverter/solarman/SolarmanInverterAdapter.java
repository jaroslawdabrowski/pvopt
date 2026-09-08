package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import io.github.jaroslawdabrowski.energyplanning.domain.BatteryStatus;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.port.out.InverterPort;

import java.io.IOException;
import java.net.Socket;
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
        byte[] modbusRequest = ModbusRtuFrame.readHoldingRegisters(
                config.modbusSlaveAddress(), config.batterySocRegister(), 1);
        byte[] modbusResponse = sendAndReceive(modbusRequest);
        int[] values = ModbusRtuFrame.parseReadHoldingRegistersResponse(modbusResponse);
        return new BatteryStatus(values[0]);
    }

    @Override
    public void applyChargeSchedule(ChargeSchedule schedule) {
        if (!config.writeEnabled()) {
            LOG.infof("DRY RUN (pvopt.inverter.solarman.write-enabled=false) - would apply: enabled=%s, "
                            + "window=%s-%s, targetSoc=%d%% (registers %d/%d not written)",
                    schedule.gridChargeEnabled(), schedule.start(), schedule.end(), schedule.targetSocPercent(),
                    config.gridChargeEnableRegister(), config.gridChargeTargetSocRegister());
            return;
        }
        writeRegister(config.gridChargeEnableRegister(), schedule.gridChargeEnabled() ? 1 : 0);
        writeRegister(config.gridChargeTargetSocRegister(), schedule.targetSocPercent());
        LOG.infof("Applied charge schedule to inverter: enabled=%s, window=%s-%s, targetSoc=%d%%",
                schedule.gridChargeEnabled(), schedule.start(), schedule.end(), schedule.targetSocPercent());
    }

    private void writeRegister(int register, int value) {
        byte[] modbusRequest = ModbusRtuFrame.writeSingleRegister(config.modbusSlaveAddress(), register, value);
        sendAndReceive(modbusRequest);
    }

    private byte[] sendAndReceive(byte[] modbusRequest) {
        byte[] v5Request = SolarmanV5Frame.encodeRequest(
                config.loggerSerial(), sequenceNumber.getAndIncrement() & 0xFFFF, modbusRequest);

        try (Socket socket = new Socket()) {
            // connect() with an explicit timeout - the Socket(host, port) constructor connects
            // with no timeout at all and can hang for a very long time when the logger is unreachable.
            socket.connect(new java.net.InetSocketAddress(config.host(), config.port()), config.socketTimeoutMillis());
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
