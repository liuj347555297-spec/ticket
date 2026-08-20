package cn.servicehub.catalog.infrastructure;

import cn.servicehub.catalog.domain.CaseMatchRecord;
import cn.servicehub.catalog.domain.CaseMatchRule;
import cn.servicehub.catalog.domain.CatalogPublicationStatus;
import cn.servicehub.catalog.domain.DictionaryDefinition;
import cn.servicehub.catalog.domain.DictionaryOption;
import cn.servicehub.catalog.domain.FormFieldDefinition;
import cn.servicehub.catalog.domain.FormFieldType;
import cn.servicehub.catalog.domain.KnowledgeCase;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import cn.servicehub.catalog.domain.ServiceCatalogRepository;
import cn.servicehub.catalog.domain.StandardTag;
import cn.servicehub.ticket.domain.TicketTag;
import cn.servicehub.ticket.domain.TicketType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Deterministic developer/test seed. Production catalog configuration always comes from MySQL. */
@Repository
@Profile("!mysql")
public class InMemoryServiceCatalogRepository implements ServiceCatalogRepository {
    private final Map<String, DictionaryDefinition> dictionaries = Map.of(
        "BROWSER", new DictionaryDefinition("BROWSER", "浏览器", CatalogPublicationStatus.PUBLISHED, List.of(
            new DictionaryOption("Chrome", "Google Chrome", true, 10),
            new DictionaryOption("Edge", "Microsoft Edge", true, 20),
            new DictionaryOption("Firefox", "Mozilla Firefox", true, 30))));
    private final List<ServiceCatalogItem> items = List.of(new ServiceCatalogItem(
        "SC-browser-performance", "页面卡顿与浏览器性能", "处理内网系统页面加载缓慢、卡顿和浏览器兼容问题",
        CatalogPublicationStatus.PUBLISHED, java.util.Set.of(TicketType.INCIDENT, TicketType.SERVICE_REQUEST), List.of(
            new FormFieldDefinition("browser", "浏览器", FormFieldType.SINGLE_SELECT, false, null, "BROWSER", 10),
            new FormFieldDefinition("error_code", "错误码", FormFieldType.TEXT, false, 128, null, 20),
            new FormFieldDefinition("affected_page", "受影响页面", FormFieldType.TEXT, false, 500, null, 30),
            new FormFieldDefinition("configuration_item_id", "关联配置项", FormFieldType.CI_ID, false, null, null, 40))));
    private final List<StandardTag> standardTags = List.of(
        new StandardTag("#页面卡顿", "页面卡顿"), new StandardTag("#浏览器", "浏览器"), new StandardTag("#网络", "网络"));
    private final List<KnowledgeCase> cases = List.of(new KnowledgeCase("case-browser-cache", "浏览器缓存导致核协 E+ 页面卡顿",
        "确认受影响页面后，清理站点缓存并重新登录；仍未恢复时附上错误码转二线。", CatalogPublicationStatus.PUBLISHED));
    private final List<CaseMatchRule> rules = List.of(new CaseMatchRule(1, "case-browser-cache", true,
        "SC-browser-performance", null, null, null, "#页面卡顿", TicketTag.Kind.STANDARD, null, "核协", 85));
    private final CopyOnWriteArrayList<CaseMatchRecord> records = new CopyOnWriteArrayList<>();

    @Override public List<ServiceCatalogItem> findPublishedItems() { return items.stream().filter(ServiceCatalogItem::isPublished).toList(); }
    @Override public Optional<ServiceCatalogItem> findById(String id) { return items.stream().filter(item -> item.id().equals(id)).findFirst(); }
    @Override public Optional<DictionaryDefinition> findDictionary(String code) { return Optional.ofNullable(dictionaries.get(code)); }
    @Override public List<StandardTag> findEnabledStandardTags() { return standardTags; }
    @Override public List<KnowledgeCase> findPublishedCases() { return cases.stream().filter(KnowledgeCase::isPublished).toList(); }
    @Override public List<CaseMatchRule> findEnabledRules() { return rules.stream().filter(CaseMatchRule::enabled).toList(); }
    @Override public void saveMatchRecord(CaseMatchRecord record) { records.add(record); }
}
