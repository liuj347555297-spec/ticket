package cn.servicehub.servicesystem.domain;

import java.time.Instant;

public record ServiceSystemModule(String systemCode, String code, String name, String path, boolean active,
                                  int sortOrder, long version, String updatedByIamUserId, Instant updatedAt) { }
