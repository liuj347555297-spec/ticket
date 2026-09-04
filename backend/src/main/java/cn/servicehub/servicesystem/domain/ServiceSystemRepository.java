package cn.servicehub.servicesystem.domain;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface ServiceSystemRepository {
    List<ServiceSystem> findAll();
    Optional<ServiceSystem> findByCode(String systemCode);
    ServiceSystem saveSystem(ServiceSystem value, long expectedVersion);
    List<ServiceSystemModule> findModules(String systemCode);
    Optional<ServiceSystemModule> findModule(String systemCode, String moduleCode);
    ServiceSystemModule saveModule(ServiceSystemModule value, long expectedVersion);
    List<ServiceSystemCatalogMapping> findSystemCatalogMappings(String systemCode);
    List<ServiceSystemCatalogMapping> findModuleCatalogMappings(String systemCode, String moduleCode);
    ServiceSystemCatalogMapping saveCatalogMapping(ServiceSystemCatalogMapping value, long expectedVersion);
    void saveTicketSnapshot(TicketServiceSystemSnapshot value);
    Optional<TicketServiceSystemSnapshot> findTicketSnapshot(String ticketId);
    void recordLifecycleEvent(String systemCode, long registryVersion, ServiceSystemStatus status, String action,
                              String actorIamUserId, String reason, Instant occurredAt);
}
