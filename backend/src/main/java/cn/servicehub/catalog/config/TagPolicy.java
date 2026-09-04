package cn.servicehub.catalog.config;

import java.util.List;

public record TagPolicy(boolean allowStandardTags, boolean allowFreeTags, int maxTags, List<String> allowedStandardTagCodes) {
    public TagPolicy { allowedStandardTagCodes = allowedStandardTagCodes == null ? List.of() : List.copyOf(allowedStandardTagCodes); }
}
