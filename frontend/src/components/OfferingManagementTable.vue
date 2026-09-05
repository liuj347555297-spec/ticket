<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { ServiceCatalogItem } from '@/api/catalog'
import type { ServiceSystemCatalogMapping } from '@/api/service-systems'
import { paginateRows } from '@/utils/adminTable'

export interface OfferingManagementRow {
  mapping: ServiceSystemCatalogMapping
  item?: ServiceCatalogItem
  lifecycleStatus: string
  managed: boolean
  processConfigured?: boolean
}

const props = defineProps<{ rows: OfferingManagementRow[]; loading: boolean; ready: boolean; error: string; busy: boolean; canManage: boolean; canReadDesign: boolean; inherited: boolean }>()
const emit = defineEmits<{ view: [item: ServiceCatalogItem]; edit: [item: ServiceCatalogItem]; lifecycle: [item: ServiceCatalogItem, action: 'publish' | 'retire' | 'approve']; design: [item: ServiceCatalogItem]; mappings: []; create: [] }>()
const search = ref('')
const page = ref(1)
const pageSize = ref(10)
const filtered = computed(() => { const needle = search.value.trim().toLocaleLowerCase(); return needle ? props.rows.filter(({ item, mapping }) => `${item?.name ?? ''} ${item?.code ?? mapping.serviceCatalogItemId}`.toLocaleLowerCase().includes(needle)) : props.rows })
const sliced = computed(() => paginateRows(filtered.value, page.value, pageSize.value))
watch(() => [search.value, props.rows.length], () => { page.value = 1 })
const typeText = (type?: ServiceCatalogItem['ticketType']) => type ? ({ INCIDENT: '故障报修', ACCESS_REQUEST: '账号权限', SERVICE_REQUEST: '服务请求', PROBLEM: '问题管理', CHANGE: '变更' })[type] : '未知'
const statusText = (status: string) => ({ DRAFT: '草稿', PENDING_REVIEW: '待复核', PUBLISHED: '已发布', RETIRED: '已停用', REJECTED: '已驳回' })[status] ?? status
const statusType = (status: string): 'success' | 'warning' | 'info' | 'danger' => status === 'PUBLISHED' ? 'success' : status === 'PENDING_REVIEW' ? 'warning' : status === 'REJECTED' ? 'danger' : 'info'
</script>

<template>
  <section class="offering-table" aria-labelledby="offering-management-title">
    <header class="offering-table__heading"><div><h3 id="offering-management-title">工单服务</h3><p>{{ inherited ? '当前模块未配置启用关联，以下为系统级继承服务。' : '显示当前系统 / 模块范围的真实关联。' }}</p></div><div><el-input v-model="search" clearable placeholder="搜索服务名称或编码" /><el-button v-if="canManage" :disabled="busy" @click="emit('mappings')">关联已有服务</el-button><el-button v-if="canReadDesign" type="primary" :disabled="busy" @click="emit('create')">＋ 新建工单设计</el-button></div></header>
    <el-alert v-if="error" class="offering-table__alert" type="error" :closable="false" show-icon><template #title>工单服务加载失败</template>{{ error }}</el-alert>
    <div class="offering-table__scroll"><el-table v-loading="loading" :data="error || !ready ? [] : sliced.items" border stripe row-key="mapping.serviceCatalogItemId">
      <template #empty><el-empty v-if="!error && ready" description="当前范围尚无工单服务" :image-size="72" /></template>
      <el-table-column label="服务编码" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ row.item?.code ?? row.mapping.serviceCatalogItemId }}</template></el-table-column>
      <el-table-column label="服务名称" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ row.item?.name ?? '服务元数据不可用' }}</template></el-table-column>
      <el-table-column label="类型" width="110"><template #default="{ row }">{{ typeText(row.item?.ticketType) }}</template></el-table-column>
      <el-table-column label="发布状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.lifecycleStatus)" effect="light">{{ statusText(row.lifecycleStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="表单版本" width="110"><template #default="{ row }">{{ row.item?.publishedVersion ? `v${row.item.publishedVersion}` : '尚未发布' }}</template></el-table-column>
      <el-table-column label="流程配置" width="110"><template #default="{ row }"><el-tag :type="row.processConfigured === true ? 'success' : 'info'" effect="plain">{{ row.processConfigured === true ? '已配置' : row.processConfigured === false ? '待配置' : '进入查看' }}</el-tag></template></el-table-column>
      <el-table-column label="默认入口" width="100" align="center"><template #default="{ row }"><el-tag v-if="row.mapping.isDefault" type="warning" effect="plain">默认</el-tag><span v-else>否</span></template></el-table-column>
      <el-table-column fixed="right" label="操作" width="230"><template #default="{ row }"><template v-if="row.item"><el-button link type="primary" :disabled="busy" @click="emit('view', row.item)">查看</el-button><el-button v-if="canManage && row.managed && ['DRAFT','REJECTED'].includes(row.lifecycleStatus)" link type="primary" :disabled="busy" @click="emit('edit', row.item)">编辑草稿</el-button><el-button v-else-if="canReadDesign" link type="primary" :disabled="busy" @click="emit('design', row.item)">表单流程</el-button><el-dropdown trigger="click" :disabled="busy" @command="(command: string) => command === 'design' ? emit('design', row.item) : command === 'mappings' ? emit('mappings') : emit('lifecycle', row.item, command as 'publish' | 'retire' | 'approve')"><el-button link type="primary">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="canReadDesign" command="design">表单与流程</el-dropdown-item><el-dropdown-item v-if="canManage" command="mappings">关联 / 取消关联</el-dropdown-item><el-dropdown-item v-if="canManage && row.managed && row.lifecycleStatus === 'DRAFT'" command="publish" divided>提交发布</el-dropdown-item><el-dropdown-item v-if="canManage && row.managed && row.lifecycleStatus === 'PENDING_REVIEW'" command="approve" divided>复核发布</el-dropdown-item><el-dropdown-item v-if="canManage && row.managed && row.lifecycleStatus === 'PUBLISHED'" command="retire" divided>停用服务</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template><span v-else class="offering-table__unavailable">仅保留关联</span></template></el-table-column>
    </el-table></div>
    <footer class="offering-table__pagination"><span>共 {{ filtered.length }} 条</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]" :total="filtered.length" layout="sizes, prev, pager, next" background /></footer>
  </section>
</template>

<style scoped>
.offering-table{box-sizing:border-box;width:100%;max-width:100%;min-width:0;padding:18px;border:1px solid #dfe7f0;border-radius:6px;background:#fff}.offering-table__heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:14px}.offering-table__heading h3{margin:0 0 5px;color:#324f6d;font-size:16px}.offering-table__heading p{margin:0;color:#7c8fa4;font-size:12px}.offering-table__heading>div:last-child{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}.offering-table__heading .el-input{width:230px}.offering-table__alert{margin-bottom:12px}.offering-table__scroll{width:100%;max-width:100%;min-width:0;overflow-x:auto}.offering-table__scroll :deep(.el-table){min-width:1080px}.offering-table__unavailable{color:#9aa8b8;font-size:12px}.offering-table__pagination{display:flex;align-items:center;justify-content:flex-end;gap:16px;margin-top:16px;color:#8494a6;font-size:12px}@media(max-width:720px){.offering-table__heading{flex-direction:column}.offering-table__heading>div:last-child,.offering-table__heading .el-input,.offering-table__heading .el-button{width:100%}.offering-table__pagination{align-items:flex-end;flex-direction:column}.offering-table__pagination :deep(.el-pagination){justify-content:flex-end;max-width:100%;overflow-x:auto}}
</style>
