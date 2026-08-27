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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AttachmentAndKnowledgeControllerTest {
 @Autowired MockMvc mvc;
 @Test void attachmentIsServerDetectedAndDownloadIsObjectAuthorized() throws Exception {
  String id=createTicket(); MockMultipartFile file=new MockMultipartFile("file","../../report.txt","application/pdf","safe report".getBytes());
  String response=mvc.perform(multipart("/api/v1/tickets/{id}/attachments",id).file(file).with(user("iam-u-1001").roles("REQUESTER")).with(csrf()))
   .andExpect(status().isOk()).andExpect(jsonPath("$.filename",is("report.txt"))).andExpect(jsonPath("$.detectedMediaType",is("text/plain"))).andExpect(jsonPath("$.scanStatus",is("CLEAN"))).andReturn().getResponse().getContentAsString();
  String attachmentId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/download",id,attachmentId).with(user("iam-u-1002").roles("REQUESTER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/download",id,attachmentId).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control","no-store, private"));
  MockMultipartFile eicar=new MockMultipartFile("file","check.txt","text/plain","EICAR-STANDARD-ANTIVIRUS-TEST-FILE".getBytes());
  String blocked=mvc.perform(multipart("/api/v1/tickets/{id}/attachments",id).file(eicar).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.scanStatus",is("REJECTED"))).andReturn().getResponse().getContentAsString();
  String blockedId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(blocked).get("id").asText();
  mvc.perform(get("/api/v1/tickets/{id}/attachments/{attachment}/download",id,blockedId).with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isBadRequest());
 }
 @Test void knowledgeImportNeedsControlledAdministratorAndReviewBeforePublication() throws Exception {
  MockMultipartFile title=new MockMultipartFile("title","","text/plain","浏览器卡顿手册".getBytes()); MockMultipartFile category=new MockMultipartFile("categoryCode","","text/plain","BROWSER".getBytes()); MockMultipartFile file=new MockMultipartFile("file","guide.pdf","application/pdf","%PDF-1.7 sample".getBytes());
  mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).with(user("iam-u-1001").roles("REQUESTER")).with(csrf())).andExpect(status().isForbidden());
  String created=mvc.perform(multipart("/api/v1/knowledge/documents/imports").file(title).file(category).file(file).with(user("iam-admin").roles("SERVICE_MANAGER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PENDING_REVIEW"))).andReturn().getResponse().getContentAsString();
  com.fasterxml.jackson.databind.JsonNode parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created);
  mvc.perform(post("/api/v1/knowledge/documents/{id}/publish",parsed.get("id").asText()).param("versionId",parsed.get("currentVersionId").asText()).with(user("iam-admin").roles("SERVICE_MANAGER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("PUBLISHED")));
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
 private String createTicket() throws Exception { String body=mvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("Idempotency-Key","c4d3c2b1-1234-4abc-8def-123456789012").contentType(MediaType.APPLICATION_JSON).content("{\"serviceCatalogItemId\":\"SC-browser-performance\",\"serviceCatalogFormVersion\":1,\"type\":\"INCIDENT\",\"title\":\"页面卡顿\",\"description\":\"缓慢\",\"structuredFields\":{\"browser\":\"Chrome\"},\"tags\":[]}" )).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(); return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText(); }
}
