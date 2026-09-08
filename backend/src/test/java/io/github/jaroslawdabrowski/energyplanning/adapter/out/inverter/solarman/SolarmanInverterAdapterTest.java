package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatNoException;

class SolarmanInverterAdapterTest {

    /**
     * Regression guard for a real incident: with writeEnabled=false, applyChargeSchedule must
     * never attempt a Modbus write (and therefore never open a socket) - it should just log and
     * return. Using an unroutable host here proves no network call happens: if the dry-run guard
     * were ever removed or bypassed, this test would hang/fail on a connect timeout instead of
     * completing instantly.
     */
    @Test
    void doesNotTouchTheNetworkWhenWriteIsDisabled() {
        var adapter = new SolarmanInverterAdapter(fakeConfig(false));
        var schedule = new ChargeSchedule(true, LocalTime.of(22, 0), LocalTime.of(6, 0), 80);

        assertThatNoException().isThrownBy(() -> adapter.applyChargeSchedule(schedule));
    }

    private static SolarmanInverterConfig fakeConfig(boolean writeEnabled) {
        return new SolarmanInverterConfig() {
            @Override
            public String host() {
                return "192.0.2.1"; // TEST-NET-1 (RFC 5737) - guaranteed unroutable, never resolves
            }

            @Override
            public int port() {
                return 8899;
            }

            @Override
            public long loggerSerial() {
                return 1L;
            }

            @Override
            public int modbusSlaveAddress() {
                return 1;
            }

            @Override
            public int batterySocRegister() {
                return 588;
            }

            @Override
            public int gridChargeEnableRegister() {
                return 145;
            }

            @Override
            public int gridChargeTargetSocRegister() {
                return 146;
            }

            @Override
            public boolean writeEnabled() {
                return writeEnabled;
            }

            @Override
            public int socketTimeoutMillis() {
                return 200;
            }
        };
    }
}
