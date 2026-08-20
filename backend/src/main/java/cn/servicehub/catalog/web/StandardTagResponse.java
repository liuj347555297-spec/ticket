package cn.servicehub.catalog.web;

import cn.servicehub.catalog.domain.StandardTag;

public record StandardTagResponse(String name, String label) {
    static StandardTagResponse from(StandardTag tag) { return new StandardTagResponse(tag.name(), tag.label()); }
}
