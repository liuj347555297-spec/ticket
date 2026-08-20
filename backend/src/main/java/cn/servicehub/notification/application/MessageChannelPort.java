package cn.servicehub.notification.application;

import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.Notification;

/** Provider boundary. Implementations must never accept arbitrary browser target identifiers. */
public interface MessageChannelPort {
    MessageChannel channel();
    boolean enabled();
    void deliver(Notification notification);
}
