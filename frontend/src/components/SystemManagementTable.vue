<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { ServiceSystem } from '@/api/service-systems'
import { filterSystems, paginateRows } from '@/utils/adminTable'

const props = defineProps<{
  systems: ServiceSystem[]
  loading: boolean
  error: string
  warning?: string
  selectedCode?: string
  busy: boolean
  canManage: boolean
  canReadDesign: boolean
  serviceCounts?: Record<string, number>
}>()
const emit = defineEmits<{
  refresh: []
  create: []
  select: [system: ServiceSystem]
  view: [system: ServiceSystem]
  edit: [system: ServiceSystem]
  modules: [system: ServiceSystem]
  mappings: [system: ServiceSystem]
  design: [system: ServiceSystem]
  status: [system: ServiceSystem, action: 'publish' | 'retire']
}>()

const filters = reactive({ keyword: '', status: '', organization: '', owner: '' })
const page = ref(1)
const pageSize = ref(10)
const filtered = computed(() => filterSystems(props.systems, filters))
const sliced = computed(() => paginateRows(filtered.value, page.value, pageSize.value))
watch(() => [filters.keyword, filters.status, filters.organization, filters.owner, props.systems.length], () => { page.value = 1 })

function clearFilters(): void {
  filters.keyword = ''; filters.status = ''; filters.organization = ''; filters.owner = ''
}
function statusType(status: ServiceSystem['lifecycleStatus']): 'success' | 'info' | 'warning' {
  return status === 'PUBLISHED' ? 'success' : status === 'RETIRED' ? 'info' : 'warning'
}
function statusText(status: ServiceSystem['lifecycleStatus']): string {
  return status === 'PUBLISHED' ? '已上架' : status === 'RETIRED' ? '已下架' : '草稿'
}
function updatedText(system: ServiceSystem): string {
  if (!system.publishedAt) return `版本 v${system.version}`
  const value = new Date(system.publishedAt)
  return Number.isNaN(value.getTime()) ? system.publishedAt : value.toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <section class="management-table-panel" aria-labelledby="system-management-title">
    <header class="management-table-heading">
      <div><h3 id="system-management-title">系统管理</h3><p>系统列表 → 系统下工单服务 → 表单与流程配置。下架代替物理删除。</p></div>
      <el-button v-if="canManage" type="primary" :disabled="busy" @click="emit('create')">＋ 新建系统</el-button>
    </header>

    <el-form class="management-query" :inline="true" @submit.prevent>
      <el-form-item label="名称 / 编码"><el-input v-model="filters.keyword" clearable placeholder="请输入系统名称或编码" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态"><el-option label="草稿" value="DRAFT" /><el-option label="已上架" value="PUBLISHED" /><el-option label="已下架" value="RETIRED" /></el-select></el-form-item>
      <el-form-item label="所属组织"><el-input v-model="filters.organization" clearable placeholder="组织 ID" /></el-form-item>
      <el-form-item label="负责人"><el-input v-model="filters.owner" clearable placeholder="负责人 ID" /></el-form-item>
      <el-form-item class="management-query__actions"><el-button @click="clearFilters">重置</el-button><el-button :loading="loading" @click="emit('refresh')">刷新</el-button></el-form-item>
    </el-form>

    <el-alert v-if="error" class="management-alert" type="error" :closable="false" show-icon><template #title>系统列表加载失败</template>{{ error }}</el-alert>
    <el-alert v-else-if="warning" class="management-alert" type="warning" :closable="false" show-icon>{{ warning }}</el-alert>
    <div class="management-table-scroll">
      <el-table v-loading="loading" :data="error ? [] : sliced.items" border stripe row-key="systemCode" :row-class-name="({ row }: { row: ServiceSystem }) => row.systemCode === selectedCode ? 'is-current-system' : ''" @row-dblclick="(row: ServiceSystem) => emit('view', row)">
        <template #empty><el-empty v-if="!error" description="没有符合条件的系统" :image-size="72" /></template>
        <el-table-column prop="systemCode" label="系统编码" min-width="130" show-overflow-tooltip />
        <el-table-column prop="systemName" label="系统名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="owningOrganizationId" label="所属组织" min-width="150" show-overflow-tooltip />
        <el-table-column prop="ownerIamUserId" label="负责人" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ row.ownerIamUserId || '未配置' }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusType(row.lifecycleStatus)" effect="light">{{ statusText(row.lifecycleStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="服务数" width="90" align="center"><template #default="{ row }">{{ serviceCounts?.[row.systemCode] ?? '—' }}</template></el-table-column>
        <el-table-column label="更新时间" min-width="165"><template #default="{ row }">{{ updatedText(row) }}</template></el-table-column>
        <el-table-column fixed="right" label="操作" width="230">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="busy" @click="emit('select', row)">工单服务</el-button>
            <el-button v-if="canManage" link type="primary" :disabled="busy || row.lifecycleStatus !== 'DRAFT'" @click="emit('edit', row)">编辑</el-button>
            <el-button v-else link type="primary" :disabled="busy" @click="emit('view', row)">查看</el-button>
            <el-dropdown trigger="click" :disabled="busy" @command="(command: string) => command === 'view' ? emit('view', row) : command === 'modules' ? emit('modules', row) : command === 'mappings' ? emit('mappings', row) : command === 'design' ? emit('design', row) : emit('status', row, command as 'publish' | 'retire')">
              <el-button link type="primary">更多</el-button>
              <template #dropdown><el-dropdown-menu><el-dropdown-item command="view">查看详情</el-dropdown-item><el-dropdown-item v-if="canManage" command="modules">模块管理</el-dropdown-item><el-dropdown-item v-if="canManage" command="mappings">关联服务</el-dropdown-item><el-dropdown-item v-if="canReadDesign" command="design">新建工单设计</el-dropdown-item><el-dropdown-item v-if="canManage && row.lifecycleStatus === 'DRAFT'" command="publish" divided>上架系统</el-dropdown-item><el-dropdown-item v-if="canManage && row.lifecycleStatus === 'PUBLISHED'" command="retire" divided>下架系统</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <footer class="management-pagination"><span>共 {{ filtered.length }} 条</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :page-sizes="[10, 20, 50]" :total="filtered.length" layout="sizes, prev, pager, next" background /></footer>
  </section>
</template>

<style scoped>
.management-table-panel{box-sizing:border-box;width:100%;max-width:100%;min-width:0;padding:18px;border:1px solid #dfe7f0;border-radius:6px;background:#fff}.management-table-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:16px}.management-table-heading h3{margin:0 0 5px;color:#324f6d;font-size:16px}.management-table-heading p{margin:0;color:#7c8fa4;font-size:12px}.management-query{display:flex;align-items:flex-start;padding:14px 14px 0;margin-bottom:14px;border:1px solid #e2e9f1;border-radius:5px;background:#f8fafc}.management-query :deep(.el-form-item){margin-right:14px;margin-bottom:14px}.management-query :deep(.el-input){width:190px}.management-query :deep(.el-select){width:145px}.management-query__actions{margin-left:auto}.management-alert{margin-bottom:12px}.management-table-scroll{width:100%;max-width:100%;min-width:0;overflow-x:auto}.management-table-scroll :deep(.el-table){min-width:1120px}.management-table-scroll :deep(.is-current-system td.el-table__cell){background:#eef6ff}.management-pagination{display:flex;align-items:center;justify-content:flex-end;gap:16px;margin-top:16px;color:#8494a6;font-size:12px}@media(max-width:720px){.management-table-heading{flex-direction:column}.management-table-heading .el-button{width:100%}.management-query{display:grid}.management-query :deep(.el-form-item){display:grid;margin-right:0}.management-query :deep(.el-input),.management-query :deep(.el-select){width:100%}.management-query__actions{margin-left:0}.management-pagination{align-items:flex-end;flex-direction:column}.management-pagination :deep(.el-pagination){justify-content:flex-end;max-width:100%;overflow-x:auto}}
</style>
