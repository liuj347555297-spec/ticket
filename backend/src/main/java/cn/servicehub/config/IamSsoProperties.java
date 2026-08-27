package cn.servicehub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** OIDC is opt-in; client registration details stay in deployment secrets/configuration. */
@ConfigurationProperties(prefix = "servicehub.iam-sso")
public record IamSsoProperties(boolean enabled, String iamUserIdAttribute) {
    public IamSsoProperties {
        iamUserIdAttribute = iamUserIdAttribute == null || iamUserIdAttribute.isBlank() ? "sub" : iamUserIdAttribute.trim();
    }
}
