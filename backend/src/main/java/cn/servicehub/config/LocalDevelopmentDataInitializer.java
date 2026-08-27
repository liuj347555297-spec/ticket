package cn.servicehub.config;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Seeds synthetic MySQL data only for the local-dev profile; IAM synchronization always owns real projections. */
@Component
@Profile("mysql & local-dev")
public class LocalDevelopmentDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public LocalDevelopmentDataInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
            INSERT INTO iam_organization_projection (iam_organization_id, organization_code, organization_name, parent_iam_organization_id, organization_path, active, source_system, source_version, synced_at)
            VALUES ('ORG-LOCAL-IT', 'LOCAL-IT', '本地开发 / 信息技术部', NULL, '/ORG-LOCAL-IT', TRUE, 'LOCAL_DEV', '1', ?)
            ON DUPLICATE KEY UPDATE organization_name=VALUES(organization_name), active=TRUE, synced_at=VALUES(synced_at)""", now);
        user(now, "iam-u-local-requester", "local.requester", "本地开发提单人", "POS-LOCAL-REQUESTER", "业务系统使用人");
        user(now, "iam-u-local-first-line", "local.firstline", "本地一线工程师", "POS-LOCAL-FIRST-LINE", "一线服务台工程师");
        user(now, "iam-u-local-service-manager", "local.manager", "本地服务经理", "POS-LOCAL-MANAGER", "服务台经理");
        user(now, "iam-u-local-admin", "local.admin", "本地开发管理员", "POS-LOCAL-ADMIN", "本地平台管理员");
        backofficeRole(now, "iam-u-local-first-line", "ROLE_FIRST_LINE_SUPPORT");
        backofficeRole(now, "iam-u-local-service-manager", "ROLE_SERVICE_MANAGER");
        backofficeRole(now, "iam-u-local-service-manager", "ROLE_FIRST_LINE_SUPPORT");
        backofficeRole(now, "iam-u-local-admin", "ROLE_FIRST_LINE_SUPPORT");
        backofficeRole(now, "iam-u-local-admin", "ROLE_SECOND_LINE_SUPPORT");
        backofficeRole(now, "iam-u-local-admin", "ROLE_SERVICE_MANAGER");
        backofficeRole(now, "iam-u-local-admin", "ROLE_PLATFORM_ADMIN");
        backofficeRole(now, "iam-u-local-admin", "ROLE_AUDITOR");
        catalog(now, "SC-ERP-PERFORMANCE", "业务系统 - 页面性能问题", "页面加载慢、查询超时或操作卡顿。", "[\"INCIDENT\"]");
        catalog(now, "SC-ACCESS-REQUEST", "账号与权限 - 角色申请", "申请业务系统访问权限或角色变更。", "[\"ACCESS_REQUEST\"]");
        jdbc.update("""
            INSERT INTO service_catalog_dictionary (code, name, publication_status, version, updated_at) VALUES ('AFFECTED_SYSTEM', '影响系统', 'PUBLISHED', 1, ?)
            ON DUPLICATE KEY UPDATE publication_status='PUBLISHED', updated_at=VALUES(updated_at)""", now);
        option("ERP", "ERP", 1); option("FINANCE", "财务共享", 2); option("NUCLEAR_E", "核协E+", 3);
        field("SC-ERP-PERFORMANCE", "affected_system", "影响系统", "SINGLE_SELECT", true, 100, "AFFECTED_SYSTEM", 1);
        field("SC-ERP-PERFORMANCE", "affected_page", "受影响页面 / 模块", "TEXT", false, 200, null, 2);
        field("SC-ACCESS-REQUEST", "requested_role", "申请角色", "TEXT", true, 100, null, 1);
        tag("#页面卡顿", "页面卡顿"); tag("#ERP", "ERP"); tag("#账号权限", "账号权限");
    }

    private void user(Timestamp now, String iamUserId, String loginName, String displayName, String positionId, String positionName) {
        jdbc.update("""
            INSERT INTO iam_user_projection (iam_user_id, login_name, display_name, active, source_system, source_version, synced_at)
            VALUES (?, ?, ?, TRUE, 'LOCAL_DEV', '1', ?)
            ON DUPLICATE KEY UPDATE login_name=VALUES(login_name), display_name=VALUES(display_name), active=TRUE, synced_at=VALUES(synced_at)""", iamUserId, loginName, displayName, now);
        jdbc.update("""
            INSERT INTO iam_user_organization_position_projection (iam_user_id, iam_organization_id, iam_position_id, position_name, is_primary, active, source_system, source_version, synced_at)
            VALUES (?, 'ORG-LOCAL-IT', ?, ?, TRUE, TRUE, 'LOCAL_DEV', '1', ?)
            ON DUPLICATE KEY UPDATE position_name=VALUES(position_name), is_primary=TRUE, active=TRUE, synced_at=VALUES(synced_at)""", iamUserId, positionId, positionName, now);
    }

    private void backofficeRole(Timestamp now, String iamUserId, String roleCode) {
        jdbc.update("""
            INSERT INTO platform_backoffice_user (iam_user_id, enabled, version, created_by_iam_user_id, updated_by_iam_user_id, created_at, updated_at)
            VALUES (?, TRUE, 1, 'LOCAL_DEV', 'LOCAL_DEV', ?, ?)
            ON DUPLICATE KEY UPDATE enabled=TRUE, updated_by_iam_user_id='LOCAL_DEV', updated_at=VALUES(updated_at)""", iamUserId, now, now);
        jdbc.update("""
            INSERT INTO platform_backoffice_user_role (iam_user_id, role_code, active, granted_by_iam_user_id, granted_at, revoked_by_iam_user_id, revoked_at)
            VALUES (?, ?, TRUE, 'LOCAL_DEV', ?, NULL, NULL)
            ON DUPLICATE KEY UPDATE active=TRUE, granted_by_iam_user_id='LOCAL_DEV', granted_at=VALUES(granted_at), revoked_by_iam_user_id=NULL, revoked_at=NULL""",
            iamUserId, roleCode, now);
    }

    private void catalog(Timestamp now, String id, String name, String description, String types) {
        jdbc.update("""
            INSERT INTO service_catalog_item (id, name, description, publication_status, supported_ticket_types, version, published_at, created_at, updated_at)
            VALUES (?, ?, ?, 'PUBLISHED', ?, 1, ?, ?, ?)
            ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), publication_status='PUBLISHED', supported_ticket_types=VALUES(supported_ticket_types), updated_at=VALUES(updated_at)""", id, name, description, types, now, now, now);
    }
    private void option(String code, String label, int order) { jdbc.update("""
            INSERT INTO service_catalog_dictionary_option (dictionary_code, option_code, option_label, enabled, sort_order) VALUES ('AFFECTED_SYSTEM', ?, ?, TRUE, ?)
            ON DUPLICATE KEY UPDATE option_label=VALUES(option_label), enabled=TRUE, sort_order=VALUES(sort_order)""", code, label, order); }
    private void field(String item, String code, String label, String type, boolean required, Integer max, String dictionary, int order) { jdbc.update("""
            INSERT INTO service_catalog_form_field (catalog_item_id, field_code, field_label, field_type, required, max_length, dictionary_code, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE field_label=VALUES(field_label), field_type=VALUES(field_type), required=VALUES(required), max_length=VALUES(max_length), dictionary_code=VALUES(dictionary_code), sort_order=VALUES(sort_order)""", item, code, label, type, required, max, dictionary, order); }
    private void tag(String name, String label) { jdbc.update("""
            INSERT INTO service_catalog_tag (tag_name, tag_label, enabled) VALUES (?, ?, TRUE)
            ON DUPLICATE KEY UPDATE tag_label=VALUES(tag_label), enabled=TRUE""", name, label); }
}
