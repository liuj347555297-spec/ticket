package cn.servicehub.knowledge.web;
import cn.servicehub.knowledge.application.KnowledgeService;
import cn.servicehub.knowledge.domain.KnowledgeDocumentVersion;
import cn.servicehub.knowledge.domain.KnowledgeFeedback;
import cn.servicehub.knowledge.domain.KnowledgeReviewCandidate;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @Validated @RequestMapping("/api/v1/knowledge/documents") public class KnowledgeController {
 private final KnowledgeService service; public KnowledgeController(KnowledgeService s){service=s;}
 @PostMapping(value="/imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) KnowledgeDocumentResponse importDocument(@RequestPart("title") String title,@RequestPart("categoryCode") @Pattern(regexp="^[A-Z][A-Z0-9_-]{1,63}$") String category,@RequestParam(value="tags",required=false) List<String> tags,@RequestParam(value="serviceCatalogItemIds",required=false) List<String> serviceCatalogItemIds,@RequestPart("file") MultipartFile file){return KnowledgeDocumentResponse.from(service.importDocument(title,category,tags,serviceCatalogItemIds,file));}
 @GetMapping List<KnowledgeDocumentResponse> list(@RequestParam(value="q",required=false) String q,@RequestParam(value="category",required=false) String category,@RequestParam(value="tag",required=false) String tag){return service.list(q,category,tag).stream().map(KnowledgeDocumentResponse::from).toList();}
 @GetMapping("/{documentId}") KnowledgeDocumentResponse get(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id){return KnowledgeDocumentResponse.from(service.get(id));}
 @GetMapping("/{documentId}/content") KnowledgeContentResponse content(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id){var value=service.preview(id);return new KnowledgeContentResponse(value.versionId(),value.versionNumber(),value.content());}
 @GetMapping("/{documentId}/versions") List<KnowledgeVersionResponse> versions(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id){return service.versions(id).stream().map(KnowledgeVersionResponse::from).toList();}
 @GetMapping("/reviews") List<KnowledgeReviewResponse> reviews(){return service.reviewQueue().stream().map(KnowledgeReviewResponse::from).toList();}
 @GetMapping("/reviews/candidates") List<KnowledgeReviewCandidateResponse> reviewCandidates(){return service.reviewCandidates().stream().map(KnowledgeReviewCandidateResponse::from).toList();}
 @PostMapping("/{documentId}/publish") KnowledgeDocumentResponse publish(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id,@RequestParam @Pattern(regexp="^KVER-[0-9a-fA-F-]{36}$") String versionId){return KnowledgeDocumentResponse.from(service.publish(id,versionId));}
 @PostMapping("/{documentId}/archive") KnowledgeDocumentResponse archive(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id){return KnowledgeDocumentResponse.from(service.archive(id));}
 @PostMapping("/{documentId}/reviews/complete") KnowledgeDocumentResponse completeReview(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id){return KnowledgeDocumentResponse.from(service.completeReview(id));}
 @PostMapping("/{documentId}/feedback") KnowledgeFeedbackResponse feedback(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id,@Valid @RequestBody KnowledgeFeedbackRequest request){var summary=service.feedback(id,request.value(),request.reasonCode());return new KnowledgeFeedbackResponse(summary.helpfulCount(),summary.notHelpfulCount(),summary.total());}
 @GetMapping("/{documentId}/feedback") KnowledgeFeedbackResponse feedbackSummary(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id){var summary=service.feedbackSummary(id);return new KnowledgeFeedbackResponse(summary.helpfulCount(),summary.notHelpfulCount(),summary.total());}
 @PostMapping("/from-tickets/{ticketId}/draft") KnowledgeDocumentResponse fromResolvedTicket(@PathVariable @Pattern(regexp="^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,@Valid @RequestBody KnowledgeDraftFromTicketRequest request){return KnowledgeDocumentResponse.from(service.createDraftFromResolvedTicket(ticketId,request.categoryCode(),request.tags()));}
 @PostMapping(value="/{documentId}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) KnowledgeVersionResponse importVersion(@PathVariable("documentId") @Pattern(regexp="^KDOC-[0-9a-fA-F-]{36}$") String id,@RequestPart("file") MultipartFile file){return KnowledgeVersionResponse.from(service.importVersion(id,file));}

 record KnowledgeContentResponse(String versionId,int versionNumber,String content){}
 record KnowledgeFeedbackRequest(@NotNull KnowledgeFeedback.FeedbackValue value,@Pattern(regexp="^$|^[A-Z][A-Z0-9_]{1,63}$") String reasonCode){}
 record KnowledgeFeedbackResponse(long helpfulCount,long notHelpfulCount,long total){}
 record KnowledgeDraftFromTicketRequest(@Pattern(regexp="^[A-Z][A-Z0-9_-]{1,63}$") String categoryCode,List<String> tags){}
 record KnowledgeReviewCandidateResponse(String documentId,String title,String reasonCode,java.time.Instant reviewDueAt,String reviewOwnerIamUserId,long helpfulCount,long notHelpfulCount){static KnowledgeReviewCandidateResponse from(KnowledgeReviewCandidate value){return new KnowledgeReviewCandidateResponse(value.documentId(),value.title(),value.reasonCode(),value.reviewDueAt(),value.reviewOwnerIamUserId(),value.helpfulCount(),value.notHelpfulCount());}}
 record KnowledgeVersionResponse(String id,int versionNumber,String detectedMediaType,long sizeBytes,String owningOrganizationId,List<String> serviceCatalogItemIds,String status,String reviewerIamUserId,java.time.Instant createdAt,java.time.Instant publishedAt){static KnowledgeVersionResponse from(KnowledgeDocumentVersion v){return new KnowledgeVersionResponse(v.id(),v.versionNumber(),v.detectedMediaType(),v.sizeBytes(),v.owningOrganizationId(),v.serviceCatalogItemIds(),v.status().name(),v.reviewerIamUserId(),v.createdAt(),v.publishedAt());}}
 record KnowledgeReviewResponse(String importTaskId,String documentId,String title,String categoryCode,String taskStatus,String errorCode,java.time.Instant submittedAt,List<KnowledgeVersionResponse> pendingVersions){static KnowledgeReviewResponse from(KnowledgeService.KnowledgeReviewItem item){return new KnowledgeReviewResponse(item.task().id(),item.document().id(),item.document().title(),item.document().categoryCode(),item.task().status(),item.task().errorCode(),item.task().createdAt(),item.pendingVersions().stream().map(KnowledgeVersionResponse::from).toList());}}
}
