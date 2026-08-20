package cn.servicehub.notification.application;

import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.Notification;
import org.springframework.stereotype.Component;

/**
 * WPS IM integration boundary. It intentionally remains disabled until a reviewed service-account
 * credential, TLS endpoint allow-list, request signing and provider callback contract are supplied.
 */
@Component
public class WpsImMessageChannelAdapter implements MessageChannelPort {
    @Override public MessageChannel channel() { return MessageChannel.WPS_IM; }
    @Override public boolean enabled() { return false; }
    @Override public void deliver(Notification notification) {
        throw new IllegalStateException("WPS IM provider is disabled pending approved configuration");
    }

    /** Builds only a fixed card once a managed configuration has been approved; no caller supplies a URL or template. */
    WpsImNotificationCard renderCard(Notification notification, WpsImManagedChannelConfiguration configuration) {
        if (notification.ticketId() == null) throw new IllegalArgumentException("WPS notification requires a server-owned ticket reference");
        return new WpsImNotificationCard(notification.title(), notification.body(), configuration.targetUrl(notification.ticketId()), configuration.templateRef());
    }
}
