package io.github.jaroslawdabrowski.energyplanning.adapter.out.inverter.solarman;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeSchedule;
import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.InverterConnectionSettings;
import org.junit.jupiter.api.Test;

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
        var schedule = new ChargeSchedule(ChargeWindow.OVERNIGHT, true, 80);
        // TEST-NET-1 (RFC 5737) - guaranteed unroutable, never resolves
        var connection = new InverterConnectionSettings("192.0.2.1", 8899, 1L);

        assertThatNoException().isThrownBy(() -> adapter.applyChargeSchedule(schedule, connection));
    }

    private static SolarmanInverterConfig fakeConfig(boolean writeEnabled) {
        return new SolarmanInverterConfig() {
            @Override
            public int modbusSlaveAddress() {
                return 1;
            }

            @Override
            public int batterySocRegister() {
                return 588;
            }

            @Override
            public int touTimeBaseRegister() {
                return 148;
            }

            @Override
            public int touPowerBaseRegister() {
                return 154;
            }

            @Override
            public int touBattTargetBaseRegister() {
                return 166;
            }

            @Override
            public int touGridChargeEnableBaseRegister() {
                return 172;
            }

            @Override
            public int dailyBatteryChargeRegister() {
                return 514;
            }

            @Override
            public int dailyBatteryDischargeRegister() {
                return 515;
            }

            @Override
            public int dailyGridImportRegister() {
                return 520;
            }

            @Override
            public int dailyGridExportRegister() {
                return 521;
            }

            @Override
            public int dailyLoadRegister() {
                return 526;
            }

            @Override
            public int dailyPvRegister() {
                return 529;
            }

            @Override
            public int batteryPowerRegister() {
                return 590;
            }

            @Override
            public int gridPowerRegister() {
                return 625;
            }

            @Override
            public int loadPowerRegister() {
                return 653;
            }

            @Override
            public int pvPowerBaseRegister() {
                return 672;
            }

            @Override
            public int pvInputCount() {
                return 2;
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
