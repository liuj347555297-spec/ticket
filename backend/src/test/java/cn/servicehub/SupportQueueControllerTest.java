package cn.servicehub;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.servicehub.access.domain.BackofficeAccess;
import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.access.domain.BackofficeDataScope;
import cn.servicehub.security.VerifiedIamAuthenticationFactory;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.team.SupportQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties="servicehub.workflow.direct-accept-routing=true")
@AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SupportQueueControllerTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired BackofficeAccessRepository access;
    @Autowired VerifiedIamAuthenticationFactory verified;@Autowired TicketWorkflowRepository workflows;@Autowired SupportQueueRepository queues;
    @Autowired cn.servicehub.workflow.team.SupportQueueCommandRepository commands;
    @BeforeEach void scopeHandlers(){scope("iam-u-local-first-line");scope("iam-u-local-service-manager");}
    @Test void activeSharedQueueFiltersListCapturesSnapshotAndClaimsOnce() throws Exception {
        createAndActivateQueue();
        mvc.perform(put("/api/v1/admin/service-catalog/items/SC-browser-performance/workflow-node-policies/accept")
                .with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match","0")
                .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SHARED_QUEUE\",\"queueCode\":\"QUEUE_FIN\",\"candidateRoles\":[\"ROLE_FIRST_LINE_SUPPORT\"],\"enabled\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.queueCode",is("QUEUE_FIN")));
        String created=mvc.perform(post("/api/v1/tickets").with(user("iam-u-1002").roles("REQUESTER")).with(csrf())
                .header("Idempotency-Key","a1111111-1111-4111-8111-111111111111").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceCatalogItemId\":\"SC-browser-performance\",\"serviceCatalogFormVersion\":1,\"type\":\"INCIDENT\",\"title\":\"财务共享队列\",\"description\":\"页面缓慢\",\"structuredFields\":{\"browser\":\"Chrome\"},\"tags\":[]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status",is("PENDING_ACCEPTANCE"))).andReturn().getResponse().getContentAsString();
        String ticketId=json.readTree(created).required("id").asText();
        var first=verified.create("iam-u-local-first-line","TEST");
        mvc.perform(get("/api/v1/tickets").param("teamQueueCode","QUEUE_FIN").with(authentication(first)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total",is(1))).andExpect(jsonPath("$.items[0].id",is(ticketId)));
        mvc.perform(get("/api/v1/tickets").param("teamQueueCode","QUEUE_UNKNOWN").with(authentication(first))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tickets").param("queue","MY_TODO").param("teamQueueCode","QUEUE_FIN").with(authentication(first))).andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals("QUEUE_FIN",workflows.findOpenTask(ticketId,"accept").orElseThrow().queueCode());
        org.junit.jupiter.api.Assertions.assertEquals(1,queues.findRoutingSnapshots(ticketId).size());
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions",ticketId).with(authentication(first)).with(csrf()).header("If-Match","\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CLAIM\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions",ticketId).with(authentication(verified.create("iam-u-local-service-manager","TEST"))).with(csrf()).header("If-Match","\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CLAIM\"}"))
            .andExpect(status().isConflict());
    }
    @Test void adminCrudUsesVersionsAndActivationRequiresGovernedMembers() throws Exception {createAndActivateQueue();mvc.perform(get("/api/v1/admin/support-queues/QUEUE_FIN").with(user("admin").roles("PLATFORM_ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("ACTIVE"))).andExpect(jsonPath("$.version",is(2)));String key="d1111111-1111-4111-8111-111111111111",body="{\"expectedVersion\":2,\"reason\":\"队列维护停用\"}";mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/deactivate").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.queue.status",is("INACTIVE")));mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/deactivate").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.queue.status",is("INACTIVE")));mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/deactivate").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body.replace("维护停用","异常复用"))).andExpect(status().isConflict());}
    @Test void legacyReconciliationRequiresIndependentApprovalAndDoesNotExecuteBusiness() throws Exception{createAndActivateQueue();Instant now=Instant.now();String key="f1111111-1111-4111-8111-111111111111";commands.reserve(new cn.servicehub.workflow.team.SupportQueueCommandRecord("legacy-user",key,"DEACTIVATE","support-queue","QUEUE_FIN","legacy-sha","RECONCILIATION_REQUIRED",null,null,"LEGACY_RESULT_UNPROVEN",now,null,now.plusSeconds(300),0,null,null,1,"LEGACY_UNVERIFIABLE",null,null,null));String request=mvc.perform(post("/api/v1/admin/support-queues/commands/legacy-user/{key}/reconciliation-requests",key).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"commandVersion\":0,\"decision\":\"LEGACY_RESULT_ONLY\",\"resultResourceType\":\"support-queue\",\"resultResourceId\":\"QUEUE_FIN\",\"reason\":\"人工确认历史结果引用\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();String id=json.readTree(request).required("id").asText();mvc.perform(post("/api/v1/admin/support-queues/reconciliation-requests/{id}/approve",id).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"reason\":\"申请人不能自批\"}")).andExpect(status().isForbidden());mvc.perform(post("/api/v1/admin/support-queues/reconciliation-requests/{id}/approve",id).with(user("reviewer").roles("PLATFORM_ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"reason\":\"独立复核历史结果\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("APPROVED")));var command=commands.find("legacy-user",key).orElseThrow();org.junit.jupiter.api.Assertions.assertEquals("LEGACY_RESULT_ONLY",command.status());org.junit.jupiter.api.Assertions.assertEquals("QUEUE_FIN",command.resultResourceId());mvc.perform(get("/api/v1/admin/support-queues/QUEUE_FIN").with(user("reviewer").roles("PLATFORM_ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("ACTIVE")));}
    @Test void idempotentMigrationPlanSeparatesApprovalAndMigratesOnlyUnclaimedTask() throws Exception {
        createQueue("QUEUE_FIN","财务源队列","b2111111-1111-4111-8111-111111111111");
        createQueue("QUEUE_TARGET","财务目标队列","b3111111-1111-4111-8111-111111111111");
        mvc.perform(put("/api/v1/admin/service-catalog/items/SC-browser-performance/workflow-node-policies/accept").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match","0").contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SHARED_QUEUE\",\"queueCode\":\"QUEUE_FIN\",\"candidateRoles\":[\"ROLE_FIRST_LINE_SUPPORT\"],\"enabled\":true}")).andExpect(status().isOk());
        String open=createTicket("a2111111-1111-4111-8111-111111111111","迁移未领取");
        String claimed=createTicket("a3111111-1111-4111-8111-111111111111","迁移在办");
        var handler=verified.create("iam-u-local-first-line","TEST");
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions",claimed).with(authentication(handler)).with(csrf()).header("If-Match","\"1\"").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CLAIM\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/deactivate").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key","d2111111-1111-4111-8111-111111111111").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2,\"reason\":\"存在活动任务时禁止直停\",\"replacementQueueCode\":\"QUEUE_TARGET\"}")).andExpect(status().isConflict());
        String request="{\"sourceQueueExpectedVersion\":2,\"targetQueueCode\":\"QUEUE_TARGET\",\"targetQueueExpectedVersion\":2,\"reason\":\"停用源队列并迁移任务\"}";
        String key="e2111111-1111-4111-8111-111111111111";
        String created=mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/migration-plans").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andExpect(jsonPath("$.status",is("PENDING_APPROVAL"))).andExpect(jsonPath("$.unclaimedItemCount",is(1))).andExpect(jsonPath("$.inProgressHandoverCount",is(1))).andReturn().getResponse().getContentAsString();
        String planId=json.readTree(created).required("id").asText();
        mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/migration-plans").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andExpect(jsonPath("$.id",is(planId)));
        mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/migration-plans").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(request.replace("targetQueueExpectedVersion\":2","targetQueueExpectedVersion\":1"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/migration-plans/{id}/approve",planId).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key","e3111111-1111-4111-8111-111111111111").contentType(MediaType.APPLICATION_JSON).content("{\"expectedPlanVersion\":0,\"reason\":\"申请人不得自审\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/migration-plans/{id}/approve",planId).with(user("reviewer").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key","e4111111-1111-4111-8111-111111111111").contentType(MediaType.APPLICATION_JSON).content("{\"expectedPlanVersion\":0,\"reason\":\"复核迁移清单后批准\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("APPROVED")));
        mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/migration-plans/{id}/execute",planId).with(user("executor").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key","e5111111-1111-4111-8111-111111111111").contentType(MediaType.APPLICATION_JSON).content("{\"expectedPlanVersion\":1,\"reason\":\"执行已批准迁移计划\"}")).andExpect(status().isAccepted()).andExpect(jsonPath("$.status",is("FAILED")));
        org.junit.jupiter.api.Assertions.assertEquals("QUEUE_TARGET",workflows.findOpenTask(open,"accept").orElseThrow().queueCode());
        var claimedTask=workflows.findOpenTask(claimed,"accept").orElseThrow();org.junit.jupiter.api.Assertions.assertEquals("QUEUE_FIN",claimedTask.queueCode());org.junit.jupiter.api.Assertions.assertEquals("iam-u-local-first-line",claimedTask.assigneeIamUserId());
        mvc.perform(get("/api/v1/admin/support-queues/QUEUE_FIN/migration-plans/{id}",planId).with(user("executor").roles("PLATFORM_ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.items[?(@.itemType == 'IN_PROGRESS_HANDOVER')].status",org.hamcrest.Matchers.hasItem("BLOCKED"))).andExpect(jsonPath("$.items[?(@.itemType == 'IN_PROGRESS_HANDOVER')].handoverTaskId").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue()))).andExpect(jsonPath("$.items[?(@.itemType == 'UNCLAIMED_TASK_MIGRATION')].status",org.hamcrest.Matchers.hasItem("SUCCEEDED")));
        mvc.perform(get("/api/v1/admin/support-queues/QUEUE_FIN").with(user("executor").roles("PLATFORM_ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("ACTIVE")));
    }
    private void createAndActivateQueue() throws Exception {String body="""
        {"code":"QUEUE_FIN","name":"财务一线队列","owningOrganizationId":"org-finance","serviceCatalogItemIds":["SC-browser-performance"],"scopes":[{"scopeType":"ORGANIZATION","scopeId":"org-finance"},{"scopeType":"SERVICE_CATALOG","scopeId":"SC-browser-performance"}],"members":[{"iamUserId":"iam-u-local-first-line","role":"MEMBER","effectiveFrom":"2026-01-01T00:00:00Z"},{"iamUserId":"iam-u-local-service-manager","role":"SUPERVISOR","effectiveFrom":"2026-01-01T00:00:00Z"}],"sharedClaimEnabled":true,"capacityLimit":100,"effectiveFrom":"2026-01-01T00:00:00Z","reason":"创建财务支持队列"}
        """;mvc.perform(post("/api/v1/admin/support-queues").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key","b1111111-1111-4111-8111-111111111111").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.version",is(1)));mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIN/activate").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key","c1111111-1111-4111-8111-111111111111").contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"reason\":\"成员范围验证完成\"}" )).andExpect(status().isOk()).andExpect(jsonPath("$.status",is("ACTIVE")));}
    private void createQueue(String code,String name,String key)throws Exception{String body="{\"code\":\""+code+"\",\"name\":\""+name+"\",\"owningOrganizationId\":\"org-finance\",\"serviceCatalogItemIds\":[\"SC-browser-performance\"],\"scopes\":[{\"scopeType\":\"ORGANIZATION\",\"scopeId\":\"org-finance\"},{\"scopeType\":\"SERVICE_CATALOG\",\"scopeId\":\"SC-browser-performance\"}],\"members\":[{\"iamUserId\":\"iam-u-local-first-line\",\"role\":\"MEMBER\",\"effectiveFrom\":\"2026-01-01T00:00:00Z\"},{\"iamUserId\":\"iam-u-local-service-manager\",\"role\":\"SUPERVISOR\",\"effectiveFrom\":\"2026-01-01T00:00:00Z\"}],\"sharedClaimEnabled\":true,\"effectiveFrom\":\"2026-01-01T00:00:00Z\",\"reason\":\"创建受控迁移队列\"}";mvc.perform(post("/api/v1/admin/support-queues").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());mvc.perform(post("/api/v1/admin/support-queues/{code}/activate",code).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"reason\":\"激活迁移测试队列\"}")).andExpect(status().isOk());}
    private String createTicket(String key,String title)throws Exception{String body=mvc.perform(post("/api/v1/tickets").with(user("iam-u-1002").roles("REQUESTER")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"serviceCatalogItemId\":\"SC-browser-performance\",\"serviceCatalogFormVersion\":1,\"type\":\"INCIDENT\",\"title\":\""+title+"\",\"description\":\"页面缓慢\",\"structuredFields\":{\"browser\":\"Chrome\"},\"tags\":[]}" )).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(body).required("id").asText();}
    private void scope(String id){BackofficeAccess old=access.findByIamUserId(id).orElseThrow();Set<BackofficeDataScope>s=Set.of(new BackofficeDataScope("ORGANIZATION","org-finance"),new BackofficeDataScope("SERVICE_CATALOG","SC-browser-performance"));access.save(new BackofficeAccess(id,true,old.roleCodes(),s,old.version()+1,Instant.now()),old.version(),"test-admin");}
}
