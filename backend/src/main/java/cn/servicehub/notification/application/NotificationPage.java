package cn.servicehub.notification.application;
import cn.servicehub.notification.domain.Notification;
import java.util.List;
public record NotificationPage(List<Notification> items, int page, int pageSize, long total) { }
