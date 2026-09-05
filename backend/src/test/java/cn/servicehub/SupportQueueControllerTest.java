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
import java.util.LinkedHashSet;
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
    @Autowired cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicyRepository lifecyclePolicies;
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
    @Test void authorizedRequesterSeesFiveSafeSnapshotCandidatesUntilOneClaims() throws Exception {
        for (String id : Set.of("iam-u-1001", "iam-u-local-requester", "iam-u-local-first-line", "iam-u-local-service-manager", "iam-u-local-admin")) {
            grantQueueCandidate(id);
        }
        createAndActivateFiveMemberQueue();
        mvc.perform(put("/api/v1/admin/service-catalog/items/SC-browser-performance/workflow-node-policies/accept")
                .with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"SHARED_QUEUE\",\"queueCode\":\"QUEUE_FIVE\",\"candidateRoles\":[\"ROLE_FIRST_LINE_SUPPORT\"],\"enabled\":true}"))
            .andExpect(status().isOk());
        String ticketId = createTicket("a4111111-1111-4111-8111-111111111111", "五人共享抢单");

        // The requester is allowed to read the ticket overview, but receives only the explicitly
        // safe candidate DTO. Login name, roles, contact details and account state stay private.
        mvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidateCount", is(5)))
            .andExpect(jsonPath("$.acceptanceCandidates.length()", is(5)))
            .andExpect(jsonPath("$.acceptanceCandidates[0].iamUserId").isNotEmpty())
            .andExpect(jsonPath("$.acceptanceCandidates[0].displayName").isNotEmpty())
            .andExpect(jsonPath("$.acceptanceCandidates[0].loginName").doesNotExist())
            .andExpect(jsonPath("$.acceptanceCandidates[0].roles").doesNotExist())
            .andExpect(jsonPath("$.acceptanceCandidates[0].email").doesNotExist())
            .andExpect(jsonPath("$.acceptanceCandidates[0].phone").doesNotExist());
        mvc.perform(get("/api/v1/tickets/{id}/processing-details", ticketId).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version", is(0))).andExpect(jsonPath("$.editable", is(false)));
        mvc.perform(put("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"eventSource\":\"PHONE\",\"processingDescription\":\"领取前不得写入\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "999999999999999999999999999999")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(5, queues.findRoutingSnapshots(ticketId).get(0).candidateIamUserIds().size());

        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CLAIM\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidateCount", is(0)))
            .andExpect(jsonPath("$.acceptanceCandidates").isEmpty())
            .andExpect(jsonPath("$.participants[?(@.role == 'PRIMARY')].identity.iamUserId",
                org.hamcrest.Matchers.hasItem("iam-u-local-first-line")));
        String processingBody = "{\"eventSource\":\"PHONE\",\"proposingOrganization\":\"财务运行组\",\"onSiteSupportRequired\":true,\"causeCategory\":\"UNDER_INVESTIGATION\",\"processingDescription\":\"已核对告警与访问日志。\",\"resolutionDescription\":\"待确认修复窗口。\",\"thirdPartyHandled\":false,\"currentProgress\":\"定位中\"}";
        mvc.perform(put("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(processingBody))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version", is(1))).andExpect(jsonPath("$.editable", is(true)))
            .andExpect(jsonPath("$.processingDescription", is("已核对告警与访问日志。")));
        mvc.perform(put("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON).content(processingBody))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/tickets/{id}/processing-details", ticketId).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version", is(1))).andExpect(jsonPath("$.editable", is(false)))
            .andExpect(jsonPath("$.updatedByIamUserId", is("iam-u-local-first-line")));
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("IN_PROGRESS"))).andExpect(jsonPath("$.version", is(2)));
        mvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleApprovalRequests").isEmpty());
        mvc.perform(get("/api/v1/tickets/{id}/workflow/transfer-candidates", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.iamUserId == 'iam-u-local-service-manager')]").isNotEmpty());
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"TRANSFER\",\"targetIamUserId\":\"iam-u-1002\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))).with(csrf()).header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"TRANSFER\",\"targetIamUserId\":\"iam-u-local-service-manager\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-first-line", "TEST"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.editable", is(false)));
        mvc.perform(get("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-service-manager", "TEST"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.editable", is(true)));
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId)
                .with(authentication(verified.create("iam-u-local-service-manager", "TEST"))).with(csrf()).header("If-Match", "\"2\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REQUEST_USER_FEEDBACK\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("PENDING_USER_FEEDBACK"))).andExpect(jsonPath("$.version", is(3)));
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(user("iam-u-1002").roles("REQUESTER")).with(csrf()).header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"START_PROCESSING\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("IN_PROGRESS"))).andExpect(jsonPath("$.version", is(4)));
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId)
                .with(authentication(verified.create("iam-u-local-service-manager", "TEST"))).with(csrf()).header("If-Match", "\"4\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REQUEST_USER_FEEDBACK\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("PENDING_USER_FEEDBACK"))).andExpect(jsonPath("$.version", is(5)));
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(user("iam-u-1002").roles("REQUESTER")).with(csrf()).header("If-Match", "\"5\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RESOLVE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("RESOLVED"))).andExpect(jsonPath("$.version", is(6)));
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(user("iam-u-1002").roles("REQUESTER")).with(csrf()).header("If-Match", "\"6\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CLOSE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CLOSED"))).andExpect(jsonPath("$.version", is(7)));
        mvc.perform(get("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-service-manager", "TEST"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.editable", is(false)));
        mvc.perform(put("/api/v1/tickets/{id}/processing-details", ticketId)
                .with(authentication(verified.create("iam-u-local-service-manager", "TEST"))).with(csrf()).header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON).content(processingBody))
            .andExpect(status().isForbidden());
        // The append-only snapshot is still available to the server for audit after the public
        // current-candidate projection has converged to the actual assignee.
        org.junit.jupiter.api.Assertions.assertEquals(5, queues.findRoutingSnapshots(ticketId).get(0).candidateIamUserIds().size());
    }

    @Test void nonSharedAssignmentNeverPublishesAcceptanceCandidates() throws Exception {
        mvc.perform(put("/api/v1/admin/service-catalog/items/SC-browser-performance/workflow-node-policies/accept")
                .with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"SYSTEM_RANDOM\",\"candidateRoles\":[\"ROLE_FIRST_LINE_SUPPORT\"],\"enabled\":true}"))
            .andExpect(status().isOk());
        String ticketId = createTicket("a5111111-1111-4111-8111-111111111111", "系统随机分派");
        mvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(user("iam-u-1002").roles("REQUESTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidateCount", is(0)))
            .andExpect(jsonPath("$.acceptanceCandidates").isEmpty());
    }
    @Test void explicitlyPublishedAcceptancePolicyStillCreatesRealApproval() throws Exception {
        createAndActivateQueue();
        mvc.perform(put("/api/v1/admin/service-catalog/items/SC-browser-performance/workflow-node-policies/accept")
                .with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"SHARED_QUEUE\",\"queueCode\":\"QUEUE_FIN\",\"candidateRoles\":[\"ROLE_FIRST_LINE_SUPPORT\"],\"enabled\":true}"))
            .andExpect(status().isOk());
        Instant now = Instant.now();
        var publishedPolicy = lifecyclePolicies.save(new cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy(
            "accept-explicit", "高风险目录受理复核", cn.servicehub.workflow.domain.WorkflowAction.ACCEPT,
            "SC-browser-performance", null, Set.of("ROLE_SERVICE_MANAGER"), "ANY_ONE", 100, 60,
            "TEST-60M", "TEST-AUDIT", "PUBLISHED", 1, now, now, now), null);
        String ticketId = createTicket("a6111111-1111-4111-8111-111111111111", "受理审批策略");
        var handler = verified.create("iam-u-local-first-line", "TEST");
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(authentication(handler)).with(csrf())
                .header("If-Match", "\"1\"").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CLAIM\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(authentication(handler)).with(csrf())
                .header("If-Match", "\"1\"").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("PENDING_ACCEPTANCE"))).andExpect(jsonPath("$.version", is(1)));
        // Deployment may retire a blanket policy, but an already frozen request still governs
        // this exact ticket/workflow source and cannot be bypassed by resubmitting the action.
        lifecyclePolicies.save(new cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy(
            publishedPolicy.id(), publishedPolicy.name(), publishedPolicy.action(), publishedPolicy.serviceCatalogItemId(), publishedPolicy.priority(),
            publishedPolicy.candidateRoles(), publishedPolicy.decisionMode(), publishedPolicy.approvalThresholdPercent(), publishedPolicy.timeoutMinutes(),
            publishedPolicy.timeoutPolicyVersion(), publishedPolicy.escalationPolicyVersion(), "RETIRED", 2,
            publishedPolicy.createdAt(), Instant.now(), publishedPolicy.publishedAt()), 1L);
        mvc.perform(post("/api/v1/tickets/{id}/workflow/actions", ticketId).with(authentication(handler)).with(csrf())
                .header("If-Match", "\"1\"").contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ACCEPT\"}"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/tickets/{id}/workflow", ticketId).with(authentication(handler)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleApprovalRequests[0].action", is("ACCEPT")))
            .andExpect(jsonPath("$.lifecycleApprovalRequests[0].status", is("PENDING_APPROVAL")))
            .andExpect(jsonPath("$.availableActions[?(@.code == 'ACCEPT')]").isEmpty());
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
    private void createAndActivateFiveMemberQueue() throws Exception {
        String body = """
            {"code":"QUEUE_FIVE","name":"五人共享队列","owningOrganizationId":"org-finance","serviceCatalogItemIds":["SC-browser-performance"],"scopes":[{"scopeType":"ORGANIZATION","scopeId":"org-finance"},{"scopeType":"SERVICE_CATALOG","scopeId":"SC-browser-performance"}],"members":[{"iamUserId":"iam-u-1001","role":"MEMBER","effectiveFrom":"2026-01-01T00:00:00Z"},{"iamUserId":"iam-u-local-requester","role":"MEMBER","effectiveFrom":"2026-01-01T00:00:00Z"},{"iamUserId":"iam-u-local-first-line","role":"MEMBER","effectiveFrom":"2026-01-01T00:00:00Z"},{"iamUserId":"iam-u-local-service-manager","role":"SUPERVISOR","effectiveFrom":"2026-01-01T00:00:00Z"},{"iamUserId":"iam-u-local-admin","role":"SUPERVISOR","effectiveFrom":"2026-01-01T00:00:00Z"}],"sharedClaimEnabled":true,"capacityLimit":100,"effectiveFrom":"2026-01-01T00:00:00Z","reason":"验证五人共享抢单"}
            """;
        mvc.perform(post("/api/v1/admin/support-queues").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf())
                .header("Idempotency-Key", "b4111111-1111-4111-8111-111111111111")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/admin/support-queues/QUEUE_FIVE/activate").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf())
                .header("Idempotency-Key", "c4111111-1111-4111-8111-111111111111")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"reason\":\"五名成员范围验证完成\"}"))
            .andExpect(status().isOk());
    }
    private void createQueue(String code,String name,String key)throws Exception{String body="{\"code\":\""+code+"\",\"name\":\""+name+"\",\"owningOrganizationId\":\"org-finance\",\"serviceCatalogItemIds\":[\"SC-browser-performance\"],\"scopes\":[{\"scopeType\":\"ORGANIZATION\",\"scopeId\":\"org-finance\"},{\"scopeType\":\"SERVICE_CATALOG\",\"scopeId\":\"SC-browser-performance\"}],\"members\":[{\"iamUserId\":\"iam-u-local-first-line\",\"role\":\"MEMBER\",\"effectiveFrom\":\"2026-01-01T00:00:00Z\"},{\"iamUserId\":\"iam-u-local-service-manager\",\"role\":\"SUPERVISOR\",\"effectiveFrom\":\"2026-01-01T00:00:00Z\"}],\"sharedClaimEnabled\":true,\"effectiveFrom\":\"2026-01-01T00:00:00Z\",\"reason\":\"创建受控迁移队列\"}";mvc.perform(post("/api/v1/admin/support-queues").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());mvc.perform(post("/api/v1/admin/support-queues/{code}/activate",code).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"reason\":\"激活迁移测试队列\"}")).andExpect(status().isOk());}
    private String createTicket(String key,String title)throws Exception{String body=mvc.perform(post("/api/v1/tickets").with(user("iam-u-1002").roles("REQUESTER")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content("{\"serviceCatalogItemId\":\"SC-browser-performance\",\"serviceCatalogFormVersion\":1,\"type\":\"INCIDENT\",\"title\":\""+title+"\",\"description\":\"页面缓慢\",\"structuredFields\":{\"browser\":\"Chrome\"},\"tags\":[]}" )).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(body).required("id").asText();}
    private void scope(String id){BackofficeAccess old=access.findByIamUserId(id).orElseThrow();Set<BackofficeDataScope>s=Set.of(new BackofficeDataScope("ORGANIZATION","org-finance"),new BackofficeDataScope("SERVICE_CATALOG","SC-browser-performance"));access.save(new BackofficeAccess(id,true,old.roleCodes(),s,old.version()+1,Instant.now()),old.version(),"test-admin");}
    private void grantQueueCandidate(String id) {
        BackofficeAccess old = access.findByIamUserId(id).orElse(null);
        Set<String> roles = new LinkedHashSet<>(old == null ? Set.of() : old.roleCodes());
        roles.add("ROLE_FIRST_LINE_SUPPORT");
        Set<BackofficeDataScope> scopes = new LinkedHashSet<>(old == null ? Set.of() : old.dataScopes());
        scopes.add(new BackofficeDataScope("ORGANIZATION", "org-finance"));
        scopes.add(new BackofficeDataScope("SERVICE_CATALOG", "SC-browser-performance"));
        long expected = old == null ? 0 : old.version();
        access.save(new BackofficeAccess(id, true, roles, scopes, expected + 1, Instant.now()), expected, "test-admin");
    }
}
