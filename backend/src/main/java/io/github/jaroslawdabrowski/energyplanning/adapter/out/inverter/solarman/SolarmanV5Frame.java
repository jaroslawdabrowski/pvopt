package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Encoding/decoding of the "Solarman V5" frame that the Deye/Solarman logger wraps
 * around the actual Modbus RTU frame before sending it over TCP (usually port 8899).
 * Byte layout verified against the reference implementation (jmccrohan/pysolarmanv5)
 * and against a real captured response from a Deye logger on the LAN:
 *
 * <pre>
 * offset  size  field
 * 0       1     start marker (0xA5)
 * 1-2     2     declared length, little-endian = 15 + len(modbus frame)
 * 3       1     control code suffix (0x10)
 * 4       1     control code (0x45 request / 0x15 response)
 * 5-6     2     sequence number, little-endian
 * 7-10    4     logger serial number, little-endian
 * 11      1     frame type (0x02)
 * 12-13   2     sensor type (0x0000)
 * 14-17   4     delivery time (0)
 * 18-21   4     power-on time (0)
 * 22-25   4     offset time (0)
 * 25..-2  N     embedded Modbus RTU frame
 * -2      1     checksum: sum of bytes[1..-2] (everything except the start marker,
 *               checksum byte and end marker), truncated to 8 bits
 * -1      1     end marker (0x15)
 * </pre>
 *
 * Note the one-byte overlap between "offset time" and the Modbus frame at offset 25:
 * a real captured response from a Deye logger confirmed the embedded Modbus frame
 * genuinely starts there (one byte earlier than the 11-byte header + 15-byte fixed
 * trailer built by {@link #encodeRequest} would suggest) - this matches pysolarmanv5's
 * own `modbus_frame = v5_frame[25:-2]`. Requests and responses are apparently not
 * perfectly symmetric in this reverse-engineered protocol.
 */
final class SolarmanV5Frame {

    private static final int START = 0xA5;
    private static final int END = 0x15;
    private static final int CONTROL_CODE_SUFFIX = 0x10;
    private static final int CONTROL_CODE_REQUEST = 0x45;
    /** frameType(1)+sensorType(2)+3x time(4 each) = 15 bytes, per pysolarmanv5's `length = 15 + len(modbus_frame)`. */
    private static final int TRAILER_FIXED_LENGTH = 15;
    /** Fixed byte offset where the embedded Modbus frame begins (11-byte header + 15-byte fixed trailer). */
    private static final int MODBUS_FRAME_OFFSET = 25;

    private SolarmanV5Frame() {
    }

    static byte[] encodeRequest(long loggerSerial, int sequenceNumber, byte[] modbusRtuFrame) {
        int declaredLength = TRAILER_FIXED_LENGTH + modbusRtuFrame.length;

        var frame = new ByteArrayOutputStream();
        frame.write(START);
        frame.writeBytes(le16(declaredLength));
        frame.write(CONTROL_CODE_SUFFIX);
        frame.write(CONTROL_CODE_REQUEST);
        frame.writeBytes(le16(sequenceNumber));
        frame.writeBytes(le32(loggerSerial));
        frame.write(0x02); // frame type
        frame.writeBytes(le16(0x0000)); // sensor type
        frame.writeBytes(new byte[4]); // delivery time
        frame.writeBytes(new byte[4]); // power-on time
        frame.writeBytes(new byte[4]); // offset time
        frame.writeBytes(modbusRtuFrame);

        byte[] withoutTrailer = frame.toByteArray();
        int checksum = checksum(withoutTrailer, 1, withoutTrailer.length - 1);

        frame.write(checksum);
        frame.write(END);
        return frame.toByteArray();
    }

    /** Extracts the embedded Modbus RTU frame from the logger's response, verifying framing and checksum. */
    static byte[] decodeResponse(byte[] raw) {
        if (raw.length < MODBUS_FRAME_OFFSET + 2 || (raw[0] & 0xFF) != START || (raw[raw.length - 1] & 0xFF) != END) {
            throw new IllegalArgumentException("Invalid Solarman V5 frame (bad start/end markers)");
        }

        int expectedChecksum = raw[raw.length - 2] & 0xFF;
        int actualChecksum = checksum(raw, 1, raw.length - 3);
        if (actualChecksum != expectedChecksum) {
            throw new IllegalArgumentException("Invalid Solarman V5 frame checksum");
        }

        byte[] modbusFrame = new byte[raw.length - 2 - MODBUS_FRAME_OFFSET];
        System.arraycopy(raw, MODBUS_FRAME_OFFSET, modbusFrame, 0, modbusFrame.length);
        return modbusFrame;
    }

    /** Sum of bytes[fromInclusive..toInclusive], truncated to 8 bits. */
    private static int checksum(byte[] data, int fromInclusive, int toInclusive) {
        int sum = 0;
        for (int i = fromInclusive; i <= toInclusive; i++) {
            sum += (data[i] & 0xFF);
        }
        return sum & 0xFF;
    }

    private static byte[] le16(int value) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array();
    }

    private static byte[] le32(long value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) value).array();
    }
}
