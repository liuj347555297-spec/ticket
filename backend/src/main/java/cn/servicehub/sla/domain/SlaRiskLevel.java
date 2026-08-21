package cn.servicehub.sla.domain;

/** A derived warning, never an automatic escalation or punitive action. */
public enum SlaRiskLevel {
    ON_TRACK, AT_RISK, BREACHED
}
