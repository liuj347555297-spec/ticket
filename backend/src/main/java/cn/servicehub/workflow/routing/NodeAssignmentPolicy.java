package cn.servicehub.workflow.routing;

import java.util.Set;

/** Versioned service-catalog routing policy. Candidate roles are platform-role codes, never browser input. */
public record NodeAssignmentPolicy(String serviceCatalogItemId, String nodeKey, NodeAssignmentMode mode,
                                   String queueCode, Set<String> candidateRoles, long version, boolean enabled) {
    public NodeAssignmentPolicy { candidateRoles = candidateRoles == null ? Set.of() : Set.copyOf(candidateRoles); }
    public NodeAssignmentPolicy(String serviceCatalogItemId,String nodeKey,NodeAssignmentMode mode,Set<String> candidateRoles,long version,boolean enabled){this(serviceCatalogItemId,nodeKey,mode,null,candidateRoles,version,enabled);}
}
