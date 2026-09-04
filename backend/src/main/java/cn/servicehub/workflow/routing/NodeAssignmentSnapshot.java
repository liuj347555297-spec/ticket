package cn.servicehub.workflow.routing;

import java.time.Instant;
import java.util.Set;

/** Immutable routing evidence captured when a ticket enters a node. */
public record NodeAssignmentSnapshot(String ticketId, String nodeKey, NodeAssignmentMode mode, String queueCode, Set<String> candidateRoles,
                                     long policyVersion, String selectedIamUserId, Instant capturedAt) {
    public NodeAssignmentSnapshot { candidateRoles = candidateRoles == null ? Set.of() : Set.copyOf(candidateRoles); }
    public NodeAssignmentSnapshot(String ticketId,String nodeKey,NodeAssignmentMode mode,Set<String> roles,long version,String selected,Instant at){this(ticketId,nodeKey,mode,null,roles,version,selected,at);}
}
