# BPMN 与多表单设计平台：需求、选型与实施设计

## 1. 需求与现状结论

用户要求新建、受理/处理工单展示 BPMN 设计预览；可拖拽设计流程与表单；一条流程可使用多种表单，一个审批节点可绑定多张表单；现有业务控件可注册复用。保持文档先行、多代理分工开发，不迁移到另一套流程引擎。

现有 ManagedFormConfiguration 将目录、表单身份、草稿版本和发布修订混为一个聚合；建单只读取一份表单，处理页仍按固定动作保存文本。当前流程图是节点轨道而非 XML/DI。Flowable 虽支持复杂 BPMN，项目的主工单执行适配器却依赖固定节点和单活动任务，因此任意部署设计器 XML 会破坏业务状态、鉴权与任务关联。

## 2. 选型

采用 bpmn-js Modeler/Viewer + 本项目 Vue 业务表单设计器及白名单控件注册表，保留 Flowable 7.2。bpmn-js 提供成熟 BPMN 拖拽、连线、导入导出和画布扩展；表单版本、节点多表单绑定、数据权限和发布治理由 ServiceHub 实现，不宣称画布自带业务执行能力。

| 候选 | 结论 |
| --- | --- |
| bpmn-js + 项目 Vue 表单设计器 | 首选；复用 Element Plus、富文本及既有 schema，避免引入第二个业务渲染框架 |
| bpmn-js + form-js | 成熟通用方案，但自定义控件使用 Preact，现有 Vue 业务组件需要适配 |
| bpmn-js + FormCreate | Vue3/ElementPlus 备选，需要 schema 适配及安全约束；高级收费功能不计入开源能力 |
| Flowable Design 商业版 | 一体化程度高，但须商业许可，不等同于引擎的开源授权 |

依据：[bpmn-js 官方](https://bpmn.io/toolkit/bpmn-js/)、[扩展机制](https://bpmn.io/toolkit/bpmn-js/walkthrough/)、[form-js](https://bpmn.io/toolkit/form-js/)、[Flowable 表单引用](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs/)、[Flowable Design 安装许可](https://documentation.flowable.com/latest/admin/installs/design-quick)。使用 bpmn-js 须保留版权许可及完整可见、不被覆盖的标识，遵循 [bpmn.io License](https://bpmn.io/license/)；不能当作普通 MIT 去除水印。

## 3. 目标领域模型

- FormDefinition：稳定 formId/code、名称、归属组织，与服务目录解耦。
- FormRevision：独立业务修订号与不可变字段/布局/控件版本快照；草稿保存仅改变并发版本，不增加业务修订号。
- WorkflowRevision：BPMN XML（含 DI）与流程业务修订，不能拿工单状态版本替代。
- NodeFormBinding：nodeId → 多个 formId + revision，带顺序、EDIT/READ_ONLY、完成必填标记。字段值未来按 formId/revision/fieldCode 隔离，不能扁平合并覆盖。
- CatalogReleaseBundle：一次发布冻结流程修订、全部表单修订和节点绑定。运行实例冻结发布包，历史工单不随设计修改漂移。
- TicketFormSubmission：按任务/表单修订保存提交、审批数据快照与并发版本。服务端逐字段计算 HIDDEN/READ/WRITE/REQUIRED。

原生 flowable:formKey 是单个引用，不是多表单数组；一个节点多表单由平台有序绑定清单实现，后续运行适配器解析发布包，不在浏览器拼任意执行表达式。

## 4. 本批可验收边界（Phase A）

1. 新建预览当前平台已部署生命周期；处理预览实例冻结的实际定义版本，真实活动节点高亮。无 DI 的旧定义仅生成展示坐标，不重新部署、不回写原定义；响应明确 AUTHORED/GENERATED 与只读展示投影。缺历史快照返回不可用，禁止偷偷用 latest。
2. 新增“流程与表单设计”工作台，提供 BPMN 拖拽、选择节点、编辑名称、撤销/重做、XML 导入导出、只读预览。
3. 一个设计包可包含多个独立 formId 的多份修订；DRAFT 可改，FROZEN 是不可变的**设计快照**，不等于业务发布。可复制为下一修订，旧绑定不自动升级。
4. 表单控件库/画布/属性/预览，拖拽与键盘上移下移；节点绑定多个明确修订并排序，配置可编辑/只读及完成必填。导入已有服务表单生成新设计修订，不覆盖旧目录配置。
5. 服务端保存设计包草稿与并发版本，按组织范围和角色校验、记录审计，不存表单填写值。页面能力明确 DRAFT_ONLY；本批没有把自由 BPMN 发布成工单运行流程。

## 5. 后续运行接入（Phase B/C，尚未交付）

- 发布请求/四眼审批/引用完整性校验与不可变 CatalogReleaseBundle，活动发布指针和草稿分离。
- 当前节点表单读取/保存/完成接口、字段数据权限、审批快照、多实例/并行任务的聚合推进；替换固定节点 singleResult 假设并保持旧实例兼容。
- 受控审批/消息/附件/CI/人员插件绑定，不允许任意 Java 类、scriptTask、delegateExpression、HTTP URL 或 SQL 由设计稿执行。
- 上述运行接口通过真实 MySQL/Flowable 集成测试与权限矩阵后，才能开启发布按钮。不能把“能保存设计稿”称为“多表单审批已上线”。

## 6. 控件注册、安全与迁移

编译时注册，配置仅引用白名单 controlId + controlVersion。首批：text、textarea、number、date、datetime、select、multiselect、boolean、richtext、tags、ci、attachment、iam、user、section。富文本复用 TicketRichTextEditor；附件、人员、CI、IAM 等业务控件在缺少运行上下文时显示受管预览/只读提示，不模拟上传或人员授权。控件表单不接受 JS、iframe、任意组件导入和网络 URL。

设计保存禁止未知字段/未知控件、系统保留字段覆盖、跨组织引用、重复 formId/revision/fieldCode、损坏绑定和超限 XML/schema；服务端安全解析 XML，拒绝 DTD/实体与执行扩展。预览只提取标准图形元素和 DI，不暴露执行表达式、候选人员、凭据或监听器。所有 JSON 值为配置元数据，不包含工单正文/填写值。

新设计存储并行于旧服务目录，不自动删除、改号或重写已有表单/工单。FROZEN 设计快照不可回写；草稿保存使用 If-Match/版本冲突保护，版本冲突保留本页编辑等待人工核对。

## 7. Phase A 契约与验收

- GET `/workflow/ticket-lifecycle/diagram`：当前受控主流程的展示 XML。
- GET `/tickets/{ticketId}/workflow/diagram`：经对象鉴权读取冻结定义；含 activeNodeIds/completedNodeIds、availability、layoutSource。
- GET/POST `/admin/design-studio/drafts`，GET/PUT `/admin/design-studio/drafts/{id}`：设计包草稿列表/创建/详情/乐观锁保存；`executionMode=DRAFT_ONLY`。
- 设计包保存体与前端 `api/designer.ts` 共享字段契约。formRevision.status=DRAFT/FROZEN，version 为整个包的乐观锁版本，不与表单修订混用。
- 验收：BPMN 真实 XML import、旧 DI 补全、历史定义隔离；表单拖拽/快照不可改/修订复制；同节点多表单独立绑定；禁止非法配置/跨组织写；保存后刷新可恢复；已运行工单不改变；构建、后端测试及浏览器验证。

## 8. 本批实施与验收记录（2026-09-03）

初始实现入口为 `/design-studio`；2026-09-04 已按文档 15 整合至 `/service-config` 的系统上下文，旧地址仅兼容重定向。使用 bpmn-js 18.27.0。当前为单流程基础建模子集；多实例、多泳池、数据对象、脚本与执行表达式不开放。BPMN 基础画布支持拖动、连线、改名、撤销重做和 XML 导入导出。只读画布居中并预留边距，保留完整可见的 bpmn.io 标识及分发许可文件。

V45 已在本地 MySQL 成功执行，仅新增设计包草稿表。没有部署设计稿，也未变更存量工单。后端保持本地 18080，前端 1525；该端口选择避免与本机另一项目的 8080 冲突。

| 验收项 | 实测结果 |
| --- | --- |
| 新建事件预览 | 选择本地 ERP 服务后，展示真实标准事件流程 v1，Viewer 无编辑工具栏 |
| 受理工单预览 | `TKT-20260831-000003` 展示实例冻结 v7；1 个活动节点（受理）、3 个已完成节点，没有替换成 v1 |
| BPMN 操作 | 浏览器拖动用户任务并改名“双表单受理审核”，保存后刷新保留 XML 和布局 |
| 多表单/修订 | 设计包“多表单审批设计验收”包含申请信息表 r1 冻结快照、r2 草稿，以及独立审核意见表 r1 |
| 单节点多表单 | `UserTask_Review` 绑定申请信息表 r1（只读）及审核意见表 r1（可填写、完成必填） |
| 跨页保存与快照 | 从流程切至表单页，r2 新增日期并保存为包并发版本 v2；重新读库确认 r1 仍 1 字段，r2 为 2 字段，绑定不升级 |
| 控件 | 已注册 15 种；浏览器验证拖入单行文本、字段属性编辑、冻结后禁止修改、复制下一修订；受管控件尚不执行真实附件/人员操作 |
| 前端 | `npm test` 23/23；`npm run build` 通过，BPMN 独立分包约 531 kB（gzip 151 kB） |
| 后端 | 本批 DesignStudio 测试 16/16；全量 129 个用例中 128 通过，1 个既有游标篡改用例失败，见下文 |

浏览器截图位于 `output/playwright/`：`design-studio-bpmn.png`、`design-studio-forms.png`、`create-bpmn-preview.png`、`ticket-bpmn-preview.png`。验收设计包 ID 为 `DS-bb07c9d0-a098-38c5-8fd5-391cab8f795b`，作为可打开的示例保留，执行模式始终为 `DRAFT_ONLY`。

全量测试未全绿：`TicketControllerTest.cursorRejectsTamperingSubjectAndFilterChangesAndPagesStably:253` 预期 400 实得 200。只读排查发现既有用例修改 Base64URL 签名末位，可能只改变未使用的填充位而仍解码成相同字节（本次 A → B）。本批没有修改该游标逻辑或通过反复重跑掩盖失败；后续需用确定的字节篡改用例，并明确是否拒绝非规范编码。

浏览器回归修复：节点绑定合法性直接从最新 BPMN XML 解析，不依赖画布挂载期间的临时节点事件缓存，避免切至表单页后误判全部绑定失效。身份/设计包切换使旧异步响应失效；不确定创建请求保留同一幂等键与请求体。

## 9. 下一批开发顺序及放行条件

1. 完善 Phase B 发布需求与接口设计：发布申请/审批状态、不可变发布包、目录选择发布包、冻结引用、撤回与回滚；先确认与旧目录发布规则的兼容关系。
2. 开发发布校验与受控 Flowable 部署：白名单节点、条件与人员策略，发布事务/失败补偿，历史实例继续使用原定义；设计冻结不等于发布。
3. 开发任务多表单读写：按任务、formId、修订隔离值，后台计算字段读写/必填权限，保存草稿与完成提交、乐观锁、审计及受管附件/人员/CI 适配。
4. 改造单任务假设并验证并行/会签/退回：任务列表代替 `singleResult`，明确工单聚合状态和多表单回退行为；增加跨组织与多角色权限矩阵测试。
5. 真实 MySQL/Flowable 端到端验收通过后才开启“发布并启用”，并在新建与处理页按实例发布包渲染多表单。此前不得将本批称为多表单审批运行已经交付。
