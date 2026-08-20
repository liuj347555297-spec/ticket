package cn.servicehub.notification.application;
import cn.servicehub.notification.domain.NotificationDelivery;
import java.util.List;
public record NotificationDeliveryPage(List<NotificationDelivery> items, int page, int pageSize, long total) { }
