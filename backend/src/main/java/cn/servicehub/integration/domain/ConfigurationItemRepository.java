package cn.servicehub.integration.domain;

import java.util.List;
import java.util.Optional;

public interface ConfigurationItemRepository {
    Optional<ConfigurationItem> findById(String id);
    List<ConfigurationItem> findByTicketId(String ticketId);
    List<ConfigurationItem> findByOrganizationId(String organizationId);
    void replaceTicketAssociations(String ticketId, List<String> configurationItemIds);
}
