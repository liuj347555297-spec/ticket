package cn.servicehub.integration.domain;

/** Read-only CMDB projection. The platform neither edits nor discovers CIs. */
public record ConfigurationItem(String id, String sourceCode, String name, String ciType, String status,
                                String organizationId) { }
