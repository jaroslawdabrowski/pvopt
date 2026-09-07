package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModbusCrc16Test {

    @Test
    void matchesKnownModbusSpecificationExample() {
        // Example from the Modbus Application Protocol specification: slave 0x11, function 0x03,
        // start address 0x006B, register count 0x0003 -> CRC = 0x8776 (LE: 76 87).
        byte[] request = {0x11, 0x03, 0x00, 0x6B, 0x00, 0x03};

        byte[] crc = ModbusCrc16.computeLittleEndian(request, 0, request.length);

        assertThat(crc).containsExactly(0x76, 0x87);
    }
}
