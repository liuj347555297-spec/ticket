package cn.servicehub.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Security settings are configuration, never supplied by browser requests. */
@ConfigurationProperties(prefix = "servicehub.security")
public record SecurityProperties(List<String> allowedOrigins, boolean allowDirectTestIdentities, boolean devHeaderEnabled) {
    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
