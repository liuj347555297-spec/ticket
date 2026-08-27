package cn.servicehub.workflow.application;

import java.util.List;

/** Page metadata deliberately excludes a global total so unreadable approval tasks cannot be counted or inferred. */
public record ApprovalTaskInbox(List<ApprovalTaskInboxItem> items, int page, int pageSize) { }
