<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const label = computed(() => String(route.meta.label ?? '功能页面'))
const descriptions: Record<string, string> = {
  工单中心: '统一处理个人待办、队列工单和已参与工单。',
  新建工单: '使用服务目录驱动的结构化表单提交问题，并展示规则匹配的已解决案例。',
  知识库: '沉淀经审核发布的可复用解决方案，支持标签、检索和来源工单追溯。',
  服务配置: '管理服务目录、审批、流程版本、IAM 只读投影和消息通道。',
  运营报表: '按组织、服务、队列与 SLA 展示运行服务报告。',
  运行治理: '管理重大事件、值班容量、告警关联和数据恢复演练。',
}
</script>

<template>
  <div class="page-heading"><div><h2>{{ label }}</h2><p>{{ descriptions[label] }}</p></div></div>
  <section class="panel empty-state"><span class="empty-icon">◫</span><h3>功能页面骨架已就绪</h3><p>后续按已批准的需求、权限策略与后端接口契约逐项实现。</p></section>
</template>
