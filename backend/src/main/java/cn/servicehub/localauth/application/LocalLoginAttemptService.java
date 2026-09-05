package cn.servicehub.localauth.application;

import cn.servicehub.localauth.domain.LocalAccount;
import cn.servicehub.localauth.domain.LocalAccountRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalLoginAttemptService {
    private static final int MAX_FAILURES=5; private static final Duration LOCK_DURATION=Duration.ofMinutes(15);
    private final LocalAccountRepository accounts; private final Clock clock=Clock.systemUTC();
    public LocalLoginAttemptService(LocalAccountRepository accounts){this.accounts=accounts;}

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(String accountId){accounts.findById(accountId).ifPresent(current->{if(!current.enabled())return;var now=clock.instant();if(current.lockedUntil()!=null&&current.lockedUntil().isAfter(now))return;int previous=current.lockedUntil()==null?current.failedLoginCount():0;int count=Math.min(1_000_000,previous+1);var locked=count>=MAX_FAILURES?now.plus(LOCK_DURATION):null;accounts.update(copy(current,count,locked,current.version()+1,now),current.version());});}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public LocalAccount succeeded(String accountId){LocalAccount current=accounts.findById(accountId).orElseThrow(LocalAuthenticationFailedException::new);var now=clock.instant();if(current.failedLoginCount()==0&&current.lockedUntil()==null)return current;return accounts.update(copy(current,0,null,current.version()+1,now),current.version());}
    private LocalAccount copy(LocalAccount a,int failures,java.time.Instant locked,long version,java.time.Instant now){return new LocalAccount(a.id(),a.loginName(),a.normalizedLoginName(),a.passwordHash(),a.displayName(),a.organizationId(),a.enabled(),failures,locked,a.passwordChangedAt(),a.sessionVersion(),version,a.createdBy(),a.updatedBy(),a.createdAt(),now);}
}
