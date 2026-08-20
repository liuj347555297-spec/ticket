package cn.servicehub.notification.web;
import java.util.List;
record NotificationPageResponse(List<NotificationResponse> items, int page, int pageSize, long total) { }
