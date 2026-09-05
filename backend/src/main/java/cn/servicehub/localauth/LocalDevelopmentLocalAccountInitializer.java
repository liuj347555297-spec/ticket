package cn.servicehub.localauth;

import cn.servicehub.localauth.application.LocalAccountAdminService;
import cn.servicehub.servicesystem.domain.ServiceSystemRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Clearly marked fixtures; this component cannot exist outside local-dev. */
@Component @Profile("local-dev") @Order(100)
@ConditionalOnProperty(prefix="servicehub.local-auth",name="enabled",havingValue="true")
public class LocalDevelopmentLocalAccountInitializer implements ApplicationRunner {
    private final LocalAuthProperties properties;private final LocalAccountAdminService admin;private final ServiceSystemRepository systems;
    public LocalDevelopmentLocalAccountInitializer(LocalAuthProperties properties,LocalAccountAdminService admin,ServiceSystemRepository systems){this.properties=properties;this.admin=admin;this.systems=systems;}
    @Override public void run(ApplicationArguments args){String password=properties.localDevPassword();if(password==null||password.isBlank())throw new IllegalStateException("Local-dev account password must be configured");Set<String> allSystems=systems.findAll().stream().map(cn.servicehub.servicesystem.domain.ServiceSystem::code).collect(Collectors.toUnmodifiableSet());String org="ORG-LOCAL-IT";admin.seedLocalDevelopment("local-u-dev-requester","requester",password,"本地测试提单人",org,Set.of(),Set.of());for(int index=1;index<=5;index++){String suffix=String.format("%02d",index);admin.seedLocalDevelopment("local-u-dev-ops"+suffix,"ops"+suffix,password,"本地运维 "+suffix,org,Set.of("ROLE_FIRST_LINE_SUPPORT"),allSystems);}admin.seedLocalDevelopment("local-u-dev-admin","admin",password,"本地平台管理员",org,Set.of("ROLE_PLATFORM_ADMIN","ROLE_SERVICE_MANAGER","ROLE_FIRST_LINE_SUPPORT","ROLE_SECOND_LINE_SUPPORT","ROLE_AUDITOR"),allSystems);}
}
