package cn.servicehub.notification.domain;

/** Channels are provider-neutral. Only IN_APP is enabled in the initial deployment. */
public enum MessageChannel {
    IN_APP, WPS_IM, WECHAT_WORK, CUSTOM
}
