package cn.servicehub.config;

import java.util.Arrays;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails startup when a production process enables any direct/local identity path. */
@Component
@Profile("prod")
public class ProductionProfileGuard implements InitializingBean {
    private final Environment environment;
    private final SecurityProperties security;

    public ProductionProfileGuard(Environment environment, SecurityProperties security) {
        this.environment = environment;
        this.security = security;
    }

    @Override
    public void afterPropertiesSet() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("local-dev")) {
            throw new IllegalStateException("The local-dev profile is forbidden with prod");
        }
        if (security.allowDirectTestIdentities()) {
            throw new IllegalStateException("Direct test identities are forbidden in production");
        }
    }
}
