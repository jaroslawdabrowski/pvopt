package io.github.jaroslawdabrowski.energyplanning.adapter.out.forecast.forecastsolar;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "pvopt.forecast")
public interface ForecastSolarConfig {

    double latitude();

    double longitude();

    int declination();

    int azimuth();

    double kwp();
}
