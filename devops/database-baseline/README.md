# ServiceHub MySQL V26 基线包

本目录是从已验证的 `V1`–`V26` Flyway 升级链生成的 **新环境初版包**，用于减少新建开发、测试或灾备演练库逐条执行历史迁移的成本。

| 文件 | 用途 | 边界 |
| --- | --- | --- |
| `servicehub-v34-schema.sql` | 当前应用业务表结构，等价于顺序执行 V1–V34 | 新建空库应使用此文件；不含 Flowable 引擎表、Flyway 历史表或数据 |
| `servicehub-v32-schema.sql` | V32 历史初版结构 | 仅用于仍需验证 V32 的存量演练；不得作为新环境默认初版 |
| `servicehub-v31-schema.sql` | V31 历史初版结构 | 仅用于仍需验证 V31 的存量演练；不得作为新环境默认初版 |
| `servicehub-v28-schema.sql` | V28 历史初版结构 | 仅用于仍需验证 V28 的存量演练；不得作为新环境默认初版 |
| `servicehub-v27-schema.sql` | V27 历史初版结构 | 仅用于仍需验证 V27 的存量演练；不得作为新环境默认初版 |
| `servicehub-v26-schema.sql` | V26 历史初版结构 | 仅用于仍需验证 V26 的存量演练；不得作为新环境默认初版 |
| `servicehub-v26-demo-data.sql` | 可选的本地演示基础数据 | 仅含合成 IAM 投影、后台角色、服务目录/表单字典、标签与消息路由；不含密码、附件、工单、审计或生产人员数据 |
| `flowable-7.2.0-mysql-schema.sql` | 锁定 Flowable 7.2.0 的引擎结构 | 仅 DDL；不含流程定义、运行/历史数据或身份数据；须先于首次应用启动导入 |

## 新库初始化

1. DBA 创建空库、创建最小权限运行账号和独立迁移账号；生产账号不得使用本地演示数据。
2. 导入 `servicehub-v34-schema.sql`，再导入与当前依赖版本严格匹配的 `flowable-7.2.0-mysql-schema.sql`。本地演示环境可再导入 `servicehub-v26-demo-data.sql`。
3. **仅对这个刚导入、且尚无 `flyway_schema_history` 的新库**，以受控部署参数执行一次 Flyway 基线登记：`baseline-on-migrate=true`、`baseline-version=34`。登记完成后恢复常规配置（`baseline-on-migrate=false`）。
4. 启动当前应用并执行健康检查。Flowable 表由独立变更流程管理：禁止在生产滚动发布时让应用自动建表或升级；升级 Flowable 时必须重新生成、评审并在隔离库验证相应版本的结构脚本。
5. 后续仅新增 `V35+` 迁移。已有库继续沿用 V1–V34 的历史链并前向升级。

## 不可做的事

- 不得把本基线导入已有 ServiceHub 库，也不得在已有 Flyway 历史的库上开启 `baseline-on-migrate`。
- 不得删除、重命名或改写 `backend/src/main/resources/db/migration/V1`–`V26`；它们仍是存量环境升级、审计和恢复所需的不可变证据。
- 不得将本地演示 IAM 投影或后台角色导入生产。生产身份与组织只由 IAM 同步写入。

## 再生成与验收

新建基线前，必须先在隔离 MySQL 中验证全部 Flyway 迁移、关键接口和 Flowable 版本。结构导出必须排除 `flyway_schema_history`、`ACT_*`、`FLW_*`、`IDM_*` 以及运行态/审计/附件数据；演示数据只允许白名单中的合成配置表。导出后应在全新隔离库完成“导入结构 → Flyway 基线登记 → 启动应用 → 健康检查”的回归。
