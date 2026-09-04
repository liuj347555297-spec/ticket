package cn.servicehub.notification.application;

/** Bounded provider response body. Only the adapter parses it into a safe receipt. */
public record WpsImHttpResponse(int statusCode, String body) { }
