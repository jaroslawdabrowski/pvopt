package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import io.smallrye.config.ConfigMapping;

/**
 * The installation has two separate PV planes (east- and west-facing), so Forecast.Solar
 * needs two separate /estimate calls - one per plane - with their production summed.
 */
@ConfigMapping(prefix = "pvopt.forecast")
public interface ForecastSolarConfig {

    double latitude();

    double longitude();

    Plane east();

    Plane west();

    interface Plane {

        /** Tilt from horizontal, in degrees. */
        int declination();

        /** Forecast.Solar convention: 0=south, negative=east, positive=west, range -180..180. */
        int azimuth();

        /** Installed capacity of this plane, in kWp. */
        double kwp();
    }
}
