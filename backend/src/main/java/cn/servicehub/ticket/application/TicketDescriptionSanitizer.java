package cn.servicehub.ticket.application;

import cn.servicehub.ticket.domain.TicketDescriptionFormat;
import cn.servicehub.attachment.domain.AttachmentRepository;
import cn.servicehub.attachment.domain.AttachmentScanStatus;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The browser is never a trust boundary. This deliberately small policy matches the first
 * TipTap toolbar and excludes images, styles, embeds, event attributes and all non-HTTP links.
 */
@Component
public class TicketDescriptionSanitizer {
    private static final int MAX_PLAIN_TEXT_LENGTH = 4_000;
    private static final Pattern WHITESPACE = Pattern.compile("[\\t\\r\\n ]+");
    private static final Set<String> FORBIDDEN_ELEMENTS = Set.of("script", "style", "iframe", "object", "embed", "svg", "math", "video", "audio", "form", "input");
    private static final Pattern INLINE_IMAGE_URL = Pattern.compile("^/api/v1/tickets/(TKT-[0-9]{8}-[0-9]{6})/attachments/(ATT-[0-9a-fA-F-]{36})/inline$");
    private static final Set<String> FONT_SIZES = Set.of("12", "14", "16", "18");
    private static final PolicyFactory RICH_TEXT_POLICY = new HtmlPolicyBuilder()
        .allowElements("p", "br", "strong", "em", "span", "ul", "ol", "li", "code", "pre", "h3", "h4")
        .allowElements("a")
        .allowAttributes("href").onElements("a")
        .allowElements("img")
        .allowAttributes("src", "alt").onElements("img")
        .allowAttributes("data-font-size").onElements("span")
        .allowUrlProtocols("http", "https")
        .requireRelNofollowOnLinks()
        .toFactory();
    private final AttachmentRepository attachments;

    public TicketDescriptionSanitizer() {
        this.attachments = null;
    }

    @Autowired
    public TicketDescriptionSanitizer(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    public TicketDescription sanitize(String rawDescription, TicketDescriptionFormat requestedFormat) {
        return sanitize(rawDescription, requestedFormat, null);
    }

    /** Inline images are allowed only after the ticket exists and its image attachment is scan-clean. */
    public TicketDescription sanitize(String rawDescription, TicketDescriptionFormat requestedFormat, String ticketId) {
        TicketDescriptionFormat format = requestedFormat == null ? TicketDescriptionFormat.PLAIN_TEXT : requestedFormat;
        if (format == TicketDescriptionFormat.PLAIN_TEXT) {
            String plainText = normalize(rawDescription);
            requireContent(plainText);
            return new TicketDescription(format, plainText, null);
        }

        var document = Jsoup.parseBodyFragment(rawDescription == null ? "" : rawDescription);
        if (document.getAllElements().stream().anyMatch(element -> FORBIDDEN_ELEMENTS.contains(element.normalName()))) {
            throw new IllegalArgumentException("Rich-text content contains a forbidden element");
        }
        if (document.select("span[data-font-size]").stream().anyMatch(element -> !FONT_SIZES.contains(element.attr("data-font-size")))) {
            throw new IllegalArgumentException("Rich-text content contains an unsupported font size");
        }
        document.select("img").forEach(image -> validateInlineImage(image.attr("src"), ticketId));
        String sanitizedHtml = RICH_TEXT_POLICY.sanitize(rawDescription);
        String plainText = normalize(Jsoup.parseBodyFragment(sanitizedHtml).text());
        requireContent(plainText);
        if (sanitizedHtml.length() > 12_000) {
            throw new IllegalArgumentException("Rich-text content is too large after sanitization");
        }
        return new TicketDescription(TicketDescriptionFormat.RICH_TEXT, plainText, sanitizedHtml);
    }

    private void validateInlineImage(String source, String ticketId) {
        if (ticketId == null) throw new IllegalArgumentException("Inline images require an existing ticket");
        var matcher = INLINE_IMAGE_URL.matcher(source);
        if (!matcher.matches() || !ticketId.equals(matcher.group(1))) throw new IllegalArgumentException("Inline image source is invalid");
        if (attachments == null) throw new IllegalArgumentException("Inline image validation is unavailable");
        var attachment = attachments.findByIdAndTicketId(matcher.group(2), ticketId).orElseThrow(() -> new IllegalArgumentException("Inline image is unavailable"));
        if (attachment.scanStatus() != AttachmentScanStatus.CLEAN || !attachment.detectedMediaType().startsWith("image/")) {
            throw new IllegalArgumentException("Inline image is not scan-clean");
        }
    }

    private static String normalize(String value) {
        return WHITESPACE.matcher(value == null ? "" : value.trim()).replaceAll(" ");
    }

    private static void requireContent(String plainText) {
        if (plainText.isBlank() || plainText.length() > MAX_PLAIN_TEXT_LENGTH) {
            throw new IllegalArgumentException("Ticket description is missing or too large");
        }
    }
}
