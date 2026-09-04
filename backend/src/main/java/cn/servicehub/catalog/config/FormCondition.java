package cn.servicehub.catalog.config;

import java.util.List;

/** Declarative condition only. Expressions, scripts and arbitrary regular expressions are never accepted. */
public record FormCondition(String fieldCode, FormConditionOperator operator, List<String> values) {
    public FormCondition { values = values == null ? List.of() : List.copyOf(values); }
}
