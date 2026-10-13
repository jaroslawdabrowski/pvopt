package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Measured energy over one expensive period (local times), one row per window and period start. */
@Entity
@Table(name = "period_outcome")
public class PeriodOutcomeEntity extends PanacheEntity {

    @Column(name = "window", nullable = false, length = 20)
    public String window;

    @Column(name = "period_from", nullable = false)
    public LocalDateTime periodFrom;

    @Column(name = "period_to", nullable = false)
    public LocalDateTime periodTo;

    @Column(name = "load_kwh", nullable = false)
    public double loadKwh;

    @Column(name = "pv_kwh", nullable = false)
    public double pvKwh;

    @Column(name = "grid_import_kwh", nullable = false)
    public double gridImportKwh;

    @Column(name = "grid_export_kwh", nullable = false)
    public double gridExportKwh;

    @Column(name = "soc_at_start_percent", nullable = false)
    public int socAtStartPercent;

    @Column(name = "soc_at_end_percent", nullable = false)
    public int socAtEndPercent;
}
