package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Encoding/decoding of the "Solarman V5" frame that the Deye/Solarman logger wraps
 * around the actual Modbus RTU frame before sending it over TCP (usually port 8899).
 * The frame layout follows the widely known, reverse-engineered protocol documented
 * by open-source projects (pysolarmanv5, the Home Assistant "solarman" integration,
 * deye-inverter-mqtt).
 *
 * NOTE: the frame-type/sensor-type/time-counter fields below are "neutral" values
 * (zeros), accepted in practice by most loggers - but the exact byte layout MUST be
 * verified against the user's actual hardware (see Phase 1 of the plan) before this
 * adapter is used in production.
 */
final class SolarmanV5Frame {

    private static final int START = 0xA5;
    private static final int END = 0x15;
    private static final int CONTROL_CODE_REQUEST = 0x4510;
    /** control(2)+seq(2)+loggerSerial(4)+frameType(1)+sensorType(2)+3x time(4 each) = 23 bytes. */
    private static final int HEADER_LENGTH = 23;

    private SolarmanV5Frame() {
    }

    static byte[] encodeRequest(long loggerSerial, int sequenceNumber, byte[] modbusRtuFrame) {
        var payload = ByteBuffer.allocate(HEADER_LENGTH + modbusRtuFrame.length).order(ByteOrder.LITTLE_ENDIAN);
        payload.putShort((short) CONTROL_CODE_REQUEST);
        payload.putShort((short) sequenceNumber);
        payload.putInt((int) loggerSerial);
        payload.put((byte) 0x02); // frame type
        payload.putShort((short) 0x0000); // sensor type
        payload.putInt(0); // total working time
        payload.putInt(0); // power-on time
        payload.putInt(0); // offset time
        payload.put(modbusRtuFrame);

        byte[] payloadBytes = payload.array();
        int checksum = checksum(payloadBytes);

        var frame = new ByteArrayOutputStream();
        frame.write(START);
        var length = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) payloadBytes.length);
        frame.writeBytes(length.array());
        frame.writeBytes(payloadBytes);
        frame.write(checksum);
        frame.write(END);
        return frame.toByteArray();
    }

    /** Extracts the embedded Modbus RTU frame from the logger's response, verifying framing and checksum. */
    static byte[] decodeResponse(byte[] raw) {
        if (raw.length < 1 + 2 + HEADER_LENGTH + 1 + 1 || (raw[0] & 0xFF) != START || (raw[raw.length - 1] & 0xFF) != END) {
            throw new IllegalArgumentException("Invalid Solarman V5 frame (bad start/end markers)");
        }
        int payloadLength = ((raw[2] & 0xFF) << 8 | (raw[1] & 0xFF));
        byte[] payload = new byte[payloadLength];
        System.arraycopy(raw, 3, payload, 0, payloadLength);

        int expectedChecksum = raw[3 + payloadLength] & 0xFF;
        if (checksum(payload) != expectedChecksum) {
            throw new IllegalArgumentException("Invalid Solarman V5 frame checksum");
        }

        byte[] modbusFrame = new byte[payload.length - HEADER_LENGTH];
        System.arraycopy(payload, HEADER_LENGTH, modbusFrame, 0, modbusFrame.length);
        return modbusFrame;
    }

    private static int checksum(byte[] payload) {
        int sum = 0;
        for (byte b : payload) {
            sum += (b & 0xFF);
        }
        return sum & 0xFF;
    }
}
