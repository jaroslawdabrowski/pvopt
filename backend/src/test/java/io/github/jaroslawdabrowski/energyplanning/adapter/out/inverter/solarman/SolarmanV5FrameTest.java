package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolarmanV5FrameTest {

    @Test
    void decodeExtractsTheSameModbusFrameThatWasEncoded() {
        byte[] modbusFrame = ModbusRtuFrame.readHoldingRegisters(0x01, 184, 1);

        // A response frame has a different control code than a request, but decodeResponse
        // doesn't validate the control code (loggers vary here) - we're only testing
        // the symmetry of the framing itself (start/length/checksum/end).
        byte[] v5Frame = SolarmanV5Frame.encodeRequest(1234567890L, 42, modbusFrame);

        byte[] extracted = SolarmanV5Frame.decodeResponse(v5Frame);

        assertThat(extracted).containsExactly(modbusFrame);
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
}
