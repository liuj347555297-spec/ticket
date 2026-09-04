package cn.servicehub.servicesystem.web;

import cn.servicehub.servicesystem.application.ServiceSystemRegistryService;
import cn.servicehub.servicesystem.domain.ServiceSystem;
import cn.servicehub.servicesystem.domain.ServiceSystemCatalogMapping;
import cn.servicehub.servicesystem.domain.ServiceSystemModule;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requester-safe projections: published objects in the current IAM organization only. */
@RestController
@RequestMapping("/api/v1/service-systems")
public class ServiceSystemController {
    private final ServiceSystemRegistryService service;
    private final cn.servicehub.catalog.application.ServiceCatalogService catalogs;
    public ServiceSystemController(ServiceSystemRegistryService service, cn.servicehub.catalog.application.ServiceCatalogService catalogs){this.service=service;this.catalogs=catalogs;}
    @GetMapping public List<ServiceSystem> systems(){return service.listAvailable();}
    @GetMapping("/{systemCode}/modules") public List<ServiceSystemModule> modules(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode){return service.listAvailableModules(systemCode);}
    @GetMapping("/{systemCode}/catalog-mappings") public List<ServiceSystemCatalogMapping> mappings(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@RequestParam(required=false) @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") String moduleCode){return service.listAvailableMappings(systemCode,moduleCode);}
    @GetMapping("/{systemCode}/catalog-items")
    public List<cn.servicehub.catalog.web.ServiceCatalogPageResponse.Item> catalogItems(
            @PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,
            @RequestParam(required=false) @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") String moduleCode) {
        var items = service.listAvailableCatalogItems(systemCode, moduleCode);
        var tags = catalogs.standardTags();
        return items.stream().map(item -> cn.servicehub.catalog.web.ServiceCatalogPageResponse.item(item, catalogs, tags)).toList();
    }
}
