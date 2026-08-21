package cn.servicehub.attachment.web;

import cn.servicehub.attachment.application.AttachmentService;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController @Validated @RequestMapping("/api/v1/tickets/{ticketId}/attachments")
public class AttachmentController {
    private final AttachmentService service; public AttachmentController(AttachmentService service) { this.service=service; }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) AttachmentResponse upload(@PathVariable @Pattern(regexp="^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,@RequestPart("file") MultipartFile file) { return AttachmentResponse.from(service.upload(ticketId,file)); }
    @GetMapping List<AttachmentResponse> list(@PathVariable @Pattern(regexp="^TKT-[0-9]{8}-[0-9]{6}$") String ticketId) { return service.list(ticketId).stream().map(AttachmentResponse::from).toList(); }
    @GetMapping("/{attachmentId}/download") ResponseEntity<InputStreamResource> download(@PathVariable @Pattern(regexp="^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,@PathVariable @Pattern(regexp="^ATT-[0-9a-fA-F-]{36}$") String attachmentId) { var result=service.download(ticketId,attachmentId); return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(result.attachment().sizeBytes()).header(HttpHeaders.CONTENT_DISPOSITION,org.springframework.http.ContentDisposition.attachment().filename(result.attachment().originalFilename(),StandardCharsets.UTF_8).build().toString()).header(HttpHeaders.CACHE_CONTROL,"no-store, private").header("X-Content-Type-Options","nosniff").body(new InputStreamResource(result.content())); }
}
