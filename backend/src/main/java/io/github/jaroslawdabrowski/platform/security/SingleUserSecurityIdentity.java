package io.github.jaroslawdabrowski.platform.security;

import io.quarkus.security.credential.Credential;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

import java.security.Permission;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

/**
 * Minimal SecurityIdentity implementation for a single shared user with no
 * roles/permissions - sufficient for gating access with Basic Auth on a LAN.
 */
final class SingleUserSecurityIdentity implements SecurityIdentity {

    private final String username;

    SingleUserSecurityIdentity(String username) {
        this.username = username;
    }

    @Override
    public Principal getPrincipal() {
        return () -> username;
    }

    @Override
    public boolean isAnonymous() {
        return false;
    }

    @Override
    public Set<String> getRoles() {
        return Set.of();
    }

    @Override
    public boolean hasRole(String role) {
        return false;
    }

    @Override
    public Set<Permission> getPermissions() {
        return Set.of();
    }

    @Override
    public <T extends Credential> T getCredential(Class<T> credentialType) {
        return null;
    }

    @Override
    public Set<Credential> getCredentials() {
        return Set.of();
    }

    @Override
    public <T> T getAttribute(String name) {
        return null;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public Uni<Boolean> checkPermission(Permission permission) {
        return Uni.createFrom().item(true);
    }
}
