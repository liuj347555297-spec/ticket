package cn.servicehub.localauth.infrastructure;

import cn.servicehub.iam.domain.IamUserProjection;
import cn.servicehub.iam.domain.OrganizationSummary;
import cn.servicehub.iam.domain.PositionSummary;
import cn.servicehub.localauth.domain.LocalAccountProjectionWriter;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryLocalAccountProjectionWriter implements LocalAccountProjectionWriter {
    private static final Set<String> ORGANIZATIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static { ORGANIZATIONS.addAll(Set.of("ORG-LOCAL-IT", "org-it", "org-finance")); }
    private final InMemoryLocalProjectionStore store;
    public InMemoryLocalAccountProjectionWriter(InMemoryLocalProjectionStore store){this.store=store;}
    @Override public boolean activeOrganizationExists(String id){return ORGANIZATIONS.contains(id);}
    @Override public void ensureLocalOrganization(String id,String name,Instant at){ORGANIZATIONS.add(id);}
    @Override public void upsert(String id,String login,String display,String org,boolean active,long version,Instant at){
        store.put(new IamUserProjection(id,login,display,active,new OrganizationSummary(org,org),
            List.of(new PositionSummary("LOCAL-ACCOUNT","本地账号",true)),"LOCAL_ACCOUNT",Long.toString(version),at));
    }
}
