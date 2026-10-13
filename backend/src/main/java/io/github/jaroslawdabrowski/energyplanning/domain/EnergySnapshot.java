package io.github.jaroslawdabrowski.energyplanning.domain;

import java.time.LocalDateTime;

/** The inverter's daily counters and battery SOC at one moment (taken at every cheap window boundary). */
public record EnergySnapshot(LocalDateTime takenAt, DailyEnergyCounters counters, int socPercent) {
}
