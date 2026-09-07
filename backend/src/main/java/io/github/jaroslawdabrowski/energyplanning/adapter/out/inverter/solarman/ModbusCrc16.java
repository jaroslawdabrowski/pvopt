package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

/**
 * Standard CRC16/MODBUS (polynomial 0xA001, LSB first, init 0xFFFF).
 * Independent of any specific inverter manufacturer.
 */
final class ModbusCrc16 {

    private ModbusCrc16() {
    }

    static int compute(byte[] data, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int bit = 0; bit < 8; bit++) {
                boolean lsbSet = (crc & 0x0001) != 0;
                crc >>>= 1;
                if (lsbSet) {
                    crc ^= 0xA001;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /** CRC16 encoded as 2 little-endian bytes (as used in a Modbus RTU frame). */
    static byte[] computeLittleEndian(byte[] data, int offset, int length) {
        int crc = compute(data, offset, length);
        return new byte[] {(byte) (crc & 0xFF), (byte) ((crc >> 8) & 0xFF)};
    }
}
