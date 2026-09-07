package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Building and parsing Modbus RTU frames (address + function + data + CRC16).
 * Functions 0x03 (read holding registers) and 0x06 (write single register)
 * - the only two we need for now to read SOC and write the charge mode.
 */
final class ModbusRtuFrame {

    private static final int FUNCTION_READ_HOLDING_REGISTERS = 0x03;
    private static final int FUNCTION_WRITE_SINGLE_REGISTER = 0x06;

    private ModbusRtuFrame() {
    }

    static byte[] readHoldingRegisters(int slaveAddress, int startRegister, int registerCount) {
        var buffer = ByteBuffer.allocate(6);
        buffer.put((byte) slaveAddress);
        buffer.put((byte) FUNCTION_READ_HOLDING_REGISTERS);
        buffer.putShort((short) startRegister);
        buffer.putShort((short) registerCount);
        return withCrc(buffer.array());
    }

    static byte[] writeSingleRegister(int slaveAddress, int register, int value) {
        var buffer = ByteBuffer.allocate(6);
        buffer.put((byte) slaveAddress);
        buffer.put((byte) FUNCTION_WRITE_SINGLE_REGISTER);
        buffer.putShort((short) register);
        buffer.putShort((short) value);
        return withCrc(buffer.array());
    }

    /** Extracts register values (uint16, big-endian - standard Modbus) from a function-0x03 response. */
    static int[] parseReadHoldingRegistersResponse(byte[] frame) {
        if (frame.length < 5 || (frame[1] & 0xFF) != FUNCTION_READ_HOLDING_REGISTERS) {
            throw new IllegalArgumentException("Unexpected Modbus response frame for read holding registers");
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

    private static byte[] withCrc(byte[] pdu) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(pdu);
        out.writeBytes(ModbusCrc16.computeLittleEndian(pdu, 0, pdu.length));
        return out.toByteArray();
    }
}
