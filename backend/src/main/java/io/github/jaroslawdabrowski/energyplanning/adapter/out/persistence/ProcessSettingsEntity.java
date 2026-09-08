package io.github.jaroslawdabrowski.energyplanning.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row table: this app has one shared user, so there is exactly one set of
 * process settings, stored as one JSON blob under a fixed id (mirrors
 * {@code LanguagePreferenceEntity}'s pattern). The row simply not existing yet is how
 * "nothing configured" is represented - see {@code ProcessSettingsNotConfiguredException}.
 */
@Entity
@Table(name = "process_settings")
public class ProcessSettingsEntity extends PanacheEntityBase {

    static final long SINGLETON_ID = 1L;

    @Id
    public Long id = SINGLETON_ID;

    @Column(name = "settings_json", nullable = false, length = 4000)
    public String settingsJson;
}
