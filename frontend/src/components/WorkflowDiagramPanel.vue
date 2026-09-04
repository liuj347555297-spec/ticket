<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { designerApi, type WorkflowDiagram } from '@/api/designer'
import { useSessionStore } from '@/stores/session'
import BpmnCanvas from '@/components/designer/BpmnCanvas.vue'
const props = withDefaults(defineProps<{ ticketId?: string; active?: boolean }>(), { active: true })
const session = useSessionStore()
const diagram = ref<WorkflowDiagram | null>(null), loading = ref(false), error = ref('')
let generation = 0, disposed = false
async function load(): Promise<void> {
  const current = ++generation
  diagram.value = null; error.value = ''
  if (!props.active) { loading.value = false; return }
  loading.value = true
  try {
    const result = props.ticketId ? await designerApi.ticketDiagram(props.ticketId) : await designerApi.lifecycleDiagram()
    if (!disposed && current === generation) diagram.value = result
  } catch { if (!disposed && current === generation) error.value = '流程图暂不可用或不在当前授权范围，请稍后重试。' }
  finally { if (!disposed && current === generation) loading.value = false }
}
watch(() => [props.ticketId, props.active, session.currentUser?.iamUserId, session.currentUser?.organizationIamOrganizationId], load, { immediate: true })
onBeforeUnmount(() => { disposed = true; generation++ })
</script>
<template>
  <section class="workflow-bpmn-panel" aria-label="BPMN 流程预览">
    <p v-if="loading" role="status">正在加载实际 BPMN 定义…</p>
    <div v-else-if="error" role="status"><p>{{ error }}</p><button class="button button--secondary" type="button" @click="load">重新加载</button></div>
    <template v-else-if="diagram?.availability === 'AVAILABLE' && diagram.bpmnXml">
      <header><b>{{ ticketId ? '本工单绑定的流程版本' : '当前标准事件流程' }} · v{{ diagram.version }}</b><small>{{ diagram.layoutSource === 'GENERATED' ? '旧定义未附布局，已生成展示坐标（未重新部署）' : '按 BPMN 设计布局显示' }}</small></header>
      <BpmnCanvas v-if="active" :xml="diagram.bpmnXml" read-only :active-node-ids="diagram.activeNodeIds" :completed-node-ids="diagram.completedNodeIds" />
      <p class="workflow-bpmn-hint">只读 BPMN 展示投影；不包含执行参数、候选人及审批变量，不具备部署或跳转能力。</p>
    </template>
    <p v-else-if="active" role="status">没有可用的流程定义快照；不会使用最新设计替代历史工单流程。</p>
  </section>
</template>
<style scoped>
.workflow-bpmn-panel { min-width:0; }.workflow-bpmn-panel > header{display:flex;flex-wrap:wrap;gap:8px;align-items:center;justify-content:space-between;margin-bottom:10px}.workflow-bpmn-panel b{font-size:13px;color:#345c7d}.workflow-bpmn-panel small,.workflow-bpmn-panel p{font-size:11px;color:#788b9e;line-height:1.6}.workflow-bpmn-hint{margin:8px 0}
</style>
