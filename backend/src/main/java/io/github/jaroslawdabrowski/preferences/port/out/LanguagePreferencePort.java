package io.github.jaroslawdabrowski.preferences.port.out;

import io.github.jaroslawdabrowski.preferences.domain.Language;

import java.util.Optional;

public interface LanguagePreferencePort {

    Optional<Language> load();

    void save(Language language);
}
