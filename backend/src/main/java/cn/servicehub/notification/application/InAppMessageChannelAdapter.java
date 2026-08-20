package cn.servicehub.notification.application;

import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.Notification;
import org.springframework.stereotype.Component;

/** In-app delivery is represented by the durable notification record and needs no network call. */
@Component
public class InAppMessageChannelAdapter implements MessageChannelPort {
    @Override public MessageChannel channel() { return MessageChannel.IN_APP; }
    @Override public boolean enabled() { return true; }
    @Override public void deliver(Notification notification) { /* durable record is the delivery */ }
}
