package cn.servicehub.notification.application;

/** Pre-rendered immutable request. `credential` is never persisted, logged or returned to a UI. */
public record WpsImHttpRequest(String idempotencyKey, String credential, String jsonBody) { }
