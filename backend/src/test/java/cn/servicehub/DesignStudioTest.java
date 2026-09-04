package cn.servicehub;

import cn.servicehub.designer.*;
import cn.servicehub.designer.StudioModels.*;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.workflow.engine.SafeBpmnXml;
import cn.servicehub.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesignStudioTest {
    static final String XML="""
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="servicehub.design">
          <bpmn:process id="Process_1" isExecutable="false"><bpmn:startEvent id="Start_1"/><bpmn:sequenceFlow id="Flow_1" sourceRef="Start_1" targetRef="Task_1"/><bpmn:userTask id="Task_1"/><bpmn:endEvent id="End_1"/></bpmn:process>
        </bpmn:definitions>
        """;
    private CurrentUserProvider users;
    private StudioDraftService service;
    private InMemoryStudioDraftRepository repo;
    @BeforeEach void setup(){users=mock(CurrentUserProvider.class);actor("ROLE_PLATFORM_ADMIN");repo=new InMemoryStudioDraftRepository();service=createService(repo);}
    private StudioDraftService createService(StudioDraftRepository repository){return new StudioDraftService(repository,users,mock(AuditEventPublisher.class),mock(cn.servicehub.servicesystem.domain.ServiceSystemRepository.class),mock(cn.servicehub.catalog.domain.ServiceCatalogRepository.class),mock(cn.servicehub.catalog.config.FormConfigurationRepository.class));}
    private void actor(String... roles){when(users.requireCurrentUser()).thenReturn(new CurrentUser("admin",Set.of(roles),"test"));}
    private Field field(String code,String control){return new Field("field-1",code,"测试字段",control,1,false,false,"帮助",List.of(),null);}
    private Form form(String id,int revision,String status){return new Form(id,"FORM_"+id,"审批表",revision,status,List.of(field("feedback","textarea")));}
    private Input input(long version,String xml,List<Form> forms,List<Binding> bindings){return new Input(version,"流程设计","org-it",xml,forms,bindings,"保存设计用于测试");}
    private Binding binding(String node,String form,int rev,int order){return new Binding(node,form,rev,order,"EDIT",true);}

    @Test void emptyDraftRoundtripAndCreateRetryAreSafe(){Input in=input(0,XML,List.of(),List.of());Draft d=service.create(in,"key-0001");assertEquals("DRAFT_ONLY",d.executionMode());assertEquals(d,service.create(in,"key-0001"));assertEquals(1,service.list().size());assertEquals(d,service.get(d.id()));Draft saved=service.update(d.id(),input(0,XML,List.of(new Form("new","FORM_NEW","空草稿",1,"DRAFT",List.of())),List.of()),0);assertEquals(1,saved.version());assertThrows(StudioConflictException.class,()->service.update(d.id(),in,0));}
    @Test void multipleFormsAndIndependentRevisionsBindToOneNode(){Form a=form("A",1,"FROZEN"),b=form("B",1,"DRAFT");Draft d=service.create(input(0,XML,List.of(a,b),List.of(binding("Task_1","A",1,1),binding("Task_1","B",1,2))),"key-0002");Draft next=service.update(d.id(),input(0,XML,List.of(a,b,form("A",2,"DRAFT")),d.nodeBindings()),0);assertEquals(3,next.forms().size());assertEquals(1,next.nodeBindings().get(0).formRevision());}
    @Test void frozenRevisionCannotBeRewrittenOrDeleted(){Form a=form("A",1,"FROZEN");Draft d=service.create(input(0,XML,List.of(a),List.of()),"key-0003");assertThrows(IllegalArgumentException.class,()->service.update(d.id(),input(0,XML,List.of(),List.of()),0));assertThrows(IllegalArgumentException.class,()->service.update(d.id(),input(0,XML,List.of(form("A",1,"DRAFT")),List.of()),0));assertThrows(IllegalArgumentException.class,()->service.update(d.id(),input(0,XML,List.of(a,form("A",3,"DRAFT")),List.of()),0));assertEquals(d,service.get(d.id()));}
    @Test void roleAndOrganizationAreBothRequired(){Draft d=service.create(input(0,XML,List.of(),List.of()),"key-0004");actor("ROLE_REQUESTER","DATA_SCOPE_ORGANIZATION:org-it");assertThrows(AccessDeniedException.class,()->service.list());actor("ROLE_SERVICE_MANAGER","DATA_SCOPE_ORGANIZATION:org-other");assertTrue(service.list().isEmpty());assertThrows(AccessDeniedException.class,()->service.get(d.id()));assertThrows(AccessDeniedException.class,()->service.update(d.id(),d.input(),0));actor("ROLE_SERVICE_MANAGER","DATA_SCOPE_ORGANIZATION:org-it");assertEquals(d,service.get(d.id()));assertThrows(AccessDeniedException.class,()->service.update(d.id(),new Input(0,"设计","org-other",XML,List.of(),List.of(),"改变所属组织"),0));}
    @Test void optimisticHeaderAndBodyMustAgree(){Draft d=service.create(input(0,XML,List.of(),List.of()),"key-0005");assertThrows(StudioConflictException.class,()->service.update(d.id(),d.input(),1));assertEquals(0,service.get(d.id()).version());}
    @Test void bindingsRejectMissingFormsWrongNodesAndDuplicateOrder(){Form f=form("A",1,"DRAFT");for(var bindings:List.of(List.of(binding("Task_1","A",2,1)),List.of(binding("End_1","A",1,1)),List.of(binding("Task_1","A",1,1),binding("Task_1","A",1,1))))assertThrows(IllegalArgumentException.class,()->service.create(input(0,XML,List.of(f),bindings),UUID.randomUUID().toString()));}
    @Test void unknownControlsSystemFieldsAndDuplicateCodesAreRejected(){for(Field bad:List.of(field("status","text"),field("feedback","remote-js"),field("Feedback","text"))){Form f=new Form("A","FORM_A","表单",1,"DRAFT",List.of(bad));assertThrows(IllegalArgumentException.class,()->service.create(input(0,XML,List.of(f),List.of()),UUID.randomUUID().toString()));}}
    @Test void allRegisteredControlsAreAccepted(){int index=0;List<Field> fields=new ArrayList<>();for(String control:List.of("text","textarea","number","date","datetime","select","multiselect","boolean","richtext","tags","ci","attachment","iam","user","section")){fields.add(new Field("field-"+index,"field_"+index,"字段",control,1,false,false,"",List.of(),null));index++;}assertEquals(15,service.create(input(0,XML,List.of(new Form("A","FORM_A","表单",1,"FROZEN",fields)),List.of()),"key-0006").forms().get(0).fields().size());}
    @Test void rejectsDtdExecutionExtensionsExpressionsAndHugeXml(){List<String> rejected=List.of("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///private'>]>"+XML,XML.replace("<bpmn:userTask id=\"Task_1\"/>","<bpmn:scriptTask id=\"Task_1\"><bpmn:script>alert(1)</bpmn:script></bpmn:scriptTask>"),XML.replace("id=\"Task_1\"/>","id=\"Task_1\" xmlns:f=\"http://flowable.org/bpmn\" f:delegateExpression=\"${evil}\"/>"),XML.replace("id=\"Task_1\"/>","id=\"Task_1\"><bpmn:extensionElements/></bpmn:userTask>"),"x".repeat(512001));for(String xml:rejected)assertThrows(IllegalArgumentException.class,()->SafeBpmnXml.validateDraft(xml));}
    @Test void displayProjectionStripsExecutionMetadataAndGeneratesStandardDi(){String xml=XML.replace("id=\"Task_1\"/>","id=\"Task_1\" xmlns:f=\"http://flowable.org/bpmn\" f:assignee=\"secret-user\"><bpmn:extensionElements><f:executionListener class=\"private.Class\"/></bpmn:extensionElements></bpmn:userTask>");var p=SafeBpmnXml.project(xml,"Process_1");assertEquals("GENERATED",p.layoutSource());assertFalse(p.xml().contains("secret-user"));assertFalse(p.xml().contains("private.Class"));assertFalse(p.xml().contains("executionListener"));assertTrue(p.xml().contains("BPMNShape"));assertTrue(p.xml().contains("waypoint"));assertEquals("AUTHORED",SafeBpmnXml.project(p.xml(),"Process_1").layoutSource());SafeBpmnXml.validateDraft(p.xml());}
    @Test void jdbcRepositoryPersistsRoundtripAndRejectsStaleUpdate(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:studio_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE design_studio_draft(id VARCHAR(64) PRIMARY KEY,organization_id VARCHAR(128),name VARCHAR(120),version BIGINT,execution_mode VARCHAR(20),payload_json CLOB,updated_at TIMESTAMP)");
        var persistent=new MySqlStudioDraftRepository(jdbc,JsonMapper.builder().findAndAddModules().build());var s=createService(persistent);Draft d=s.create(input(0,XML,List.of(form("A",1,"FROZEN")),List.of()),"key-0007");assertEquals(d,persistent.find(d.id()).orElseThrow());var changed=s.update(d.id(),d.input(),0);assertEquals(1,changed.version());assertThrows(StudioConflictException.class,()->persistent.update(changed,0));assertEquals(1,persistent.list().size());
    }
    @Test void xmlByteLimitExecutableFlagAndProcessingInstructionsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->SafeBpmnXml.validateDraft(XML.replace("isExecutable=\"false\"","isExecutable=\"true\"")));
        assertThrows(IllegalArgumentException.class,()->SafeBpmnXml.validateDraft("<?xml-stylesheet href=\"https://outside.invalid/file\"?>"+XML));
        String large=XML.replace("<bpmn:process","<!--"+"中".repeat(180000)+"--><bpmn:process");
        assertTrue(large.length()<SafeBpmnXml.MAX_XML);
        assertThrows(IllegalArgumentException.class,()->SafeBpmnXml.validateDraft(large));
    }
    @Test void sameFormCannotBindTwiceAndReadOnlyCannotRequireCompletion(){
        var forms=List.of(form("A",1,"FROZEN"),form("A",2,"DRAFT"));
        assertThrows(IllegalArgumentException.class,()->service.create(input(0,XML,forms,List.of(binding("Task_1","A",1,1),binding("Task_1","A",2,2))),"key-0008"));
        assertThrows(IllegalArgumentException.class,()->service.create(input(0,XML,forms,List.of(new Binding("Task_1","A",1,1,"READ_ONLY",true))),"key-0009"));
    }
    @Test void generatedDisplayXmlPassesBpmnSchemaAndSinglePoolReferenceIsAllowed(){
        var projection=SafeBpmnXml.project(XML,"Process_1");
        assertDoesNotThrow(()->new org.flowable.bpmn.converter.BpmnXMLConverter().validateModel(()->new java.io.ByteArrayInputStream(projection.xml().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        String pool=XML.replace("</bpmn:definitions>","<bpmn:collaboration id=\"Collaboration_1\"><bpmn:participant id=\"Pool_1\" processRef=\"Process_1\"/></bpmn:collaboration></bpmn:definitions>");
        assertDoesNotThrow(()->SafeBpmnXml.validateDraft(pool));
    }
}
