package cn.servicehub.catalog.web;

import cn.servicehub.catalog.config.ConfigurableFormFieldType;
import cn.servicehub.catalog.config.ConfiguredFormField;
import cn.servicehub.catalog.config.FormCondition;
import cn.servicehub.catalog.config.FormConditionOperator;
import cn.servicehub.catalog.config.FormConfigurationService;
import cn.servicehub.catalog.config.FormConfigurationStatus;
import cn.servicehub.catalog.config.FormPublicationRequest;
import cn.servicehub.catalog.config.ManagedFormConfiguration;
import cn.servicehub.catalog.config.TagPolicy;
import cn.servicehub.ticket.domain.TicketType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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

/** Management endpoints intentionally have no requester equivalent. */
@RestController
@RequestMapping("/api/v1/admin/service-catalog/items")
public class ServiceCatalogAdminController {
    private final FormConfigurationService service;
    public ServiceCatalogAdminController(FormConfigurationService service) { this.service = service; }
    @GetMapping public PageResponse list(@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) FormConfigurationStatus status) { List<Response> all=service.list().stream().filter(v->status==null||v.status()==status).map(Response::from).toList();int from=Math.min((page-1)*pageSize,all.size());int to=Math.min(from+pageSize,all.size());return new PageResponse(all.subList(from,to),page,pageSize,all.size()); }
    @GetMapping("/{id}") public Response get(@PathVariable @Pattern(regexp="^SC-[A-Za-z0-9_-]{3,60}$") String id) { return Response.from(service.get(id)); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Response create(@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String ignoredKey,@Valid @RequestBody UpsertRequest request) { return Response.from(service.create(request.toInput())); }
    @PutMapping("/{id}") public Response update(@PathVariable @Pattern(regexp="^SC-[A-Za-z0-9_-]{3,60}$") String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String ignoredKey,@Valid @RequestBody UpsertRequest request) { return Response.from(service.update(id,request.toInput())); }
    @PostMapping("/{id}/publish-requests") @ResponseStatus(HttpStatus.ACCEPTED) public PublicationResponse requestPublish(@PathVariable @Pattern(regexp="^SC-[A-Za-z0-9_-]{3,60}$") String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String ignoredKey,@Valid @RequestBody PublishRequest request) { return PublicationResponse.from(service.requestPublication(id,request.version(),request.reason())); }
    @PostMapping("/{id}/publish-requests/{requestId}/approve") public Response approve(@PathVariable @Pattern(regexp="^SC-[A-Za-z0-9_-]{3,60}$") String id,@PathVariable @Pattern(regexp="^FPR-[0-9a-fA-F-]{36}$") String requestId,@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String ignoredKey) { return Response.from(service.approvePublication(id,requestId)); }
    @PostMapping("/{id}/retire") public Response retire(@PathVariable @Pattern(regexp="^SC-[A-Za-z0-9_-]{3,60}$") String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String ignoredKey,@Valid @RequestBody PublishRequest request) { return Response.from(service.retire(id,request.version(),request.reason())); }
    @PostMapping("/{id}/rollback") public Response rollback(@PathVariable @Pattern(regexp="^SC-[A-Za-z0-9_-]{3,60}$") String id,@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String ignoredKey,@Valid @RequestBody RollbackRequest request) { return Response.from(service.rollback(id,request.sourceFormVersion(),request.reason())); }

    public record UpsertRequest(@Min(0) long version,@NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{2,63}$") String code,@NotBlank @Size(max=200) String name,@Size(max=500) String summary,@NotNull TicketType ticketType,@NotBlank @Size(max=64) String categoryCode,@Size(max=500) List<@Pattern(regexp="^[A-Za-z0-9:_-]{2,128}$") String> applicableOrganizationIds,@Size(min=1,max=200) List<@Valid FieldRequest> fields,@Valid TagPolicyRequest tagPolicy,@NotBlank @Size(min=4,max=500) String reason) { FormConfigurationService.DraftInput toInput(){return new FormConfigurationService.DraftInput(version,code,name,summary,ticketType,categoryCode,applicableOrganizationIds,fields==null?List.of():fields.stream().map(FieldRequest::toDomain).toList(),tagPolicy==null?null:tagPolicy.toDomain(),reason);} }
    public record FieldRequest(@NotBlank @Pattern(regexp="^[a-z][a-z0-9_]{0,63}$") String code,@NotBlank @Size(max=80) String label,@NotNull ConfigurableFormFieldType type,boolean required,@Size(max=4000) String defaultValue,@Size(max=300) String helpText,@Min(1) @Max(4000) Integer maxLength,@Pattern(regexp="^[A-Z][A-Z0-9_]{1,62}$") String dictionaryCode,@Min(1) @Max(500) int displayOrder,@Size(max=10) List<@Valid ConditionRequest> visibleWhen,@Size(max=10) List<@Valid ConditionRequest> requiredWhen) { ConfiguredFormField toDomain(){return new ConfiguredFormField(code,label,type,required,defaultValue,helpText,maxLength,dictionaryCode,displayOrder,visibleWhen==null?List.of():visibleWhen.stream().map(ConditionRequest::toDomain).toList(),requiredWhen==null?List.of():requiredWhen.stream().map(ConditionRequest::toDomain).toList());} }
    public record ConditionRequest(@NotBlank @Pattern(regexp="^[a-z][a-z0-9_]{0,63}$") String fieldCode,@NotNull FormConditionOperator operator,@Size(max=50) List<@Size(max=200) String> values) { FormCondition toDomain(){return new FormCondition(fieldCode,operator,values);} }
    public record TagPolicyRequest(boolean allowStandardTags,boolean allowFreeTags,@Min(0) @Max(20) int maxTags,@Size(max=100) List<@Pattern(regexp="^TAG-[A-Za-z0-9_-]{3,60}$") String> allowedStandardTagCodes) { TagPolicy toDomain(){return new TagPolicy(allowStandardTags,allowFreeTags,maxTags,allowedStandardTagCodes);} }
    public record PublishRequest(@Min(0) long version,@NotBlank @Size(min=4,max=500) String reason) { }
    public record RollbackRequest(@Min(1) int sourceFormVersion,@NotBlank @Size(min=4,max=500) String reason) { }
    public record PublicationResponse(String requestId,FormConfigurationStatus status,long requestedVersion) { static PublicationResponse from(FormPublicationRequest v){return new PublicationResponse(v.id(),v.status(),v.requestedVersion());} }
    public record Response(String id,String code,String name,String summary,TicketType ticketType,String categoryCode,List<String> applicableOrganizationIds,List<ConfiguredFormField> fields,TagPolicy tagPolicy,FormConfigurationStatus lifecycleStatus,long version,int formVersion,String schemaHash) { static Response from(ManagedFormConfiguration v){return new Response(v.id(),v.code(),v.name(),v.summary(),v.ticketType(),v.categoryCode(),v.applicableOrganizationIds(),v.fields(),v.tagPolicy(),v.status(),v.version(),v.formVersion(),v.schemaHash());} }
    public record PageResponse(List<Response> items,int page,int pageSize,int total) { }
}
