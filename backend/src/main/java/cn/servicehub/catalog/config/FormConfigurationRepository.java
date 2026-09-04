package cn.servicehub.catalog.config;

import java.util.List;
import java.util.Optional;

public interface FormConfigurationRepository {
    List<ManagedFormConfiguration> findAll();
    Optional<ManagedFormConfiguration> findById(String id);
    List<ManagedFormConfiguration> findPublishedHistory(String code);
    void savePublishedSnapshot(ManagedFormConfiguration configuration);
    ManagedFormConfiguration save(ManagedFormConfiguration configuration, long expectedVersion);
    FormPublicationRequest savePublicationRequest(FormPublicationRequest request);
    Optional<FormPublicationRequest> findPublicationRequest(String requestId);
}
