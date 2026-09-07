package io.github.jaroslawdabrowski.preferences.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import io.github.jaroslawdabrowski.preferences.domain.Language;
import io.github.jaroslawdabrowski.preferences.port.out.LanguagePreferencePort;

import java.util.Optional;

@ApplicationScoped
public class PanacheLanguagePreferenceRepository
        implements PanacheRepository<LanguagePreferenceEntity>, LanguagePreferencePort {

    @Override
    public Optional<Language> load() {
        return findByIdOptional(LanguagePreferenceEntity.SINGLETON_ID)
                .map(entity -> Language.valueOf(entity.language));
    }

    @Override
    @Transactional
    public void save(Language language) {
        var entity = findByIdOptional(LanguagePreferenceEntity.SINGLETON_ID)
                .orElseGet(LanguagePreferenceEntity::new);
        entity.language = language.name();
        persist(entity);
    }
}
