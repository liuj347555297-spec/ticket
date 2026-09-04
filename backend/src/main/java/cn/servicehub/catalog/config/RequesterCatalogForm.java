package cn.servicehub.catalog.config;

import cn.servicehub.catalog.domain.ServiceCatalogItem;
import java.util.List;

/** A server-resolved requester schema; it is never accepted from the browser. */
public record RequesterCatalogForm(ServiceCatalogItem item, int formVersion, String schemaHash,
                                   List<ConfiguredFormField> fields, TagPolicy tagPolicy) { }
