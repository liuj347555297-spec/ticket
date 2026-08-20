package cn.servicehub.notification.web;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
record NotificationMarkReadRequest(@NotNull @PositiveOrZero Long version) { }
