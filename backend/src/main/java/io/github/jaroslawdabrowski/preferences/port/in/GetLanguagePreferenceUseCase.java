package io.github.jaroslawdabrowski.preferences.port.in;

import io.github.jaroslawdabrowski.preferences.domain.Language;

public interface GetLanguagePreferenceUseCase {

    Language currentLanguage();
}
