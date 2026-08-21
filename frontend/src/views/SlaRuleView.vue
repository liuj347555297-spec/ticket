<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import { reportsApi, type SlaRuleSummary } from '@/api/reports'

const rules = ref<SlaRuleSummary[]>([])
const source = ref<'api' | 'demo'>('api')
const loading = ref(false)
const error = ref('')

function duration(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟`
  const days = Math.floor(minutes / 1440)
  const hours = Math.floor((minutes % 1440) / 60)
  return days ? `${days} 天 ${hours} 小时` : `${hours} 小时 ${minutes % 60} 分`
}
function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value))
}
async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try {
    const result = await reportsApi.slaRules()
    rules.value = result.data.items
    source.value = result.source
  } catch (cause) {
    rules.value = []
    error.value = cause instanceof ApiError ? cause.message : 'SLA 规则暂不可用，请检查服务端授权或稍后重试。'
  } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <div class="detail-nav"><RouterLink to="/reports">← 返回运营报表</RouterLink></div>
  <div class="page-heading"><div><h2>SLA 规则</h2><p>仅展示服务端确认当前主体可查看的已发布规则版本。</p></div><button class="button button--secondary" type="button" disabled title="编辑接口需由服务端按平台管理员权限和审批流程开放">编辑规则（预留）</button></div>
  <p class="sla-admin-notice"><b>受控管理：</b>规则的新增、编辑、发布、停用与回滚必须由服务端执行管理员授权、双人复核、版本化和审计。前端不根据角色决定可编辑性，也不提交 SLA 时间、组织范围或发布状态。</p>
  <p v-if="source === 'demo'" class="demo-notice">开发演示数据：仅在 API 连接失败时展示，不代表生产 SLA 配置。</p>
  <p v-if="error" class="form-alert form-alert--error">{{ error }}</p>
  <p v-if="loading" class="panel compact-loading">正在读取已发布 SLA 规则…</p>
  <section v-else class="panel table-panel"><div class="panel-header"><div><h3>已发布规则</h3><p>规则详情用于运营理解；工单目标时间以服务端创建/动作时的版本快照为准。</p></div><span class="readonly-badge">只读</span></div><div class="table-scroll"><table><thead><tr><th>规则 / 目录</th><th>匹配优先级</th><th>响应目标</th><th>解决目标</th><th>风险阈值</th><th>服务日历</th><th>暂停状态</th><th>版本</th><th>状态</th></tr></thead><tbody><tr v-for="rule in rules" :key="rule.id"><td><b>{{ rule.name }}</b><span class="table-subtext">{{ rule.serviceCatalogItemName }} · {{ rule.id }}</span></td><td><span class="tag" :class="rule.priorityLabel === 'P1' ? 'tag--red' : 'tag--blue'">{{ rule.priorityLabel }}</span></td><td>{{ duration(rule.responseTargetMinutes) }}</td><td>{{ duration(rule.resolutionTargetMinutes) }}</td><td>{{ rule.riskThresholdMinutes ? duration(rule.riskThresholdMinutes) : '服务端未返回' }}</td><td>{{ rule.calendarName }}</td><td><span v-for="state in rule.pauseStatusLabels" :key="state" class="tag tag--muted sla-pause-tag">{{ state }}</span></td><td>v{{ rule.version }}<span class="table-subtext">{{ formatTime(rule.publishedAt) }}</span></td><td><span class="tag" :class="rule.enabled ? 'tag--green' : 'tag--muted'">{{ rule.enabled ? '生效中' : '已停用' }}</span></td></tr></tbody></table></div><p v-if="!rules.length" class="compact-empty">暂无当前数据范围内可查看的已发布 SLA 规则。</p></section>
</template>
