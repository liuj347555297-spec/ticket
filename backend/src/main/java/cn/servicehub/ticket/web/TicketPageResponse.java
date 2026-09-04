package cn.servicehub.ticket.web;

import cn.servicehub.ticket.domain.TicketPage;
import java.time.Instant;
import java.util.List;

public record TicketPageResponse(List<TicketResponse> items, int page, int pageSize, long total,
                                 String nextCursor, boolean hasMore, Instant snapshotAt) {
    public static TicketPageResponse from(TicketPage page) {
        return new TicketPageResponse(page.items().stream().map(TicketResponse::from).toList(), page.page(), page.pageSize(), page.total(),
            page.nextCursor(), page.hasMore(), page.snapshotAt());
    }
}
