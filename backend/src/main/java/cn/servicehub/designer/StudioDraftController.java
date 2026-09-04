package cn.servicehub.designer;

import cn.servicehub.designer.StudioModels.*;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/design-studio/drafts")
public class StudioDraftController {
    private final StudioDraftService service;
    public StudioDraftController(StudioDraftService service){this.service=service;}
    @GetMapping public ListResponse list(){return new ListResponse(service.list());}
    @GetMapping("/{id}") public Draft get(@PathVariable @Pattern(regexp="^DS-[0-9a-f-]{36}$") String id){return service.get(id);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Draft create(@RequestHeader("Idempotency-Key") @Pattern(regexp="^[0-9a-fA-F-]{8,64}$") String key,@RequestBody Input body){return service.create(body,key);}
    @PutMapping("/{id}") public Draft update(@PathVariable @Pattern(regexp="^DS-[0-9a-f-]{36}$") String id,@RequestHeader("If-Match") @Pattern(regexp="^\"?[0-9]{1,18}\"?$") String version,@RequestBody Input body){return service.update(id,body,Long.parseLong(version.replace("\"","")));}
    public record ListResponse(List<Summary> items) { }
}
