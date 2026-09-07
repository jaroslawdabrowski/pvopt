package io.github.jaroslawdabrowski.platform.security;

import io.smallrye.config.ConfigMapping;

/**
 * Basic Auth username and (BCrypt) password hash - loaded from the configuration
 * file (application.properties); the plaintext password is never stored.
 */
@ConfigMapping(prefix = "pvopt.security")
public interface BasicAuthConfig {

    String username();

    String passwordHash();
}
