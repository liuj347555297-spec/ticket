package cn.servicehub;

import cn.servicehub.designer.StudioModels;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DesignStudioControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RepositoryService definitions;
    @Autowired TicketWorkflowRepository workflows;
    @Autowired cn.servicehub.servicesystem.domain.ServiceSystemRepository systems;
    @Test void ownershipIsReturnedAndDirectRequestsCannotClearOrForgeIt()throws Exception{
        var now=java.time.Instant.now();
        systems.saveSystem(new cn.servicehub.servicesystem.domain.ServiceSystem("STUDIO_ERP","设计归属测试系统",null,null,"org-it",cn.servicehub.servicesystem.domain.ServiceSystemStatus.DRAFT,0,"test fixture","admin","admin",now,now,null),0);
        systems.saveCatalogMapping(new cn.servicehub.servicesystem.domain.ServiceSystemCatalogMapping("STUDIO_ERP",null,"SC-browser-performance",true,false,0,"admin",now),0);
        var input=new StudioModels.Input(0,"系统内设计","org-it",DesignStudioTest.XML,List.of(),List.of(),"保存系统内设计包","STUDIO_ERP","SC-browser-performance");
        String key=UUID.randomUUID().toString();
        String response=mvc.perform(post("/api/v1/admin/design-studio/drafts").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.systemCode",is("STUDIO_ERP"))).andExpect(jsonPath("$.serviceCatalogItemId",is("SC-browser-performance"))).andExpect(jsonPath("$.executionMode",is("DRAFT_ONLY"))).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("id").asText();
        mvc.perform(get("/api/v1/admin/design-studio/drafts").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].systemCode",hasItem("STUDIO_ERP")));
        var cleared=new StudioModels.Input(0,input.name(),input.organizationId(),input.bpmnXml(),input.forms(),input.nodeBindings(),input.reason());
        mvc.perform(put("/api/v1/admin/design-studio/drafts/"+id).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match","0").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cleared)))
            .andExpect(status().isBadRequest());
        var forged=new StudioModels.Input(0,input.name(),input.organizationId(),input.bpmnXml(),input.forms(),input.nodeBindings(),input.reason(),"STUDIO_ERP","SC-unmapped-service");
        mvc.perform(post("/api/v1/admin/design-studio/drafts").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(forged)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/design-studio/drafts/"+id).with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version",is(0))).andExpect(jsonPath("$.systemCode",is("STUDIO_ERP")));
    }
    @Test void savedDraftApiRoundtripAndUnknownPropertiesFailClosed()throws Exception{
        var input=new StudioModels.Input(0,"设计测试","org-it",DesignStudioTest.XML,List.of(),List.of(),"创建多表单设计包");String body=json.writeValueAsString(input);
        mvc.perform(get("/api/v1/admin/design-studio/drafts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/design-studio/drafts").with(user("admin").roles("PLATFORM_ADMIN")).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/design-studio/drafts").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"version\":0","\"version\":0,\"execute\":true"))).andExpect(status().isBadRequest());
        String response=mvc.perform(post("/api/v1/admin/design-studio/drafts").with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.executionMode",is("DRAFT_ONLY"))).andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).get("id").asText();
        mvc.perform(get("/api/v1/admin/design-studio/drafts/"+id).with(user("admin").roles("PLATFORM_ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.bpmnXml",is(DesignStudioTest.XML)));
        mvc.perform(put("/api/v1/admin/design-studio/drafts/"+id).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version",is(1)));
        mvc.perform(put("/api/v1/admin/design-studio/drafts/"+id).with(user("admin").roles("PLATFORM_ADMIN")).with(csrf()).header("If-Match","\"0\"").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }
    @Test void ticketDiagramUsesFrozenDefinitionAndAuthorizationNotLatest()throws Exception{
        String created=mvc.perform(post("/api/v1/tickets").with(user("iam-u-1001").roles("REQUESTER")).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("""
            {"serviceCatalogItemId":"SC-browser-performance","serviceCatalogFormVersion":1,"type":"INCIDENT","title":"页面卡顿","description":"工作台缓慢","structuredFields":{"browser":"Chrome"},"tags":[]}
            """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String ticketId=json.readTree(created).get("id").asText();var snapshot=workflows.findInstance(ticketId).orElseThrow();
        String before=mvc.perform(get("/api/v1/tickets/"+ticketId+"/workflow/diagram").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(jsonPath("$.layoutSource",is("GENERATED"))).andExpect(jsonPath("$.activeNodeIds",hasItem("classify"))).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(before).get("bpmnXml").asText().contains("BPMNShape"));
        String newer=new String(getClass().getResourceAsStream("/processes/ticket-lifecycle.bpmn20.xml").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("ServiceHub ticket lifecycle","Updated lifecycle test");var deployment=definitions.createDeployment().addString("updated.bpmn20.xml",newer).deploy();
        long count=definitions.createProcessDefinitionQuery().count();
        mvc.perform(get("/api/v1/tickets/"+ticketId+"/workflow/diagram").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(jsonPath("$.processDefinitionId",is(snapshot.processDefinitionId()))).andExpect(jsonPath("$.version",is(snapshot.processDefinitionVersion())));
        mvc.perform(get("/api/v1/workflow/ticket-lifecycle/diagram").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(jsonPath("$.version",is(snapshot.processDefinitionVersion()+1)));
        assertEquals(count,definitions.createProcessDefinitionQuery().count());
        mvc.perform(get("/api/v1/tickets/"+ticketId+"/workflow/diagram").with(user("iam-u-other").roles("REQUESTER"))).andExpect(status().isNotFound());
        workflows.updateInstance(new cn.servicehub.workflow.domain.WorkflowInstance(snapshot.ticketId(),snapshot.engineInstanceId(),snapshot.currentNode(),snapshot.status(),snapshot.resumeStatus(),snapshot.escalationLevel(),snapshot.primaryAssigneeIamUserId(),null,null,snapshot.version()+1,snapshot.createdAt(),snapshot.updatedAt()),snapshot.version());
        mvc.perform(get("/api/v1/tickets/"+ticketId+"/workflow/diagram").with(user("iam-u-1001").roles("REQUESTER"))).andExpect(status().isOk()).andExpect(jsonPath("$.availability",is("UNAVAILABLE_LEGACY"))).andExpect(jsonPath("$.bpmnXml").isEmpty());
        definitions.deleteDeployment(deployment.getId(),true);
    }
}
