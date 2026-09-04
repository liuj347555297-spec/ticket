package cn.servicehub.servicesystem.domain;

import java.time.Instant;

/** A module mapping wins over a system mapping when the requester selected a module. */
public record ServiceSystemCatalogMapping(String systemCode, String moduleCode, String serviceCatalogItemId,
                                          boolean active, boolean defaultMapping, long version,
                                          String updatedByIamUserId, Instant updatedAt) { }
