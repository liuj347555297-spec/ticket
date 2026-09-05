package cn.servicehub.localauth.web;

import cn.servicehub.localauth.application.LocalAccountAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController @Validated
@RequestMapping("/api/v1/admin/local-accounts")
public class LocalAccountAdminController {
    private static final String ID="^[A-Za-z0-9._:-]{1,128}$";private static final String KEY="^[A-Za-z0-9-]{8,64}$";
    private final LocalAccountAdminService service;public LocalAccountAdminController(LocalAccountAdminService service){this.service=service;}
    @GetMapping public LocalAccountAdminService.AccountPage list(@RequestParam(defaultValue="1") @Min(1) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize,@RequestParam(required=false) @Size(max=128) String q,@RequestParam(required=false) @Size(max=16) String status){return service.list(page,pageSize,q,status);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public LocalAccountAdminService.AccountView create(@RequestHeader("Idempotency-Key") @Pattern(regexp=KEY) String ignored,@Valid @RequestBody CreateRequest request){return service.create(new LocalAccountAdminService.CreateCommand(request.loginName(),request.displayName(),request.organizationId(),request.password(),request.roles(),request.systemCodes(),request.reason()));}
    @PutMapping("/{id}") public LocalAccountAdminService.AccountView update(@PathVariable @Pattern(regexp=ID) String id,@RequestHeader("If-Match") @Size(max=32) String ifMatch,@RequestHeader("Idempotency-Key") @Pattern(regexp=KEY) String ignored,@Valid @RequestBody UpdateRequest request){requireMatchingVersion(ifMatch,request.version());return service.update(id,new LocalAccountAdminService.UpdateCommand(request.version(),request.displayName(),request.organizationId(),request.enabled(),request.roles(),request.systemCodes(),request.reason()));}
    @PostMapping("/{id}/password-reset") public LocalAccountAdminService.AccountView reset(@PathVariable @Pattern(regexp=ID) String id,@RequestHeader("If-Match") @Size(max=32) String ifMatch,@RequestHeader("Idempotency-Key") @Pattern(regexp=KEY) String ignored,@Valid @RequestBody PasswordResetRequest request){requireMatchingVersion(ifMatch,request.version());return service.resetPassword(id,request.version(),request.password(),request.reason());}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable @Pattern(regexp=ID) String id,@RequestHeader("If-Match") @Size(max=32) String ifMatch,@RequestHeader("Idempotency-Key") @Pattern(regexp=KEY) String ignored,@Valid @RequestBody DeleteRequest request){requireMatchingVersion(ifMatch,request.version());service.disable(id,request.version(),request.reason());}
    private long version(String value){try{String clean=value.strip();if(clean.startsWith("W/"))clean=clean.substring(2);clean=clean.replace("\"","");long parsed=Long.parseLong(clean);if(parsed<1)throw new IllegalArgumentException();return parsed;}catch(Exception exception){throw new IllegalArgumentException();}}
    private void requireMatchingVersion(String value,long bodyVersion){if(version(value)!=bodyVersion)throw new cn.servicehub.localauth.application.LocalAccountConflictException();}
    public record CreateRequest(@NotBlank @Size(min=3,max=128) String loginName,@NotBlank @Size(max=100) String displayName,@NotBlank @Pattern(regexp=ID) String organizationId,@NotBlank @Size(min=12,max=128) String password,@NotNull Set<String> roles,@NotNull Set<String> systemCodes,@NotBlank @Size(min=4,max=500) String reason){}
    public record UpdateRequest(@Min(1) long version,@NotBlank @Size(max=100) String displayName,@NotBlank @Pattern(regexp=ID) String organizationId,boolean enabled,@NotNull Set<String> roles,@NotNull Set<String> systemCodes,@NotBlank @Size(min=4,max=500) String reason){}
    public record PasswordResetRequest(@Min(1) long version,@NotBlank @Size(min=12,max=128) String password,@NotBlank @Size(min=4,max=500) String reason){}
    public record DeleteRequest(@Min(1) long version,@NotBlank @Size(min=4,max=500) String reason){}
}
