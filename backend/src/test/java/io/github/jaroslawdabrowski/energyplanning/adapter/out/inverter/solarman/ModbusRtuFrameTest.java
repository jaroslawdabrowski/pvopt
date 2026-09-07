package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void buildsWriteSingleRegisterRequest() {
        byte[] frame = ModbusRtuFrame.writeSingleRegister(0x01, 0x0002, 0x0003);

        assertThat(frame[0]).isEqualTo((byte) 0x01);
        assertThat(frame[1]).isEqualTo((byte) 0x06);
        assertThat(frame).hasSize(8);
    }
}
