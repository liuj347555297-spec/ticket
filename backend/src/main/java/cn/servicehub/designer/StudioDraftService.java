package cn.servicehub.designer;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.designer.StudioModels.*;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.workflow.engine.SafeBpmnXml;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudioDraftService {
    private static final Set<String> CONTROLS=Set.of("text","textarea","number","date","datetime","select","multiselect","boolean","richtext","tags","ci","attachment","iam","user","section");
    private static final Set<String> SYSTEM_FIELDS=Set.of("id","ticket_id","ticket_no","status","priority","requester","requester_id","requester_department","iam_user_id","organization","organization_id","handler","assignee","processor","workflow","workflow_instance","process_instance","audit","created_at","updated_at","attachments");
    private final StudioDraftRepository repository; private final CurrentUserProvider users; private final AuditEventPublisher audit;
    private final cn.servicehub.servicesystem.domain.ServiceSystemRepository systems;
    private final cn.servicehub.catalog.domain.ServiceCatalogRepository catalogs;
    private final cn.servicehub.catalog.config.FormConfigurationRepository configurations;
    public StudioDraftService(StudioDraftRepository repository,CurrentUserProvider users,AuditEventPublisher audit,
            cn.servicehub.servicesystem.domain.ServiceSystemRepository systems,
            cn.servicehub.catalog.domain.ServiceCatalogRepository catalogs,
            cn.servicehub.catalog.config.FormConfigurationRepository configurations) {
        this.repository=repository;this.users=users;this.audit=audit;this.systems=systems;this.catalogs=catalogs;this.configurations=configurations;
    }

    public List<Summary> list(){CurrentUser actor=requireRole(false);var result=repository.list().stream().filter(d->inScope(actor,d.organizationId())).map(Summary::from).toList();audit(actor,"DESIGN_STUDIO_LIST","collection",Map.of("returned",String.valueOf(result.size())));return result;}
    public Draft get(String id){CurrentUser actor=requireRole(false);Draft d=scoped(actor,required(id));audit(actor,"DESIGN_STUDIO_READ",id,Map.of("version",String.valueOf(d.version())));return d;}
    @Transactional public Draft create(Input input,String key){
        CurrentUser actor=requireRole(true);validate(input);scope(actor,input.organizationId());validateOwnership(actor,input);if(input.version()!=0)throw new StudioConflictException();preserveHistory(List.of(),input.forms());
        // Caller-scoped deterministic id makes an uncertain create retry safe without persisting the raw key.
        String id="DS-"+UUID.nameUUIDFromBytes((actor.iamUserId()+"\n"+key).getBytes(StandardCharsets.UTF_8));
        var existing=repository.find(id);if(existing.isPresent()){Draft d=scoped(actor,existing.get());if(!d.input().equals(input))throw new StudioConflictException();return d;}
        Draft result=repository.insert(draft(id,input,0));audit(actor,"DESIGN_STUDIO_CREATED",id,Map.of("version","0","organizationId",input.organizationId()));return result;
    }
    @Transactional public Draft update(String id,Input input,long ifMatch){
        CurrentUser actor=requireRole(true);Draft before=scoped(actor,required(id));
        if(ifMatch!=input.version()||input.version()!=before.version())throw new StudioConflictException();
        validate(input);scope(actor,input.organizationId());if(!before.organizationId().equals(input.organizationId()))throw new AccessDeniedException("Design organization is immutable");
        preserveOwnership(before,input);validateOwnership(actor,input);
        preserveHistory(before.forms(),input.forms());
        Draft result=repository.update(draft(id,input,before.version()+1),before.version());audit(actor,"DESIGN_STUDIO_UPDATED",id,Map.of("version",String.valueOf(result.version()),"reasonLength",String.valueOf(input.reason().length())));return result;
    }
    private Draft draft(String id,Input in,long version){return new Draft(id,version,in.name(),in.organizationId(),in.bpmnXml(),in.forms(),in.nodeBindings(),in.reason(),"DRAFT_ONLY",Instant.now(),in.systemCode(),in.serviceCatalogItemId());}
    private void preserveOwnership(Draft before,Input next){
        if(before.systemCode()!=null&&!before.systemCode().equals(next.systemCode()))throw new IllegalArgumentException("Design system cannot be changed or cleared");
        if(before.serviceCatalogItemId()!=null&&!before.serviceCatalogItemId().equals(next.serviceCatalogItemId()))throw new IllegalArgumentException("Design service cannot be changed or cleared");
    }
    private void validateOwnership(CurrentUser actor,Input in){
        // Omitted ownership remains supported for old clients; new embedded UI always supplies a system.
        if(in.systemCode()==null){if(in.serviceCatalogItemId()!=null)throw invalid();return;}
        var system=systems.findByCode(in.systemCode()).orElseThrow(StudioDraftService::invalid);
        scope(actor,system.owningOrganizationId());
        if(!system.owningOrganizationId().equals(in.organizationId()))throw new AccessDeniedException("Design organization must match the registered system");
        if(in.serviceCatalogItemId()==null)return;
        boolean exists=configurations.findById(in.serviceCatalogItemId()).isPresent()||catalogs.findById(in.serviceCatalogItemId()).isPresent();
        if(!exists)throw invalid();
        boolean systemMapping=systems.findSystemCatalogMappings(system.code()).stream()
            .anyMatch(m->m.active()&&in.serviceCatalogItemId().equals(m.serviceCatalogItemId()));
        boolean moduleMapping=systems.findModules(system.code()).stream().filter(cn.servicehub.servicesystem.domain.ServiceSystemModule::active)
            .flatMap(m->systems.findModuleCatalogMappings(system.code(),m.code()).stream())
            .anyMatch(m->m.active()&&in.serviceCatalogItemId().equals(m.serviceCatalogItemId()));
        if(!systemMapping&&!moduleMapping)throw invalid();
    }
    private void preserveHistory(List<Form> previous,List<Form> next){
        Map<String,Form> indexed=new HashMap<>();next.forEach(f->indexed.put(ref(f.formId(),f.revision()),f));
        for(Form old:previous){Form replacement=indexed.get(ref(old.formId(),old.revision()));if("FROZEN".equals(old.status())&&!old.equals(replacement))throw new IllegalArgumentException("Frozen design revision cannot be changed or removed");}
        Map<String,Integer> maximum=new HashMap<>();previous.forEach(f->maximum.merge(f.formId(),f.revision(),Math::max));
        var oldKeys=previous.stream().map(f->ref(f.formId(),f.revision())).collect(java.util.stream.Collectors.toSet());
        for(Form f:next.stream().sorted(Comparator.comparingInt(Form::revision)).toList())if(!oldKeys.contains(ref(f.formId(),f.revision()))){int expected=maximum.getOrDefault(f.formId(),0)+1;if(f.revision()!=expected)throw new IllegalArgumentException("New revision must follow the last revision");maximum.put(f.formId(),f.revision());}
    }
    private void validate(Input in){
        if(in==null||in.version()<0||!plain(in.name(),1,120)||!identifier(in.organizationId(),128)||!plain(in.reason(),4,500)||in.forms().size()>100||in.nodeBindings().size()>500)throw invalid();
        if(in.systemCode()!=null&&!in.systemCode().matches("^[A-Z][A-Z0-9_]{2,63}$")||in.serviceCatalogItemId()!=null&&!in.serviceCatalogItemId().matches("^[A-Za-z0-9_-]{3,64}$"))throw invalid();
        SafeBpmnXml.validateDraft(in.bpmnXml());
        var doc=SafeBpmnXml.parse(in.bpmnXml());Set<String> bindable=new HashSet<>();for(String type:List.of("startEvent","userTask")){var nodes=doc.getElementsByTagNameNS(SafeBpmnXml.BPMN,type);for(int i=0;i<nodes.getLength();i++)bindable.add(((org.w3c.dom.Element)nodes.item(i)).getAttribute("id"));}
        Set<String> revisions=new HashSet<>();Map<String,String> formCodes=new HashMap<>();Map<String,String> codeOwners=new HashMap<>();int totalFields=0,totalOptions=0;
        for(Form f:in.forms()){
            if(f==null||!identifier(f.formId(),128)||f.code()==null||!f.code().matches("^[A-Za-z][A-Za-z0-9_-]{0,63}$")||!plain(f.name(),1,120)||f.revision()<1||f.revision()>100000||f.status()==null||!Set.of("DRAFT","FROZEN").contains(f.status())||f.fields().size()>100||("FROZEN".equals(f.status())&&f.fields().isEmpty())||!revisions.add(ref(f.formId(),f.revision())))throw invalid();
            if(formCodes.containsKey(f.formId())&&!formCodes.get(f.formId()).equals(f.code())||codeOwners.containsKey(f.code())&&!codeOwners.get(f.code()).equals(f.formId()))throw invalid();formCodes.put(f.formId(),f.code());codeOwners.put(f.code(),f.formId());
            Set<String> fields=new HashSet<>(),fieldIds=new HashSet<>();totalFields+=f.fields().size();if(totalFields>2000)throw invalid();
            for(Field field:f.fields()){
                if(field==null||!identifier(field.id(),128)||!fieldIds.add(field.id())||field.code()==null||!field.code().matches("^[a-z][a-z0-9_]{0,63}$")||SYSTEM_FIELDS.contains(field.code())||!fields.add(field.code())||!plain(field.label(),1,80)||field.control()==null||!CONTROLS.contains(field.control())||field.controlVersion()!=1||!plain(field.helpText(),0,300)||field.options().size()>100)throw invalid();
                if(field.dictionaryCode()!=null&&!field.dictionaryCode().matches("^[A-Z][A-Z0-9_]{1,62}$"))throw invalid();
                totalOptions+=field.options().size();if(totalOptions>2000)throw invalid();
                if((!field.options().isEmpty()||field.dictionaryCode()!=null)&&!Set.of("select","multiselect").contains(field.control()))throw invalid();
                Set<String> optionValues=new HashSet<>();for(Option option:field.options())if(option==null||!plain(option.value(),1,100)||!plain(option.label(),1,100)||!optionValues.add(option.value()))throw invalid();
            }
        }
        Set<String> bindingRefs=new HashSet<>(),orders=new HashSet<>();for(Binding b:in.nodeBindings())if(b==null||!bindable.contains(b.nodeId())||!revisions.contains(ref(b.formId(),b.formRevision()))||b.displayOrder()<1||b.displayOrder()>500||b.mode()==null||!Set.of("EDIT","READ_ONLY").contains(b.mode())||("READ_ONLY".equals(b.mode())&&b.requiredOnComplete())||!bindingRefs.add(b.nodeId()+"/"+b.formId())||!orders.add(b.nodeId()+"/"+b.displayOrder()))throw invalid();
    }
    private CurrentUser requireRole(boolean write){CurrentUser actor=users.requireCurrentUser();if(actor.authorities().stream().noneMatch((write?Set.of("ROLE_SERVICE_MANAGER","ROLE_PLATFORM_ADMIN"):Set.of("ROLE_SERVICE_MANAGER","ROLE_PLATFORM_ADMIN","ROLE_AUDITOR"))::contains))throw new AccessDeniedException("Design studio role is required");return actor;}
    private boolean inScope(CurrentUser a,String org){return a.authorities().contains("ROLE_PLATFORM_ADMIN")||a.authorities().contains("DATA_SCOPE_ORGANIZATION:"+org);}
    private void scope(CurrentUser a,String org){if(!inScope(a,org))throw new AccessDeniedException("Design organization is outside assigned scope");}
    private Draft scoped(CurrentUser a,Draft d){scope(a,d.organizationId());return d;}
    private Draft required(String id){return repository.find(id).orElseThrow(()->new cn.servicehub.ticket.application.TicketNotFoundException(id));}
    private static String ref(String id,int revision){return id+"@"+revision;}
    private static boolean identifier(String s,int max){return s!=null&&s.length()<=max&&s.matches("^[A-Za-z0-9][A-Za-z0-9:_-]{0,127}$");}
    private static boolean plain(String s,int min,int max){return s!=null&&s.trim().length()>=min&&s.length()<=max&&s.indexOf('<')<0&&s.indexOf('>')<0&&s.chars().noneMatch(Character::isISOControl)&&!s.contains("${")&&!s.contains("#{");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Design metadata is invalid or unsupported");}
    private void audit(CurrentUser actor,String action,String id,Map<String,String> attrs){audit.publish(new AuditEvent(Instant.now(),MDC.get("requestId")==null?"system":MDC.get("requestId"),actor.iamUserId(),action,"design-studio",id,attrs));}
}
