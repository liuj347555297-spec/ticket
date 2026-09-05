package cn.servicehub.localauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="servicehub.local-auth")
public record LocalAuthProperties(boolean enabled,String bootstrapLoginName,String bootstrapPassword,
                                  String bootstrapDisplayName,String bootstrapOrganizationId,String bootstrapOrganizationName,
                                  String localDevPassword) { }
