package cn.servicehub.sla.web;

import cn.servicehub.sla.application.SlaService;
import cn.servicehub.sla.domain.TicketSlaTarget;
import cn.servicehub.ticket.application.TicketService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reading a target first resolves the ticket through object-level authorization. */
@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/sla")
public class TicketSlaController {
    private final TicketService tickets; private final SlaService sla;
    public TicketSlaController(TicketService tickets, SlaService sla) { this.tickets = tickets; this.sla = sla; }
    @GetMapping
    TicketSlaTarget get(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId) { tickets.get(ticketId); return sla.get(ticketId); }
}
