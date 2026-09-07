package io.github.jaroslawdabrowski.platform.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The only authentication mechanism: a single shared user with a BCrypt-hashed
 * password, configured in application.properties (pvopt.security.username /
 * password-hash). The app is only reachable on the local network, so one
 * login is enough - no roles/permissions.
 */
@ApplicationScoped
public class BasicAuthIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {

    private final BasicAuthConfig config;

    public BasicAuthIdentityProvider(BasicAuthConfig config) {
        this.config = config;
    }

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
            AuthenticationRequestContext context) {
        String providedUsername = request.getUsername();
        String providedPassword = new String(request.getPassword().getPassword());

        boolean usernameMatches = config.username().equals(providedUsername);
        boolean passwordMatches = usernameMatches && BcryptUtil.matches(providedPassword, config.passwordHash());

        if (!passwordMatches) {
            return Uni.createFrom().nullItem();
        }

        return Uni.createFrom().item(new SingleUserSecurityIdentity(providedUsername));
    }
}
