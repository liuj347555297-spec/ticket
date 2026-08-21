<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import { reportsApi, type OperationsReport, type OperationsReportQuery } from '@/api/reports'

function asCalendarDay(value: Date): string {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 10)
}

const now = new Date()
const sevenDaysAgo = new Date(now)
sevenDaysAgo.setDate(now.getDate() - 6)
const filters = ref<OperationsReportQuery>({ dateFrom: asCalendarDay(sevenDaysAgo), dateTo: asCalendarDay(now) })
const report = ref<OperationsReport>()
const source = ref<'api' | 'demo'>('api')
const loading = ref(false)
const error = ref('')

const maxTrend = computed(() => Math.max(1, ...(report.value?.trend.flatMap((item) => [item.createdCount, item.resolvedCount]) ?? [1])))
const maxStatus = computed(() => Math.max(1, ...(report.value?.statusDistribution.map((item) => item.count) ?? [1])))
const maxType = computed(() => Math.max(1, ...(report.value?.typeDistribution.map((item) => item.count) ?? [1])))
const metrics = computed(() => report.value ? [
  { label: '新建工单', value: displayMetric(report.value.summary.createdCount), note: '按创建时间统计', tone: 'blue' },
  { label: '已解决', value: displayMetric(report.value.summary.resolvedCount), note: `解决达标 ${percent(report.value.summary.resolutionComplianceRate)}`, tone: 'green' },
  { label: '解决时长 P50', value: duration(report.value.summary.resolutionP50Minutes), note: `响应达标 ${percent(report.value.summary.responseComplianceRate)}`, tone: 'blue' },
  { label: '解决时长 P90', value: duration(report.value.summary.resolutionP90Minutes), note: '按业务日历计算', tone: 'orange' },
] : [])

function displayMetric(value: number | undefined): string { return value === undefined ? '—' : String(value) }
function percent(value: number | undefined): string { return value === undefined ? '数据暂不可用' : `${value.toFixed(1)}%` }
function duration(minutes: number | undefined): string {
  if (minutes === undefined) return '—'
  if (minutes < 60) return `${minutes} 分钟`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`
}
function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value))
}
function validRange(): boolean {
  return Boolean(filters.value.dateFrom && filters.value.dateTo && filters.value.dateFrom <= filters.value.dateTo)
}
async function load(): Promise<void> {
  error.value = ''
  if (!validRange()) { error.value = '请选择有效的统计起止日期。'; return }
  loading.value = true
  try {
    const result = await reportsApi.operations({ ...filters.value })
    report.value = result.data
    source.value = result.source
  } catch (cause) {
    report.value = undefined
    error.value = cause instanceof ApiError ? cause.message : '运营报表暂不可用，请检查服务端授权或稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-heading">
    <div><h2>运营报表</h2><p>量、时效、队列与 SLA 风险均以服务端按当前 IAM 数据范围汇总。</p></div>
    <RouterLink class="button button--secondary" to="/sla-rules">SLA 规则管理入口</RouterLink>
  </div>

  <form class="panel report-filter" @submit.prevent="load">
    <label class="field"><span>统计开始</span><input v-model="filters.dateFrom" type="date" required /></label>
    <label class="field"><span>统计结束</span><input v-model="filters.dateTo" type="date" required /></label>
    <div class="report-filter__scope"><span>数据范围</span><b>{{ report?.scopeLabel ?? '由服务端按当前 IAM 身份确定' }}</b></div>
    <button class="button button--primary" type="submit" :disabled="loading">{{ loading ? '刷新中…' : '刷新报表' }}</button>
  </form>
  <p class="report-definition"><b>口径提示：</b>新建/已解决按所选时间范围的事件时间统计；响应、解决时长扣除已审批的 SLA 暂停时段；合规率以已完成或已到期计量对象计算。页面不提供组织切换，服务端会逐请求执行数据范围过滤。</p>
  <p v-if="source === 'demo'" class="demo-notice">开发演示数据：仅在 API 连接失败时展示，不代表真实组织、队列或 SLA 数据。</p>
  <p v-if="error" class="form-alert form-alert--error">{{ error }}</p>
  <p v-if="loading && !report" class="panel compact-loading">正在汇总当前权限范围内的运营数据…</p>

  <template v-if="report">
    <div class="metric-grid report-metrics"><article v-for="metric in metrics" :key="metric.label" class="metric-card"><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small :class="`text-${metric.tone}`">{{ metric.note }}</small></article></div>
    <section class="report-risk-strip" aria-label="SLA 风险摘要">
      <div><span>临近违约</span><b class="text-orange">{{ displayMetric(report.slaRisk.atRiskCount) }}</b><small>服务端 SLA 风险计数</small></div>
      <div><span>已违约</span><b class="text-red">{{ displayMetric(report.slaRisk.breachedCount) }}</b><small>需按服务规则复盘</small></div>
      <div><span>响应合规</span><b class="text-blue">{{ percent(report.summary.responseComplianceRate) }}</b><small>以已完成或到期对象计量</small></div>
      <div><span>在办工单</span><b>{{ displayMetric(report.summary.openCount) }}</b><small>当前可见范围</small></div>
    </section>

    <div class="report-grid report-grid--primary">
      <section class="panel report-panel"><div class="panel-header"><div><h3>工单趋势</h3><p>按日展示新建与解决数量。</p></div><span class="readonly-badge">{{ report.scopeLabel }}</span></div>
        <div v-if="report.trend.length" class="trend-chart" role="img" aria-label="工单新建和解决趋势图"><div v-for="point in report.trend" :key="point.date" class="trend-chart__item"><div class="trend-chart__bars"><span class="trend-chart__bar trend-chart__bar--created" :style="{ height: `${Math.max(6, point.createdCount / maxTrend * 100)}%` }" :title="`新建 ${point.createdCount}`"></span><span class="trend-chart__bar trend-chart__bar--resolved" :style="{ height: `${Math.max(6, point.resolvedCount / maxTrend * 100)}%` }" :title="`解决 ${point.resolvedCount}`"></span></div><small>{{ point.date }}</small></div></div><p v-else class="workflow-unavailable">当前统计区间的趋势汇总尚未完成，未以零值替代。</p>
        <div class="chart-legend"><span><i class="chart-legend__created"></i>新建</span><span><i class="chart-legend__resolved"></i>已解决</span></div>
      </section>
      <section class="panel report-panel"><div class="panel-header"><div><h3>状态与类型分布</h3><p>仅统计当前可见工单。</p></div></div>
        <div class="distribution-grid"><div><h4>状态</h4><div v-for="item in report.statusDistribution" :key="item.code" class="distribution-row"><span>{{ item.label }}</span><div><i :style="{ width: `${item.count / maxStatus * 100}%` }"></i></div><b>{{ item.count }}</b></div><p v-if="!report.statusDistribution.length" class="workflow-unavailable">状态分布暂不可用。</p></div><div><h4>类型</h4><div v-for="item in report.typeDistribution" :key="item.code" class="distribution-row"><span>{{ item.label }}</span><div><i class="distribution-row__type" :style="{ width: `${item.count / maxType * 100}%` }"></i></div><b>{{ item.count }}</b></div><p v-if="!report.typeDistribution.length" class="workflow-unavailable">类型分布暂不可用。</p></div></div>
      </section>
    </div>

    <section class="panel table-panel report-queue-panel"><div class="panel-header"><div><h3>队列负荷</h3><p>仅统计当前主体有权查看的队列；不展示容量配置或个人绩效。</p></div></div><div class="table-scroll"><table><thead><tr><th>队列</th><th>在办</th><th>待受理</th><th>临近违约</th><th>已违约</th></tr></thead><tbody><tr v-for="queue in report.queueLoads" :key="queue.queueId"><td><b>{{ queue.queueName }}</b><span class="table-subtext">{{ queue.queueId }}</span></td><td>{{ queue.openTicketCount }}</td><td>{{ queue.pendingAcceptanceCount }}</td><td><span class="tag tag--orange">{{ queue.atRiskCount }}</span></td><td><span class="tag tag--red">{{ queue.breachedCount }}</span></td></tr></tbody></table></div></section>
    <p class="report-footer">生成时间：{{ formatTime(report.generatedAt) }}。报表为汇总读模型，具体工单权限、审计记录与 SLA 计算以服务端实时结果为准。</p>
  </template>
</template>
