package cn.servicehub.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class VerifiedLocalAuthenticationFactory {
    public VerifiedLocalAuthentication create(String accountId,long sessionVersion){
        return new VerifiedLocalAuthentication(accountId,sessionVersion,List.of(new SimpleGrantedAuthority("ROLE_REQUESTER")));
    }
}
