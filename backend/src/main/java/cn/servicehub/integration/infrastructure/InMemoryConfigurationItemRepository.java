package cn.servicehub.integration.infrastructure;

import cn.servicehub.integration.domain.ConfigurationItem;
import cn.servicehub.integration.domain.ConfigurationItemRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryConfigurationItemRepository implements ConfigurationItemRepository {
    private final Map<String, ConfigurationItem> items = Map.of(
        "CI-PORTAL-001", new ConfigurationItem("CI-PORTAL-001", "CMDB", "核协 E+ 门户", "APPLICATION", "IN_SERVICE", "org-it"),
        "CI-NET-001", new ConfigurationItem("CI-NET-001", "CMDB", "总部办公网络", "NETWORK", "IN_SERVICE", "org-it"));
    private final ConcurrentHashMap<String, List<String>> ticketItems = new ConcurrentHashMap<>();
    @Override public Optional<ConfigurationItem> findById(String id) { return Optional.ofNullable(items.get(id)); }
    @Override public List<ConfigurationItem> findByTicketId(String ticketId) { return ticketItems.getOrDefault(ticketId, List.of()).stream().map(items::get).filter(java.util.Objects::nonNull).toList(); }
    @Override public List<ConfigurationItem> findByOrganizationId(String organizationId) { return items.values().stream().filter(item -> item.organizationId().equals(organizationId)).sorted(java.util.Comparator.comparing(ConfigurationItem::id)).toList(); }
    @Override public void replaceTicketAssociations(String ticketId, List<String> configurationItemIds) { ticketItems.put(ticketId, List.copyOf(configurationItemIds)); }
}
