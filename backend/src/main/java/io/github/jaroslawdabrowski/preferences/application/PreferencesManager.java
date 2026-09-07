package io.github.jaroslawdabrowski.preferences.application;

import jakarta.enterprise.context.ApplicationScoped;
import io.github.jaroslawdabrowski.preferences.domain.Language;
import io.github.jaroslawdabrowski.preferences.port.in.GetLanguagePreferenceUseCase;
import io.github.jaroslawdabrowski.preferences.port.in.SetLanguagePreferenceUseCase;
import io.github.jaroslawdabrowski.preferences.port.out.LanguagePreferencePort;

/**
 * The single orchestrator for the language preference: falls back to
 * {@link Language#EN} when nothing has been saved yet.
 */
@ApplicationScoped
public class PreferencesManager implements GetLanguagePreferenceUseCase, SetLanguagePreferenceUseCase {

    private final LanguagePreferencePort languagePreferencePort;

    public PreferencesManager(LanguagePreferencePort languagePreferencePort) {
        this.languagePreferencePort = languagePreferencePort;
    }

    @Override
    public Language currentLanguage() {
        return languagePreferencePort.load().orElse(Language.EN);
    }

    @Override
    public void changeLanguage(Language language) {
        languagePreferencePort.save(language);
    }
}
