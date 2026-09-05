package cn.servicehub.localauth.application;

import cn.servicehub.localauth.LocalAuthProperties;
import cn.servicehub.localauth.domain.LocalAccountRepository;
import cn.servicehub.security.VerifiedLocalAuthentication;
import cn.servicehub.security.VerifiedLocalAuthenticationFactory;
import java.text.Normalizer;
import java.time.Clock;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LocalAuthenticationService {
    private static final Pattern LOGIN=Pattern.compile("^[a-z0-9][a-z0-9._@-]{2,127}$");
    private final LocalAccountRepository accounts;private final LocalLoginAttemptService attempts;private final PasswordEncoder passwords;private final VerifiedLocalAuthenticationFactory factory;private final LocalAuthProperties properties;private final Clock clock=Clock.systemUTC();private final String dummyHash;
    public LocalAuthenticationService(LocalAccountRepository accounts,LocalLoginAttemptService attempts,PasswordEncoder passwords,VerifiedLocalAuthenticationFactory factory,LocalAuthProperties properties){this.accounts=accounts;this.attempts=attempts;this.passwords=passwords;this.factory=factory;this.properties=properties;this.dummyHash=passwords.encode("servicehub-non-account-dummy-value");}
    public VerifiedLocalAuthentication authenticate(String suppliedLogin,String password){if(!properties.enabled())throw new LocalAuthenticationFailedException();String normalized=normalizeLogin(suppliedLogin);var account=normalized==null?null:accounts.findByNormalizedLoginName(normalized).orElse(null);String candidate=password==null?"":password;boolean matched=passwords.matches(candidate,account==null?dummyHash:account.passwordHash());var now=clock.instant();boolean available=account!=null&&account.enabled()&&(account.lockedUntil()==null||!account.lockedUntil().isAfter(now));if(!matched||!available){if(account!=null)try{attempts.failed(account.id());}catch(LocalAccountConflictException ignored){/* a concurrent failure still receives the same public result */}throw new LocalAuthenticationFailedException();}try{var current=attempts.succeeded(account.id());return factory.create(current.id(),current.sessionVersion());}catch(LocalAccountConflictException ignored){throw new LocalAuthenticationFailedException();}}
    public static String normalizeLogin(String value){if(value==null)return null;String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);return LOGIN.matcher(normalized).matches()?normalized:null;}
}
