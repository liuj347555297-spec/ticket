package cn.servicehub.servicesystem.infrastructure;

import cn.servicehub.servicesystem.application.ServiceSystemConflictException;
import cn.servicehub.servicesystem.domain.ServiceSystem;
import cn.servicehub.servicesystem.domain.ServiceSystemCatalogMapping;
import cn.servicehub.servicesystem.domain.ServiceSystemModule;
import cn.servicehub.servicesystem.domain.ServiceSystemRepository;
import cn.servicehub.servicesystem.domain.TicketServiceSystemSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryServiceSystemRepository implements ServiceSystemRepository {
    private final Map<String, ServiceSystem> systems = new ConcurrentHashMap<>();
    private final Map<String, ServiceSystemModule> modules = new ConcurrentHashMap<>();
    private final Map<String, ServiceSystemCatalogMapping> mappings = new ConcurrentHashMap<>();
    private final Map<String, TicketServiceSystemSnapshot> snapshots = new ConcurrentHashMap<>();
    @Override public List<ServiceSystem> findAll() { return systems.values().stream().sorted(Comparator.comparing(ServiceSystem::name).thenComparing(ServiceSystem::code)).toList(); }
    @Override public Optional<ServiceSystem> findByCode(String code) { return Optional.ofNullable(systems.get(code)); }
    @Override public synchronized ServiceSystem saveSystem(ServiceSystem value, long expectedVersion) {
        ServiceSystem current=systems.get(value.code()); if ((current==null&&expectedVersion!=0)||(current!=null&&current.version()!=expectedVersion)) throw new ServiceSystemConflictException();
        ServiceSystem saved=new ServiceSystem(value.code(),value.name(),value.configurationItemId(),value.ownerIamUserId(),value.owningOrganizationId(),value.status(),current==null?1:current.version()+1,value.changeReason(),current==null?value.createdByIamUserId():current.createdByIamUserId(),value.updatedByIamUserId(),current==null?value.createdAt():current.createdAt(),value.updatedAt(),value.publishedAt()); systems.put(value.code(),saved); return saved;
    }
    @Override public List<ServiceSystemModule> findModules(String code) { return modules.values().stream().filter(v->v.systemCode().equals(code)).sorted(Comparator.comparingInt(ServiceSystemModule::sortOrder).thenComparing(ServiceSystemModule::code)).toList(); }
    @Override public Optional<ServiceSystemModule> findModule(String system, String module) { return Optional.ofNullable(modules.get(system+":"+module)); }
    @Override public synchronized ServiceSystemModule saveModule(ServiceSystemModule value,long expectedVersion) { String key=value.systemCode()+":"+value.code(); ServiceSystemModule current=modules.get(key); if((current==null&&expectedVersion!=0)||(current!=null&&current.version()!=expectedVersion))throw new ServiceSystemConflictException(); ServiceSystemModule saved=new ServiceSystemModule(value.systemCode(),value.code(),value.name(),value.path(),value.active(),value.sortOrder(),current==null?1:current.version()+1,value.updatedByIamUserId(),value.updatedAt());modules.put(key,saved);return saved; }
    @Override public List<ServiceSystemCatalogMapping> findSystemCatalogMappings(String system) { return mappings.values().stream().filter(v->v.systemCode().equals(system)&&v.moduleCode()==null).sorted(Comparator.comparing(ServiceSystemCatalogMapping::serviceCatalogItemId)).toList(); }
    @Override public List<ServiceSystemCatalogMapping> findModuleCatalogMappings(String system,String module) { return mappings.values().stream().filter(v->v.systemCode().equals(system)&&module.equals(v.moduleCode())).sorted(Comparator.comparing(ServiceSystemCatalogMapping::serviceCatalogItemId)).toList(); }
    @Override public synchronized ServiceSystemCatalogMapping saveCatalogMapping(ServiceSystemCatalogMapping value,long expectedVersion) { String key=value.systemCode()+":"+(value.moduleCode()==null?"_":value.moduleCode())+":"+value.serviceCatalogItemId();ServiceSystemCatalogMapping current=mappings.get(key);if((current==null&&expectedVersion!=0)||(current!=null&&current.version()!=expectedVersion))throw new ServiceSystemConflictException();ServiceSystemCatalogMapping saved=new ServiceSystemCatalogMapping(value.systemCode(),value.moduleCode(),value.serviceCatalogItemId(),value.active(),value.defaultMapping(),current==null?1:current.version()+1,value.updatedByIamUserId(),value.updatedAt());mappings.put(key,saved);return saved; }
    @Override public void saveTicketSnapshot(TicketServiceSystemSnapshot value) { snapshots.putIfAbsent(value.ticketId(),value); }
    @Override public Optional<TicketServiceSystemSnapshot> findTicketSnapshot(String id) { return Optional.ofNullable(snapshots.get(id)); }
    @Override public void recordLifecycleEvent(String systemCode,long registryVersion,cn.servicehub.servicesystem.domain.ServiceSystemStatus status,String action,String actor,String reason,java.time.Instant occurredAt) { /* audit publisher is the test/dev durable boundary */ }
}
