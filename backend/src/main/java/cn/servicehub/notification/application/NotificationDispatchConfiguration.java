package cn.servicehub.notification.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({NotificationDispatchProperties.class, WpsImProperties.class})
class NotificationDispatchConfiguration {
    /**
     * Safe default for local development. A deployment that exposes an approved managed-secret
     * resolver replaces this bean; absent credentials always keep WPS delivery unavailable.
     */
    @Bean
    @ConditionalOnMissingBean(ManagedSecretResolver.class)
    ManagedSecretResolver environmentManagedSecretResolver() {
        return new EnvironmentManagedSecretResolver();
    }
}
