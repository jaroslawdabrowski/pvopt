package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "run_issue")
public class RunIssueEntity extends PanacheEntity {

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "window", nullable = false, length = 20)
    public String window;

    @Column(name = "kind", nullable = false, length = 40)
    public String kind;

    @Column(name = "detail", nullable = false, length = 1000)
    public String detail;
}
