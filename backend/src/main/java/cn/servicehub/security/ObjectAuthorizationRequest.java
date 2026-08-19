package cn.servicehub.security;

import java.util.Map;

/** Resource-specific context (organisation, queue and ticket relationship) must be populated server-side. */
public record ObjectAuthorizationRequest(String resourceType, String resourceId, ObjectAction action,
                                         Map<String, String> serverResolvedContext) {
    public ObjectAuthorizationRequest {
        serverResolvedContext = serverResolvedContext == null ? Map.of() : Map.copyOf(serverResolvedContext);
    }
}
