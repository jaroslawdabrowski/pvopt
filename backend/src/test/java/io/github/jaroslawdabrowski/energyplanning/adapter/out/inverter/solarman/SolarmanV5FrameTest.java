package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolarmanV5FrameTest {

    @Test
    void encodeRequestProducesAValidSelfConsistentFrame() {
        byte[] modbusFrame = ModbusRtuFrame.readHoldingRegisters(0x01, 184, 1);

        byte[] v5Frame = SolarmanV5Frame.encodeRequest(1234567890L, 42, modbusFrame);

        assertThat(v5Frame[0] & 0xFF).isEqualTo(0xA5);
        assertThat(v5Frame[v5Frame.length - 1] & 0xFF).isEqualTo(0x15);
        // declared length = 15 (fixed trailer) + modbus frame length
        int declaredLength = (v5Frame[2] & 0xFF) << 8 | (v5Frame[1] & 0xFF);
        assertThat(declaredLength).isEqualTo(15 + modbusFrame.length);
    }

    @Test
    void decodeResponseExtractsTheModbusFrameFromARealCapturedResponse() {
        // Captured from a real Deye/Solarman logger (logger serial 0xB0C0A39E) responding
        // to a "read holding register 184" request: a single-register read of value 1
        // (slave address 1). This is a genuine on-the-wire capture, not a hand-built frame -
        // it is what pinned down MODBUS_FRAME_OFFSET=25 in the first place.
        byte[] realResponse = hexBytes(
                "a5 15 00 10 15 00 65 9e a3 c0 b0 02 01 fd 89 bd 03 06 1f 00 00 55 a5 e1 66 "
                        + "01 03 02 00 01 79 84 03 15");

        byte[] modbusFrame = SolarmanV5Frame.decodeResponse(realResponse);

        assertThat(modbusFrame).containsExactly(0x01, 0x03, 0x02, 0x00, 0x01, 0x79, 0x84);
    }

    @Test
    void rejectsFrameWithBadStartMarker() {
        byte[] modbusFrame = ModbusRtuFrame.readHoldingRegisters(0x01, 184, 1);
        byte[] v5Frame = SolarmanV5Frame.encodeRequest(1L, 1, modbusFrame);
        v5Frame[0] = 0x00;

        assertThatThrownBy(() -> SolarmanV5Frame.decodeResponse(v5Frame))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFrameWithCorruptedChecksum() {
        byte[] modbusFrame = ModbusRtuFrame.readHoldingRegisters(0x01, 184, 1);
        byte[] v5Frame = SolarmanV5Frame.encodeRequest(1L, 1, modbusFrame);
        v5Frame[v5Frame.length - 2] ^= 0xFF; // corrupt the checksum byte

        assertThatThrownBy(() -> SolarmanV5Frame.decodeResponse(v5Frame))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] hexBytes(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }
}
