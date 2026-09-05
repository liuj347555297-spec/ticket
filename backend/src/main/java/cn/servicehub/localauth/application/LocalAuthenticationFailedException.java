package cn.servicehub.localauth.application;

import org.springframework.security.core.AuthenticationException;

/** One public failure class prevents account/disabled/locked/password enumeration. */
public final class LocalAuthenticationFailedException extends AuthenticationException {
    public LocalAuthenticationFailedException() { super("Invalid credentials"); }
}
