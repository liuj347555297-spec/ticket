package cn.servicehub.security;

/** Mandatory service-layer gate for BOLA/IDOR-sensitive resources. */
public interface ObjectAuthorizationService {
    void requireAuthorized(CurrentUser user, ObjectAuthorizationRequest request);
}
