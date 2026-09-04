package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import cn.servicehub.ticket.domain.TicketRepository;
import cn.servicehub.ticket.domain.TicketStatus;

@SpringBootTest @AutoConfigureMockMvc @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AttachmentAndKnowledgeControllerTest {
 @Autowired MockMvc mvc;
 @Autowired TicketRepository ticketRepository;
 @Test void attachmentIsServerDetectedAndDownloadIsObjectAuthorized() throws Exception {
  String id=createTicket(); MockMultipartFile file=new MockMultipartFile("file","../../report.txt","application/pdf","safe report".getBytes());
  String response=mvc.perform(multipart("/api/v1/tickets/{id}/attachments",id).file(file).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.filename",is("report.txt"))).andExpect(jsonPath("$.detectedMediaType",is("text/plain"))).andExpect(jsonPath("$.scanStatus",is("CLEAN"))).andReturn().getResponse().getContentAsString();
  String attachmentId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/download",id,attachmentId).with(user("iam-u-1002").roles("REQUESTER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/download",id,attachmentId).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control","no-store, private"));
  MockMultipartFile eicar=new MockMultipartFile("file","check.txt","text/plain","EICAR-STANDARD-ANTIVIRUS-TEST-FILE".getBytes());
  String blocked=mvc.perform(multipart("/api/v1/tickets/{id}/attachments",id).file(eicar).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.scanStatus",is("REJECTED"))).andReturn().getResponse().getContentAsString();
  String blockedId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(blocked).get("id").asText();
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/download",id,blockedId).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isBadRequest());
 }
 @Test void knowledgeImportNeedsControlledAdministratorAndReviewBeforePublication() throws Exception {
  MockMultipartFile title=new MockMultipartFile("title","","text/plain","浏览器卡顿手册".getBytes()); MockMultipartFile category=new MockMultipartFile("categoryCode","","text/plain","BROWSER".getBytes()); MockMultipartFile file=new MockMultipartFile("file","guide.pdf","application/pdf","%PDF-1.7 sample".getBytes());
  mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).param("serviceCatalogItemIds","SC-browser-performance").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isForbidden());
  String created=mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).param("serviceCatalogItemIds","SC-browser-performance").with(manager()).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PENDING_REVIEW"))).andExpect(jsonPath("$.owningOrganizationId",is("org-it"))).andExpect(jsonPath("$.serviceCatalogItemIds[0]",is("SC-browser-performance"))).andReturn().getResponse().getContentAsString();
  com.fasterxml.jackson.databind.JsonNode parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created);
  mvc.perform(post("/api/v1/knowledge/documents/{id}/publish",parsed.get("id").asText()).param("versionId",parsed.get("currentVersionId").asText()).with(manager()).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PUBLISHED")));
 }
 @Test void publishedTextKnowledgeIsReadableButPendingAndArchivedSourcesAreNotExposed() throws Exception {
  MockMultipartFile title=new MockMultipartFile("title","","text/plain","浏览器卡顿自查".getBytes()); MockMultipartFile category=new MockMultipartFile("categoryCode","","text/plain","BROWSER".getBytes()); MockMultipartFile file=new MockMultipartFile("file","guide.txt","text/plain","先清理浏览器缓存，再重新登录。".getBytes());
  String created=mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).param("tags","#页面卡顿").param("serviceCatalogItemIds","SC-browser-performance").with(manager()).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  com.fasterxml.jackson.databind.JsonNode parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created); String id=parsed.get("id").asText();
  mvc.perform(get("/api/v1/knowledge/documents/{id}",id).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/knowledge/documents/{id}/publish",id).param("versionId",parsed.get("currentVersionId").asText()).with(manager()).with(csrf())).andExpect(status().isOk());
  mvc.perform(get("/api/v1/knowledge/documents/{id}/content",id).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(jsonPath("$.content",is("先清理浏览器缓存，再重新登录。")));
  mvc.perform(get("/api/v1/knowledge/documents/reviews").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/knowledge/documents/{id}/archive",id).with(manager()).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("ARCHIVED")));
  mvc.perform(get("/api/v1/knowledge/documents/{id}/content",id).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isNotFound());
 }
 @Test void knowledgeFeedbackIsStructuredUpsertedAndReviewCandidatesStayManagerOnly() throws Exception {
  MockMultipartFile title=new MockMultipartFile("title","","text/plain","VPN 自查".getBytes()); MockMultipartFile category=new MockMultipartFile("categoryCode","","text/plain","NETWORK".getBytes()); MockMultipartFile file=new MockMultipartFile("file","guide.txt","text/plain","受控步骤。".getBytes());
  String created=mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).param("serviceCatalogItemIds","SC-browser-performance").with(manager()).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  com.fasterxml.jackson.databind.JsonNode parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created); String id=parsed.get("id").asText();
  mvc.perform(post("/api/v1/knowledge/documents/{id}/publish",id).param("versionId",parsed.get("currentVersionId").asText()).with(manager()).with(csrf())).andExpect(status().isOk());
  mvc.perform(post("/api/v1/knowledge/documents/{id}/feedback",id).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"HELPFUL\"}").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.helpfulCount",is(1))).andExpect(jsonPath("$.total",is(1)));
  mvc.perform(post("/api/v1/knowledge/documents/{id}/feedback",id).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"NOT_HELPFUL\",\"reasonCode\":\"OUTDATED\"}").with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.helpfulCount",is(0))).andExpect(jsonPath("$.notHelpfulCount",is(1))).andExpect(jsonPath("$.total",is(1)));
  mvc.perform(get("/api/v1/knowledge/documents/reviews/candidates").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isForbidden());
 }
 @Test void ticketToKnowledgeDraftRequiresManagerAndResolvedTicket() throws Exception {
  String ticketId=createTicket(); String payload="{\"categoryCode\":\"BROWSER\",\"tags\":[\"#页面卡顿\"]}";
  mvc.perform(post("/api/v1/knowledge/documents/from-tickets/{id}/draft",ticketId).contentType(MediaType.APPLICATION_JSON).content(payload).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/knowledge/documents/from-tickets/{id}/draft",ticketId).contentType(MediaType.APPLICATION_JSON).content(payload).with(manager()).with(csrf())).andExpect(status().isBadRequest());
  org.junit.jupiter.api.Assertions.assertTrue(ticketRepository.updateStatus(ticketId,0,TicketStatus.RESOLVED,java.time.Instant.now()));
  mvc.perform(post("/api/v1/knowledge/documents/from-tickets/{id}/draft",ticketId).contentType(MediaType.APPLICATION_JSON).content(payload).with(manager()).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.owningOrganizationId",is("org-it"))).andExpect(jsonPath("$.serviceCatalogItemIds[0]",is("SC-browser-performance")));
 }
 @Test void knowledgeScopeIsRequiredAndOtherOrganizationCannotEnumeratePublishedDocument() throws Exception {
  MockMultipartFile title=new MockMultipartFile("title","","text/plain","范围知识".getBytes()); MockMultipartFile category=new MockMultipartFile("categoryCode","","text/plain","BROWSER".getBytes()); MockMultipartFile file=new MockMultipartFile("file","guide.txt","text/plain","仅本组织可见。".getBytes());
  mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).param("serviceCatalogItemIds","SC-browser-performance").with(user("iam-u-1001").roles("SERVICE_MANAGER")).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).with(manager()).with(csrf())).andExpect(status().isBadRequest());
  String created=mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).param("serviceCatalogItemIds","SC-browser-performance").with(manager()).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  var parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created);String id=parsed.get("id").asText();
  mvc.perform(post("/api/v1/knowledge/documents/{id}/publish",id).param("versionId",parsed.get("currentVersionId").asText()).with(manager()).with(csrf())).andExpect(status().isOk());
  mvc.perform(get("/api/v1/knowledge/documents/{id}/versions",id).with(auditor())).andExpect(status().isOk()).andExpect(jsonPath("$[0].owningOrganizationId",is("org-it")));
  mvc.perform(get("/api/v1/knowledge/documents/{id}/versions",id).with(user("iam-u-1001").roles("AUDITOR"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/knowledge/documents/{id}",id).with(user("iam-u-1002").roles("REQUESTER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/knowledge/documents/{id}/feedback",id).with(user("iam-u-1002").roles("REQUESTER"))).andExpect(status().isNotFound());
 }
 @Test void scanCleanTicketImageCanBeReferencedInlineButOnlyThroughItsAuthorizedRoute() throws Exception {
  String id=createTicket(); MockMultipartFile image=new MockMultipartFile("file","screen.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0});
  String response=mvc.perform(multipart("/api/v1/tickets/{id}/attachments",id).file(image).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.scanStatus",is("CLEAN"))).andReturn().getResponse().getContentAsString();
  String attachmentId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
  String body="{\"description\":\"<p>复现截图</p><p><img src=\\\"/api/v1/tickets/"+id+"/attachments/"+attachmentId+"/inline\\\" alt=\\\"screen\\\"></p>\",\"descriptionFormat\":\"RICH_TEXT\"}";
  mvc.perform(patch("/api/v1/tickets/{id}/description",id).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.descriptionFormat",is("RICH_TEXT"))).andExpect(jsonPath("$.descriptionHtml",org.hamcrest.Matchers.containsString("/inline")));
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/inline",id,attachmentId).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Type","image/png"));
 }
 @Test void requesterCannotUploadListOrRenderAnotherUsersAttachmentAndAuditorCannotDownloadIt() throws Exception {
  String id=createTicket(); MockMultipartFile image=new MockMultipartFile("file","screen.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,0});
  String response=mvc.perform(multipart("/api/v1/tickets/{id}/attachments",id).file(image).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  String attachmentId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
  mvc.perform(get("/api/v1/tickets/{id}/attachments",id).with(user("iam-u-1002").roles("REQUESTER"))).andExpect(status().isNotFound());
  mvc.perform(multipart("/api/v1/tickets/{id}/attachments",id).file(image).with(user("iam-u-1002").roles("REQUESTER")).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/inline",id,attachmentId).with(user("iam-u-1002").roles("REQUESTER"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/download",id,attachmentId).with(user("iam-u-audit").roles("AUDITOR"))).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/tickets/{id}",id).with(user("iam-u-audit").roles("AUDITOR"))).andExpect(status().isOk());
 }
 private String createTicket() throws Exception { String body=mvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("Idempotency-Key","c4d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("{\"serviceCatalogItemId\":\"SC-browser-performance\",\"serviceCatalogFormVersion\":1,\"type\":\"INCIDENT\",\"title\":\"页面卡顿\",\"description\":\"缓慢\",\"structuredFields\":{\"browser\":\"Chrome\"},\"tags\":[]}" )).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText(); }
 private RequestPostProcessor manager(){return user("iam-u-1001").authorities(new SimpleGrantedAuthority("ROLE_SERVICE_MANAGER"),new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"));}
 private RequestPostProcessor auditor(){return user("iam-u-1001").authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"),new SimpleGrantedAuthority("DATA_SCOPE_ORGANIZATION:org-it"));}
}
