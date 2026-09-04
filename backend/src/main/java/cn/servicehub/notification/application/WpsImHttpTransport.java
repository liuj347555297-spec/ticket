package cn.servicehub.notification.application;

/** Provider transport boundary, kept separate from routing and notification authorization. */
public interface WpsImHttpTransport {
    WpsImHttpResponse post(WpsImManagedChannelConfiguration configuration, WpsImHttpRequest request);
}
