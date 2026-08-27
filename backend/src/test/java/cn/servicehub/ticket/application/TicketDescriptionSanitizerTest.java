package cn.servicehub.ticket.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.servicehub.ticket.domain.TicketDescriptionFormat;
import org.junit.jupiter.api.Test;

class TicketDescriptionSanitizerTest {
    private final TicketDescriptionSanitizer sanitizer = new TicketDescriptionSanitizer();

    @Test
    void richTextKeepsOnlyTheApprovedFormattingAndDerivesPlainText() {
        TicketDescription description = sanitizer.sanitize("<p>页面<strong>卡顿</strong></p><ul><li>Chrome</li></ul><a href=\"https://kb.intra.example/a\" onclick=\"alert(1)\">查看案例</a>", TicketDescriptionFormat.RICH_TEXT);

        assertEquals(TicketDescriptionFormat.RICH_TEXT, description.format());
        assertTrue(description.sanitizedHtml().contains("<strong>卡顿</strong>"));
        assertTrue(description.sanitizedHtml().contains("https://kb.intra.example/a"));
        assertFalse(description.sanitizedHtml().contains("onclick"));
        assertTrue(description.plainText().contains("页面卡顿"));
    }

    @Test
    void richTextRejectsExecutableOrEmbeddedContent() {
        assertThrows(IllegalArgumentException.class,
            () -> sanitizer.sanitize("<p>正常文字</p><script>alert(1)</script>", TicketDescriptionFormat.RICH_TEXT));
        assertThrows(IllegalArgumentException.class,
            () -> sanitizer.sanitize("<iframe src=\"https://outside.example\"></iframe>", TicketDescriptionFormat.RICH_TEXT));
    }

    @Test
    void plainTextRemainsBackwardCompatible() {
        TicketDescription description = sanitizer.sanitize("  页面  响应缓慢  ", null);

        assertEquals(TicketDescriptionFormat.PLAIN_TEXT, description.format());
        assertEquals("页面 响应缓慢", description.plainText());
        assertEquals(null, description.sanitizedHtml());
    }
}
