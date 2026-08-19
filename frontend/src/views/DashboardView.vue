<script setup lang="ts">
const cards = [
  { label: '待受理工单', value: '18', note: '较昨日 -3', tone: 'blue' },
  { label: '我的待办', value: '8', note: '4 件临近 SLA', tone: 'orange' },
  { label: '今日已解决', value: '46', note: '首次解决率 87.2%', tone: 'green' },
  { label: '重大事件', value: '1', note: 'P1 协同处理中', tone: 'red' },
]

const tickets = [
  ['INC-20260817-0421', 'ERP 采购订单页面加载缓慢', '页面卡顿', 'P1', '处理中', '00:38:12'],
  ['REQ-20260817-1098', '财务共享角色申请', '账号权限', 'P3', '待审批', '03:16:42'],
  ['INC-20260817-0417', '办公网络间歇中断', '网络故障', 'P2', '待受理', '01:28:09'],
]
</script>

<template>
  <div class="page-heading">
    <div><h2>今日服务概览</h2><p>实时掌握工单、时效和队列运行状态</p></div>
    <RouterLink class="button button--primary" to="/tickets/new">+ 新建工单</RouterLink>
  </div>
  <div class="metric-grid">
    <article v-for="card in cards" :key="card.label" class="metric-card">
      <span>{{ card.label }}</span><strong>{{ card.value }}</strong><small :class="`text-${card.tone}`">{{ card.note }}</small>
    </article>
  </div>
  <div class="content-grid">
    <section class="panel panel--wide"><div class="panel-header"><div><h3>我的待办</h3><p>按 SLA 剩余时间排序</p></div><RouterLink to="/tickets">查看全部</RouterLink></div>
      <div class="table-scroll"><table><thead><tr><th>编号</th><th>工单主题</th><th>类型</th><th>优先级</th><th>状态</th><th>剩余 SLA</th></tr></thead><tbody><tr v-for="ticket in tickets" :key="ticket[0]"><td class="ticket-id">{{ ticket[0] }}</td><td>{{ ticket[1] }}</td><td>{{ ticket[2] }}</td><td><span class="tag" :class="ticket[3] === 'P1' ? 'tag--red' : 'tag--blue'">{{ ticket[3] }}</span></td><td>{{ ticket[4] }}</td><td>{{ ticket[5] }}</td></tr></tbody></table></div>
    </section>
    <section class="panel"><div class="panel-header"><div><h3>运行提醒</h3><p>需要关注的服务风险</p></div></div>
      <ul class="notice-list"><li><b>ERP 查询慢关联事件</b><span>距离解决 SLA 还剩 38 分钟</span></li><li><b>网络运维组临近容量</b><span>在办 16 / 容量 18</span></li><li><b>知识复审待处理</b><span>12 篇知识将在本周到期</span></li></ul>
    </section>
  </div>
</template>
