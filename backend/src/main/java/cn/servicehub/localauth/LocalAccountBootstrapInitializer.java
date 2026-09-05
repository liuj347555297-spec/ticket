package cn.servicehub.localauth;

import cn.servicehub.localauth.application.LocalAccountAdminService;
import cn.servicehub.localauth.domain.LocalAccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** One-shot, environment-owned bootstrap. No password has a code or configuration-file default. */
@Component
public class LocalAccountBootstrapInitializer implements ApplicationRunner {
    private final LocalAuthProperties properties;private final LocalAccountRepository accounts;private final LocalAccountAdminService admin;
    public LocalAccountBootstrapInitializer(LocalAuthProperties properties,LocalAccountRepository accounts,LocalAccountAdminService admin){this.properties=properties;this.accounts=accounts;this.admin=admin;}
    @Override public void run(ApplicationArguments args){if(!properties.enabled()||accounts.count()!=0)return;boolean any=notBlank(properties.bootstrapLoginName())||notBlank(properties.bootstrapPassword())||notBlank(properties.bootstrapDisplayName())||notBlank(properties.bootstrapOrganizationId())||notBlank(properties.bootstrapOrganizationName());if(!any)return;if(!notBlank(properties.bootstrapLoginName())||!notBlank(properties.bootstrapPassword())||!notBlank(properties.bootstrapDisplayName())||!notBlank(properties.bootstrapOrganizationId())||!notBlank(properties.bootstrapOrganizationName()))throw new IllegalStateException("All local account bootstrap settings are required together");admin.bootstrap(properties.bootstrapLoginName(),properties.bootstrapPassword(),properties.bootstrapDisplayName(),properties.bootstrapOrganizationId(),properties.bootstrapOrganizationName());}
    private static boolean notBlank(String value){return value!=null&&!value.isBlank();}
}
