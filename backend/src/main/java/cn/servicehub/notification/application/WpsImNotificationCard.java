package cn.servicehub.notification.application;

/** Provider-neutral, fixed-template WPS card payload. It is not an HTTP request and carries no token. */
public record WpsImNotificationCard(String title, String summary, String targetUrl, String templateRef) { }
