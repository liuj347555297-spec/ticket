package cn.servicehub.servicesystem.domain;

import java.time.Instant;

/** Immutable display and routing evidence captured in the same transaction as ticket creation. */
public record TicketServiceSystemSnapshot(String ticketId, String systemCode, String systemName, String moduleCode,
                                          String moduleName, String serviceCatalogItemId, long registryVersion,
                                          Instant capturedAt) { }
