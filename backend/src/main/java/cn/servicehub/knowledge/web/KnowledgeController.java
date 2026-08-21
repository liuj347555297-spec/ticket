package cn.servicehub.knowledge.web;
import cn.servicehub.knowledge.application.KnowledgeService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @Validated @RequestMapping("/api/v1/knowledge/documents") public class KnowledgeController {
 private final KnowledgeService service; public KnowledgeController(KnowledgeService s){service=s;}
 @PostMapping(value="/imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) KnowledgeDocumentResponse importDocument(@RequestPart("title") String title,@RequestPart("categoryCode") @Pattern(regexp="^[A-Z][A-Z0-9_-]{1,63}$") String category,@RequestPart(value="tags",required=false) List<String> tags,@RequestPart("file") MultipartFile file){return KnowledgeDocumentResponse.from(service.importDocument(title,category,tags,file));}
 @GetMapping List<KnowledgeDocumentResponse> list(){return service.list().stream().map(KnowledgeDocumentResponse::from).toList();}
 @GetMapping("/{documentId}") KnowledgeDocumentResponse get(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id){return KnowledgeDocumentResponse.from(service.get(id));}
 @PostMapping("/{documentId}/publish") KnowledgeDocumentResponse publish(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id,@RequestParam @Pattern(regexp="^KVER-[0-9a-fA-F-]{36}$") String versionId){return KnowledgeDocumentResponse.from(service.publish(id,versionId));}
}
