package cn.servicehub.ticket.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MySqlTicketRepositoryTest {
    @Test
    void likeMetacharactersAreEscapedAsLiterals() {
        assertEquals("50!%!!off!_today", MySqlTicketRepository.escapeLikeLiteral("50%!off_today"));
    }
}
