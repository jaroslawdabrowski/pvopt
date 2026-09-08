package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Building and parsing Modbus RTU frames (address + function + data + CRC16).
 * Functions 0x03 (read holding registers), 0x04 (read input registers) and 0x10
 * (write multiple registers) - Deye inverters commonly expose live status values
 * (like battery SOC) as input registers rather than holding registers, so both
 * read functions are needed to locate the right one. Function 0x06 (write single
 * register) is confirmed - against real hardware - to be silently rejected by
 * this inverter (it echoes a non-standard "05 00" response and the value never
 * actually changes); 0x10 with a single-register payload is the one that works.
 */
final class ModbusRtuFrame {

    private static final int FUNCTION_READ_HOLDING_REGISTERS = 0x03;
    private static final int FUNCTION_READ_INPUT_REGISTERS = 0x04;
    private static final int FUNCTION_WRITE_MULTIPLE_REGISTERS = 0x10;

    private ModbusRtuFrame() {
    }

    static byte[] readHoldingRegisters(int slaveAddress, int startRegister, int registerCount) {
        return readRegisters(slaveAddress, FUNCTION_READ_HOLDING_REGISTERS, startRegister, registerCount);
    }

    static byte[] readInputRegisters(int slaveAddress, int startRegister, int registerCount) {
        return readRegisters(slaveAddress, FUNCTION_READ_INPUT_REGISTERS, startRegister, registerCount);
    }

    /** Also used for single-register writes (values.length == 1) - see the class javadoc for why. */
    static byte[] writeMultipleRegisters(int slaveAddress, int startRegister, int[] values) {
        var buffer = ByteBuffer.allocate(7 + values.length * 2);
        buffer.put((byte) slaveAddress);
        buffer.put((byte) FUNCTION_WRITE_MULTIPLE_REGISTERS);
        buffer.putShort((short) startRegister);
        buffer.putShort((short) values.length);
        buffer.put((byte) (values.length * 2));
        for (int value : values) {
            buffer.putShort((short) value);
        }
        return withCrc(buffer.array());
    }

    /**
     * Validates a function-0x10 response: raises if the device rejected the write (a Modbus
     * exception, or the non-standard "05 00" this inverter sends for functions it won't accept)
     * instead of silently treating it as success.
     */
    static void checkWriteMultipleRegistersResponse(byte[] frame, int expectedStartRegister,
            int expectedRegisterCount) {
        if (frame.length >= 3 && (frame[1] & 0xFF) == (FUNCTION_WRITE_MULTIPLE_REGISTERS | 0x80)) {
            throw new IllegalArgumentException(
                    "Modbus exception response, code=" + (frame[2] & 0xFF) + ", frame=" + toHex(frame));
        }
        if (frame.length < 6 || (frame[1] & 0xFF) != FUNCTION_WRITE_MULTIPLE_REGISTERS) {
            throw new IllegalArgumentException(
                    "Unexpected/rejected Modbus response for write multiple registers: " + toHex(frame));
        }
        int echoedStart = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        int echoedCount = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
        if (echoedStart != expectedStartRegister || echoedCount != expectedRegisterCount) {
            throw new IllegalArgumentException("Write multiple registers echoed unexpected start/count "
                    + "(expected " + expectedStartRegister + "/" + expectedRegisterCount + "): " + toHex(frame));
        }
    }

    /** Extracts register values (uint16, big-endian - standard Modbus) from a function-0x03 response. */
    static int[] parseReadHoldingRegistersResponse(byte[] frame) {
        return parseReadRegistersResponse(frame, FUNCTION_READ_HOLDING_REGISTERS);
    }

    /** Extracts register values (uint16, big-endian - standard Modbus) from a function-0x04 response. */
    static int[] parseReadInputRegistersResponse(byte[] frame) {
        return parseReadRegistersResponse(frame, FUNCTION_READ_INPUT_REGISTERS);
    }

    private static byte[] readRegisters(int slaveAddress, int function, int startRegister, int registerCount) {
        var buffer = ByteBuffer.allocate(6);
        buffer.put((byte) slaveAddress);
        buffer.put((byte) function);
        buffer.putShort((short) startRegister);
        buffer.putShort((short) registerCount);
        return withCrc(buffer.array());
    }

    private static int[] parseReadRegistersResponse(byte[] frame, int expectedFunction) {
        if (frame.length >= 3 && (frame[1] & 0xFF) == (expectedFunction | 0x80)) {
            throw new IllegalArgumentException(
                    "Modbus exception response, code=" + (frame[2] & 0xFF) + ", frame=" + toHex(frame));
        }
        if (frame.length < 5 || (frame[1] & 0xFF) != expectedFunction) {
            throw new IllegalArgumentException(
                    "Unexpected Modbus response frame for function 0x" + Integer.toHexString(expectedFunction)
                            + ": " + toHex(frame));
        }
        int byteCount = frame[2] & 0xFF;
        int registerCount = byteCount / 2;
        int[] values = new int[registerCount];
        for (int i = 0; i < registerCount; i++) {
            int hi = frame[3 + i * 2] & 0xFF;
            int lo = frame[4 + i * 2] & 0xFF;
            values[i] = (hi << 8) | lo;
        }
        return values;
    }

    private static String toHex(byte[] data) {
        var sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString().trim();
    }

    private static byte[] withCrc(byte[] pdu) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(pdu);
        out.writeBytes(ModbusCrc16.computeLittleEndian(pdu, 0, pdu.length));
        return out.toByteArray();
    }
}
