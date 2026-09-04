package cn.servicehub.servicesystem.web;

import cn.servicehub.servicesystem.application.ServiceSystemRegistryService;
import cn.servicehub.servicesystem.domain.ServiceSystem;
import cn.servicehub.servicesystem.domain.ServiceSystemCatalogMapping;
import cn.servicehub.servicesystem.domain.ServiceSystemModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Back-office only. State changes are version-checked and remain inside the actor's organization scope. */
@RestController
@RequestMapping("/api/v1/admin/service-systems")
public class ServiceSystemAdminController {
    private final ServiceSystemRegistryService service;
    public ServiceSystemAdminController(ServiceSystemRegistryService service){this.service=service;}
    @GetMapping public PageResponse list(@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="50") @Min(1) @Max(100) int pageSize){List<ServiceSystem> all=service.listManaged();int from=Math.min((page-1)*pageSize,all.size());int to=Math.min(from+pageSize,all.size());return new PageResponse(all.subList(from,to),page,pageSize,all.size());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ServiceSystem create(@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String ignored,@Valid @RequestBody SystemRequest request){return service.create(request.input());}
    @GetMapping("/{systemCode}") public ServiceSystem get(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode){return service.getManaged(systemCode);}
    @PutMapping("/{systemCode}") public ServiceSystem update(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,@Valid @RequestBody SystemRequest request){return service.update(systemCode,request.withVersion(Long.parseLong(ifMatch.replace("\"",""))).input());}
    @PostMapping("/{systemCode}/publish") public ServiceSystem publish(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,@Valid @RequestBody ChangeRequest request){return service.publish(systemCode,Long.parseLong(ifMatch.replace("\"","")),request.reason());}
    @PostMapping("/{systemCode}/retire") public ServiceSystem retire(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,@Valid @RequestBody ChangeRequest request){return service.retire(systemCode,Long.parseLong(ifMatch.replace("\"","")),request.reason());}
    @GetMapping("/{systemCode}/modules") public List<ServiceSystemModule> modules(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode){return service.modulesManaged(systemCode);}
    @PutMapping("/{systemCode}/modules/{moduleCode}") public ServiceSystemModule saveModule(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") String moduleCode,@RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,@Valid @RequestBody ModuleRequest request){return service.saveModule(systemCode,new ServiceSystemRegistryService.ModuleInput(systemCode,moduleCode,request.name(),request.path(),request.active(),request.sortOrder(),Long.parseLong(ifMatch.replace("\"",""))));}
    @GetMapping("/{systemCode}/catalog-mappings") public List<ServiceSystemCatalogMapping> systemMappings(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode){return service.systemMappingsManaged(systemCode);}
    @PutMapping("/{systemCode}/catalog-mappings/{catalogItemId}") public ServiceSystemCatalogMapping saveSystemMapping(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@PathVariable @Pattern(regexp="^[A-Za-z0-9_-]{3,64}$") String catalogItemId,@RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,@Valid @RequestBody MappingRequest request){return service.saveSystemMapping(systemCode,new ServiceSystemRegistryService.MappingInput(catalogItemId,request.active(),request.defaultMapping(),Long.parseLong(ifMatch.replace("\"",""))));}
    @GetMapping("/{systemCode}/modules/{moduleCode}/catalog-mappings") public List<ServiceSystemCatalogMapping> moduleMappings(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") String moduleCode){return service.moduleMappingsManaged(systemCode,moduleCode);}
    @PutMapping("/{systemCode}/modules/{moduleCode}/catalog-mappings/{catalogItemId}") public ServiceSystemCatalogMapping saveModuleMapping(@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String systemCode,@PathVariable @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") String moduleCode,@PathVariable @Pattern(regexp="^[A-Za-z0-9_-]{3,64}$") String catalogItemId,@RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,@Valid @RequestBody MappingRequest request){return service.saveModuleMapping(systemCode,moduleCode,new ServiceSystemRegistryService.MappingInput(catalogItemId,request.active(),request.defaultMapping(),Long.parseLong(ifMatch.replace("\"",""))));}
    public record SystemRequest(@NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String code,@NotBlank @Size(max=200) String name,@Size(max=128) String configurationItemId,@Size(max=128) String ownerIamUserId,@NotBlank @Pattern(regexp="^[A-Za-z0-9._:-]{1,128}$") String owningOrganizationId,@Min(0) long version,@NotBlank @Size(min=4,max=500) String reason){ServiceSystemRegistryService.SystemInput input(){return new ServiceSystemRegistryService.SystemInput(code,name,configurationItemId,ownerIamUserId,owningOrganizationId,version,reason);}SystemRequest withVersion(long v){return new SystemRequest(code,name,configurationItemId,ownerIamUserId,owningOrganizationId,v,reason);}}
    public record ModuleRequest(@NotBlank @Size(max=200) String name,@Size(max=500) String path,boolean active,@Min(0) @Max(10000) int sortOrder){}
    public record MappingRequest(boolean active,boolean defaultMapping){}
    public record ChangeRequest(@NotBlank @Size(min=4,max=500) String reason){}
    public record PageResponse(List<ServiceSystem> items,int page,int pageSize,int total){}
}
