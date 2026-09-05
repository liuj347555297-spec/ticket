package cn.servicehub.localauth.domain;

import java.util.List;

public record LocalAccountPage(List<LocalAccount> items, int page, int pageSize, long total) {
    public LocalAccountPage { items = items == null ? List.of() : List.copyOf(items); }
}
