package cn.servicehub.ticketdraft;

import cn.servicehub.ticketdraft.TicketDraftModels.*;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/ticket-drafts")
public class TicketDraftController {
    private final TicketDraftService service; public TicketDraftController(TicketDraftService service){this.service=service;}
    @GetMapping public Page list(@RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize){return service.list(page,pageSize);}
    @GetMapping("/{id}") public Draft get(@PathVariable @Pattern(regexp="^TD-[0-9a-f-]{36}$") String id){return service.get(id);}
    @PutMapping("/{id}") public Draft save(@PathVariable @Pattern(regexp="^TD-[0-9a-f-]{36}$") String id,@RequestHeader("If-Match") @Pattern(regexp="^\"?[0-9]{1,12}\"?$") String version,@RequestBody Input body){return service.save(id,body,Long.parseLong(version.replace("\"","")));}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable @Pattern(regexp="^TD-[0-9a-f-]{36}$") String id,@RequestHeader("If-Match") @Pattern(regexp="^\"?[0-9]{1,12}\"?$") String version){service.delete(id,Long.parseLong(version.replace("\"","")));}
}
