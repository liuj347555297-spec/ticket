package cn.servicehub.workflow.application;

/**
 * Minimal personnel projection that may be disclosed to an authorized ticket reader while a
 * shared queue task is still unclaimed. Credentials, login names, roles and contact details are
 * deliberately absent.
 */
public record AcceptanceCandidate(String iamUserId, String displayName, String organizationName,
                                  String positionName) {
}
