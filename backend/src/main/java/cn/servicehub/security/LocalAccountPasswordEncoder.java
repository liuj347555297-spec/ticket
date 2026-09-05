package cn.servicehub.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pre-hashes the bounded password before adaptive bcrypt so multibyte input cannot hit bcrypt's
 * 72-byte truncation boundary. The persisted value remains a standard {bcrypt} delegated hash.
 */
public final class LocalAccountPasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder delegate=PasswordEncoderFactories.createDelegatingPasswordEncoder();
    @Override public String encode(CharSequence rawPassword){return delegate.encode(digest(rawPassword));}
    @Override public boolean matches(CharSequence rawPassword,String encodedPassword){return delegate.matches(digest(rawPassword),encodedPassword);}
    private String digest(CharSequence raw){try{return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest((raw==null?"":raw.toString()).getBytes(StandardCharsets.UTF_8)));}catch(Exception exception){throw new IllegalStateException("Password hashing is unavailable",exception);}}
}
