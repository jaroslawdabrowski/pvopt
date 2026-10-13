package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** The inverter's daily counters and SOC at one cheap window boundary (local time). */
@Entity
@Table(name = "energy_snapshot")
public class EnergySnapshotEntity extends PanacheEntity {

    @Column(name = "taken_at", nullable = false)
    public LocalDateTime takenAt;

    @Column(name = "pv_kwh", nullable = false)
    public double pvKwh;

    @Column(name = "load_kwh", nullable = false)
    public double loadKwh;

    @Column(name = "grid_import_kwh", nullable = false)
    public double gridImportKwh;

    @Column(name = "grid_export_kwh", nullable = false)
    public double gridExportKwh;

    @Column(name = "battery_charge_kwh", nullable = false)
    public double batteryChargeKwh;

    @Column(name = "battery_discharge_kwh", nullable = false)
    public double batteryDischargeKwh;

    @Column(name = "soc_percent", nullable = false)
    public int socPercent;
}
