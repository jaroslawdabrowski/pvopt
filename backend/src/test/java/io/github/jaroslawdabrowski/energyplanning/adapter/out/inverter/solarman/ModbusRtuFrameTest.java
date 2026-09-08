package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModbusRtuFrameTest {

    @Test
    void buildsReadHoldingRegistersRequestMatchingSpecExample() {
        byte[] frame = ModbusRtuFrame.readHoldingRegisters(0x11, 0x6B, 0x03);

        assertThat(frame).containsExactly(0x11, 0x03, 0x00, 0x6B, 0x00, 0x03, 0x76, 0x87);
    }

    @Test
    void parsesReadHoldingRegistersResponse() {
        byte[] payload = {0x11, 0x03, 0x04, 0x12, 0x34, 0x56, 0x78};
        byte[] crc = ModbusCrc16.computeLittleEndian(payload, 0, payload.length);
        byte[] response = new byte[payload.length + 2];
        System.arraycopy(payload, 0, response, 0, payload.length);
        System.arraycopy(crc, 0, response, payload.length, 2);

        int[] values = ModbusRtuFrame.parseReadHoldingRegistersResponse(response);

        assertThat(values).containsExactly(0x1234, 0x5678);
    }

    @Test
    void buildsWriteMultipleRegistersRequest() {
        // slave=1, function=0x10, start=2, quantity=1, byteCount=2, value=3, then CRC.
        byte[] payload = {0x01, 0x10, 0x00, 0x02, 0x00, 0x01, 0x02, 0x00, 0x03};

        byte[] frame = ModbusRtuFrame.writeMultipleRegisters(0x01, 0x0002, new int[] {0x0003});

        assertThat(frame).hasSize(payload.length + 2);
        assertThat(frame).startsWith(payload);
        byte[] expectedCrc = ModbusCrc16.computeLittleEndian(payload, 0, payload.length);
        assertThat(new byte[] {frame[frame.length - 2], frame[frame.length - 1]}).containsExactly(expectedCrc);
    }

    @Test
    void acceptsAMatchingWriteMultipleRegistersEcho() {
        byte[] payload = {0x01, 0x10, 0x00, (byte) 0x94, 0x00, 0x01};
        byte[] response = withCrc(payload);

        assertThatThrownBy(() -> ModbusRtuFrame.checkWriteMultipleRegistersResponse(response, 0x99, 1))
                .isInstanceOf(IllegalArgumentException.class); // wrong expected register -> rejected

        ModbusRtuFrame.checkWriteMultipleRegistersResponse(response, 0x94, 1); // matching -> no exception
    }

    @Test
    void rejectsTheNonStandardTwoByteResponseThisInverterSendsForUnsupportedWrites() {
        byte[] response = {0x05, 0x00};

        assertThatThrownBy(() -> ModbusRtuFrame.checkWriteMultipleRegistersResponse(response, 0x94, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] withCrc(byte[] payload) {
        byte[] crc = ModbusCrc16.computeLittleEndian(payload, 0, payload.length);
        byte[] frame = new byte[payload.length + 2];
        System.arraycopy(payload, 0, frame, 0, payload.length);
        System.arraycopy(crc, 0, frame, payload.length, 2);
        return frame;
    }
}
