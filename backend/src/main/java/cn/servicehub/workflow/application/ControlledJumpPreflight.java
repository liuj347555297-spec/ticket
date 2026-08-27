package cn.servicehub.workflow.application;

import java.util.List;

/** Fail-closed execution preflight; it never changes a Flowable instance. */
public record ControlledJumpPreflight(boolean executable, List<String> blockingReasons,
                                      String currentTaskDisposition, String targetCandidateRole,
                                      String candidateResolution, boolean candidateRecalculationRequired,
                                      String slaImpact, String notificationImpact) { }
