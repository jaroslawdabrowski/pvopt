package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import io.github.jaroslawdabrowski.energyplanning.domain.ChargeWindow;
import io.github.jaroslawdabrowski.energyplanning.domain.DailyEnergyCounters;
import io.github.jaroslawdabrowski.energyplanning.domain.EnergySnapshot;
import io.github.jaroslawdabrowski.energyplanning.domain.PeriodOutcome;
import io.github.jaroslawdabrowski.energyplanning.port.out.EnergyRecordPort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheEnergyRecordRepository implements EnergyRecordPort {

    @Override
    @Transactional
    public void saveSnapshot(EnergySnapshot snapshot) {
        var entity = new EnergySnapshotEntity();
        entity.takenAt = snapshot.takenAt();
        var c = snapshot.counters();
        entity.pvKwh = c.pvKwh();
        entity.loadKwh = c.loadKwh();
        entity.gridImportKwh = c.gridImportKwh();
        entity.gridExportKwh = c.gridExportKwh();
        entity.batteryChargeKwh = c.batteryChargeKwh();
        entity.batteryDischargeKwh = c.batteryDischargeKwh();
        entity.socPercent = snapshot.socPercent();
        entity.persist();
    }

    @Override
    public Optional<EnergySnapshot> findSnapshotNear(LocalDateTime at, Duration tolerance) {
        List<EnergySnapshotEntity> candidates = EnergySnapshotEntity
                .list("takenAt >= ?1 and takenAt <= ?2", at.minus(tolerance), at.plus(tolerance));
        return candidates.stream()
                .min(Comparator.comparing(e -> Duration.between(at, e.takenAt).abs()))
                .map(PanacheEnergyRecordRepository::toDomain);
    }

    @Override
    @Transactional
    public void saveOutcome(PeriodOutcome outcome) {
        PeriodOutcomeEntity entity = PeriodOutcomeEntity
                .<PeriodOutcomeEntity>find("window = ?1 and periodFrom = ?2", outcome.window().name(), outcome.from())
                .firstResultOptional()
                .orElseGet(PeriodOutcomeEntity::new);
        entity.window = outcome.window().name();
        entity.periodFrom = outcome.from();
        entity.periodTo = outcome.to();
        entity.loadKwh = outcome.loadKwh();
        entity.pvKwh = outcome.pvKwh();
        entity.gridImportKwh = outcome.gridImportKwh();
        entity.gridExportKwh = outcome.gridExportKwh();
        entity.socAtStartPercent = outcome.socAtStartPercent();
        entity.socAtEndPercent = outcome.socAtEndPercent();
        entity.persist();
    }

    @Override
    public List<PeriodOutcome> findOutcomes(ChargeWindow window, LocalDateTime since) {
        return PeriodOutcomeEntity.<PeriodOutcomeEntity>list("window = ?1 and periodFrom >= ?2 order by periodFrom",
                        window.name(), since)
                .stream().map(PanacheEnergyRecordRepository::toDomain).toList();
    }

    @Override
    public List<PeriodOutcome> findOutcomesSince(LocalDateTime since) {
        return PeriodOutcomeEntity.<PeriodOutcomeEntity>list("periodFrom >= ?1 order by periodFrom", since)
                .stream().map(PanacheEnergyRecordRepository::toDomain).toList();
    }

    private static EnergySnapshot toDomain(EnergySnapshotEntity e) {
        return new EnergySnapshot(e.takenAt, new DailyEnergyCounters(e.pvKwh, e.loadKwh, e.gridImportKwh,
                e.gridExportKwh, e.batteryChargeKwh, e.batteryDischargeKwh), e.socPercent);
    }

    private static PeriodOutcome toDomain(PeriodOutcomeEntity e) {
        return new PeriodOutcome(ChargeWindow.valueOf(e.window), e.periodFrom, e.periodTo, e.loadKwh, e.pvKwh,
                e.gridImportKwh, e.gridExportKwh, e.socAtStartPercent, e.socAtEndPercent);
    }
}
