package io.github.jaroslawdabrowski.preferences.adapter.in.web;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import io.github.jaroslawdabrowski.preferences.domain.Language;
import io.github.jaroslawdabrowski.preferences.port.in.GetLanguagePreferenceUseCase;
import io.github.jaroslawdabrowski.preferences.port.in.SetLanguagePreferenceUseCase;

@Path("/api/preferences/language")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class PreferencesResource {

    private final GetLanguagePreferenceUseCase getLanguagePreferenceUseCase;
    private final SetLanguagePreferenceUseCase setLanguagePreferenceUseCase;

    public PreferencesResource(GetLanguagePreferenceUseCase getLanguagePreferenceUseCase,
            SetLanguagePreferenceUseCase setLanguagePreferenceUseCase) {
        this.getLanguagePreferenceUseCase = getLanguagePreferenceUseCase;
        this.setLanguagePreferenceUseCase = setLanguagePreferenceUseCase;
    }

    @GET
    public LanguagePreferenceResponse get() {
        return new LanguagePreferenceResponse(getLanguagePreferenceUseCase.currentLanguage().name());
    }

    @PUT
    public LanguagePreferenceResponse set(LanguagePreferenceResponse request) {
        var language = Language.valueOf(request.language());
        setLanguagePreferenceUseCase.changeLanguage(language);
        return new LanguagePreferenceResponse(language.name());
    }
}
