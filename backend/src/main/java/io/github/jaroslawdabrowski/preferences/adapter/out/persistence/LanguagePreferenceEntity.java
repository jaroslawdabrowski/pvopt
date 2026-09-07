package io.github.jaroslawdabrowski.preferences.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row table: this app has one shared user, so there is exactly one
 * language preference, always stored under a fixed id.
 */
@Entity
@Table(name = "language_preference")
public class LanguagePreferenceEntity extends PanacheEntityBase {

    static final long SINGLETON_ID = 1L;

    @Id
    public Long id = SINGLETON_ID;

    @Column(name = "language", nullable = false, length = 2)
    public String language;
}
