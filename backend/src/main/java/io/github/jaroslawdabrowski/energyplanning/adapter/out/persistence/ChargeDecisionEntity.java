package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "charge_decision")
public class ChargeDecisionEntity extends PanacheEntity {

    @Column(name = "decided_at", nullable = false)
    public Instant decidedAt;

    @Column(name = "charge_from_grid", nullable = false)
    public boolean chargeFromGrid;

    @Column(name = "target_soc_percent", nullable = false)
    public int targetSocPercent;

    @Column(name = "reason", nullable = false, length = 50)
    public String reason;

    @Column(name = "reason_params_json", nullable = false, length = 1000)
    public String reasonParamsJson;
}
