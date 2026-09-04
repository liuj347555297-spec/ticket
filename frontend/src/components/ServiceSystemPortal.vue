<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { serviceSystemApi, type ServiceSystem, type ServiceSystemModule } from '@/api/service-systems'
import type { ServiceCatalogItem } from '@/api/catalog'
import { servicePortalApi } from '@/api/service-portal'
import { useSessionStore } from '@/stores/session'
import { createPortalRequestGate, filterPortalSystems, groupPortalServices, portalTicketUrl, sortPortalModules } from '@/utils/servicePortal'

type LoadState = 'IDLE' | 'LOADING' | 'READY' | 'ERROR'
const session = useSessionStore()
const ready = computed(() => Boolean(session.currentUser) && !session.loading)
const systems = ref<ServiceSystem[]>([])
const modules = ref<ServiceSystemModule[]>([])
const services = ref<ServiceCatalogItem[]>([])
const systemCode = ref('')
const moduleCode = ref('')
const systemSearch = ref('')
const serviceSearch = ref('')
const systemState = ref<LoadState>('IDLE')
const moduleState = ref<LoadState>('IDLE')
const serviceState = ref<LoadState>('IDLE')
const systemGate = createPortalRequestGate()
const moduleGate = createPortalRequestGate()
const serviceGate = createPortalRequestGate()
let disposed = false
const visibleSystems = computed(() => filterPortalSystems(systems.value, systemSearch.value))
const currentSystem = computed(() => systems.value.find((system) => system.systemCode === systemCode.value))
const currentModule = computed(() => modules.value.find((module) => module.moduleCode === moduleCode.value))
const groups = computed(() => groupPortalServices(services.value, serviceSearch.value))
const resultCount = computed(() => groups.value.reduce((total, group) => total + group.items.length, 0))
const quickServices = computed(() => services.value.slice(0, 4))
const linkFor = (service: ServiceCatalogItem) => portalTicketUrl(systemCode.value, service.id, moduleCode.value || undefined)

async function loadServices(): Promise<void> {
  const isCurrent = serviceGate.next()
  const selectedSystem = systemCode.value
  const selectedModule = moduleCode.value
  services.value = []
  if (!ready.value || !selectedSystem) { serviceState.value = 'IDLE'; return }
  serviceState.value = 'LOADING'
  try {
    const result = await servicePortalApi.catalogItems(selectedSystem, selectedModule || undefined)
    if (!disposed && ready.value && isCurrent()) { services.value = result; serviceState.value = 'READY' }
  } catch {
    if (!disposed && ready.value && isCurrent()) serviceState.value = 'ERROR'
  }
}
async function loadModules(): Promise<void> {
  const isCurrent = moduleGate.next()
  const selectedSystem = systemCode.value
  modules.value = []
  if (!ready.value || !selectedSystem) { moduleState.value = 'IDLE'; return }
  moduleState.value = 'LOADING'
  try {
    const result = await serviceSystemApi.listModules(selectedSystem)
    if (!disposed && ready.value && isCurrent()) { modules.value = sortPortalModules(result); moduleState.value = 'READY' }
  } catch {
    if (!disposed && ready.value && isCurrent()) moduleState.value = 'ERROR'
  }
}
function selectSystem(value: string): void {
  if (!ready.value || !systems.value.some((system) => system.systemCode === value)) return
  moduleGate.invalidate()
  serviceGate.invalidate()
  systemCode.value = value
  moduleCode.value = ''
  serviceSearch.value = ''
  modules.value = []
  services.value = []
  void loadModules()
  void loadServices()
}
function selectModule(value: string): void {
  if (!ready.value || (value && !modules.value.some((module) => module.moduleCode === value))) return
  moduleCode.value = value
  serviceSearch.value = ''
  void loadServices()
}
async function loadSystems(): Promise<void> {
  if (!ready.value) return
  const isCurrent = systemGate.next()
  systemState.value = 'LOADING'
  try {
    const result = await serviceSystemApi.list()
    if (disposed || !ready.value || !isCurrent()) return
    systems.value = filterPortalSystems(result.items, '')
    systemState.value = 'READY'
    if (systems.value[0]) selectSystem(systems.value[0].systemCode)
  } catch {
    if (!disposed && ready.value && isCurrent()) systemState.value = 'ERROR'
  }
}
function resetScope(): void {
  systemGate.invalidate(); moduleGate.invalidate(); serviceGate.invalidate()
  systems.value = []; modules.value = []; services.value = []
  systemCode.value = ''; moduleCode.value = ''; systemSearch.value = ''; serviceSearch.value = ''
  systemState.value = 'IDLE'; moduleState.value = 'IDLE'; serviceState.value = 'IDLE'
  if (ready.value) void loadSystems()
}
watch(() => JSON.stringify([session.loading, session.currentUser?.iamUserId, session.currentUser?.organizationIamOrganizationId, session.source, session.authorization]), resetScope, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; systemGate.invalidate(); moduleGate.invalidate(); serviceGate.invalidate() })
</script>

<template>
  <div class="system-portal">
    <section v-if="serviceState === 'READY' && quickServices.length" class="panel system-portal__quick">
      <header><h3>快捷发起</h3><span>{{ currentSystem?.systemName }}{{ currentModule ? ` / ${currentModule.moduleName}` : '' }} · 当前服务入口</span></header>
      <div class="system-portal__quick-list">
        <RouterLink v-for="service in quickServices" :key="service.id" :to="linkFor(service)"><span aria-hidden="true">▣</span><b>{{ service.name }}</b><span aria-hidden="true">↗</span></RouterLink>
      </div>
    </section>

    <section class="panel system-portal__directory" aria-labelledby="portal-title">
      <header class="system-portal__heading"><div><h3 id="portal-title">系统服务目录</h3><p>先选择业务系统，再选择要发起的工单服务</p></div><label class="system-portal__search"><span class="system-portal__sr-only">搜索业务系统</span><input v-model="systemSearch" type="search" placeholder="搜索系统名称 / 编码" :disabled="systemState !== 'READY'" /></label></header>
      <div v-if="!ready" class="system-portal__state" role="status">{{ session.loading ? '正在确认当前身份…' : '身份信息不可用，请重新登录或刷新页面。' }}</div>
      <div v-else-if="systemState === 'LOADING' || systemState === 'IDLE'" class="system-portal__state" role="status">正在读取可见的业务系统…</div>
      <div v-else-if="systemState === 'ERROR'" class="system-portal__state system-portal__state--error" role="status"><b>系统目录加载失败</b><p>未使用演示服务，请稍后重试。</p><button type="button" @click="loadSystems">重试系统目录</button></div>
      <div v-else-if="!systems.length" class="system-portal__state" role="status"><b>当前没有可申请的业务系统</b><p>仅展示当前身份有权访问、已上架的系统。</p></div>
      <template v-else>
        <nav class="system-portal__systems" aria-label="选择业务系统"><button v-for="system in visibleSystems" :key="system.systemCode" type="button" :aria-pressed="systemCode === system.systemCode" :class="{ 'is-active': systemCode === system.systemCode }" @click="selectSystem(system.systemCode)">{{ system.systemName }}</button></nav>
        <p v-if="!visibleSystems.length" class="system-portal__filter-note" role="status">没有匹配的系统。<button type="button" @click="systemSearch = ''">清空系统搜索</button></p>
        <div class="system-portal__body">
          <aside class="system-portal__modules" aria-label="选择系统模块" :aria-busy="moduleState === 'LOADING'"><h4>系统模块</h4><button type="button" :class="{ 'is-active': !moduleCode }" :aria-pressed="!moduleCode" @click="selectModule('')">系统通用服务</button><button v-for="module in modules" :key="module.moduleCode" type="button" :class="{ 'is-active': moduleCode === module.moduleCode }" :aria-pressed="moduleCode === module.moduleCode" @click="selectModule(module.moduleCode)">{{ module.moduleName }}</button><p v-if="moduleState === 'LOADING'" role="status">模块加载中…</p><div v-else-if="moduleState === 'ERROR'" class="system-portal__module-error" role="status">模块读取失败<button type="button" @click="loadModules">重试模块</button></div><p v-else-if="moduleState === 'READY' && !modules.length">未配置独立模块</p></aside>
          <div class="system-portal__content" :aria-busy="serviceState === 'LOADING'">
            <div class="system-portal__context"><div><strong>{{ currentSystem?.systemName }}</strong><span>{{ currentModule?.moduleName ?? '系统通用服务' }}</span></div><label class="system-portal__search"><span class="system-portal__sr-only">搜索当前工单服务</span><input v-model="serviceSearch" type="search" placeholder="搜索工单服务" :disabled="serviceState !== 'READY'" /></label></div>
            <div v-if="serviceState === 'LOADING' || serviceState === 'IDLE'" class="system-portal__state" role="status">正在读取该范围内的已发布工单服务…</div>
            <div v-else-if="serviceState === 'ERROR'" class="system-portal__state system-portal__state--error" role="status"><b>工单服务加载失败</b><p>可以重试，也可以切换其他系统或模块。</p><button type="button" @click="loadServices">重试工单服务</button></div>
            <div v-else-if="!services.length" class="system-portal__state" role="status"><b>该范围暂未发布工单服务</b><p>可切换其他模块查看；没有模块映射时，服务端按系统通用配置返回。</p></div>
            <div v-else-if="!groups.length" class="system-portal__state" role="status"><b>没有匹配的工单服务</b><button type="button" @click="serviceSearch = ''">清空服务搜索</button></div>
            <template v-else><p class="system-portal__result" role="status">{{ resultCount }} 项可申请服务 · 每项服务使用各自发布的表单配置</p><section v-for="group in groups" :key="group.type" class="system-portal__group"><h4>{{ group.label }}<small>{{ group.items.length }}</small></h4><div class="system-portal__cards"><RouterLink v-for="service in group.items" :key="service.id" :to="linkFor(service)" class="system-portal__card"><span class="system-portal__card-icon" aria-hidden="true">▣</span><div><b>{{ service.name }}</b><p>{{ service.summary || '进入后查看该服务的申请要求与表单。' }}</p><small>已发布 · 表单 v{{ service.publishedVersion }}</small></div><span class="system-portal__card-arrow" aria-hidden="true">›</span></RouterLink></div></section></template>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>
