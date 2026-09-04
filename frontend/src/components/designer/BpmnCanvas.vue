<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Modeler from 'bpmn-js/lib/Modeler'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import type Canvas from 'diagram-js/lib/core/Canvas'
import type CommandStack from 'diagram-js/lib/command/CommandStack'
import type ElementRegistry from 'diagram-js/lib/core/ElementRegistry'
import type Modeling from 'bpmn-js/lib/features/modeling/Modeling'
import type { Element as BpmnElement } from 'bpmn-js/lib/model/Types'
import type { BpmnCanvasHandle, BpmnNodeSummary } from '@/bpmn/types'
import { validateBpmnXml } from '@/bpmn/safeXml'
import { translateBpmn } from '@/bpmn/translate'
import { designOnlyModule } from '@/bpmn/designOnlyModule'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn.css'

const props = withDefaults(defineProps<{
  xml: string
  readOnly?: boolean
  activeNodeIds?: string[]
  completedNodeIds?: string[]
}>(), { readOnly: false, activeNodeIds: () => [], completedNodeIds: () => [] })
const emit = defineEmits<{
  'update:xml': [xml: string]
  'select-node': [node: BpmnNodeSummary | null]
  'diagram-nodes': [nodes: BpmnNodeSummary[]]
  'dirty-change': [dirty: boolean]
}>()

const host = ref<HTMLDivElement | null>(null)
const loading = ref(false)
const ready = ref(false)
const error = ref('')
const selectedNode = ref<BpmnNodeSummary | null>(null)
const nodeName = ref('')
const canUndo = ref(false)
const canRedo = ref(false)
const zoomPercent = ref(100)
const hasRuntimeMarkers = computed(() => props.activeNodeIds.length > 0 || props.completedNodeIds.length > 0)

// Toolkit instances own DOM nodes and cyclic graphs; keep them out of Vue reactivity.
let toolkit: Modeler | NavigatedViewer | null = null
let toolkitReadOnly = false
let importQueue: Promise<void> = Promise.resolve()
let requestGeneration = 0
let changeVersion = 0
let emittedXml = ''
let loadedXml = ''
let disposed = false
let exportTimer: ReturnType<typeof setTimeout> | undefined
let resizeObserver: ResizeObserver | null = null
let markedIds = new Set<string>()

function isBpmnElement(value: unknown): value is BpmnElement {
  if (!value || typeof value !== 'object') return false
  const element = value as Record<string, unknown>
  return typeof element.id === 'string' && typeof element.type === 'string'
    && element.businessObject !== null && typeof element.businessObject === 'object'
}

function summarize(value: unknown): BpmnNodeSummary | null {
  if (!isBpmnElement(value) || value.type === 'label' || value.type === 'bpmn:Process'
    || value.type === 'bpmn:Collaboration') return null
  const businessObject: unknown = value.businessObject
  const name = (businessObject as Record<string, unknown>).name
  return { id: value.id, type: value.type, name: typeof name === 'string' ? name : '' }
}

function selectNode(element: unknown): void {
  selectedNode.value = summarize(element)
  nodeName.value = selectedNode.value?.name ?? ''
  emit('select-node', selectedNode.value)
}

function reportNodes(): void {
  if (!toolkit || !ready.value) return
  const registry = toolkit.get<ElementRegistry>('elementRegistry')
  const nodes = registry.getAll().map(summarize).filter((node): node is BpmnNodeSummary => node !== null)
  emit('diagram-nodes', nodes)
  if (selectedNode.value) selectNode(registry.get(selectedNode.value.id))
}

function updateHistory(): void {
  const stack = toolkit?.get<CommandStack>('commandStack', false)
  canUndo.value = !props.readOnly && !!stack?.canUndo()
  canRedo.value = !props.readOnly && !!stack?.canRedo()
}

function applyMarkers(): void {
  if (!toolkit || !ready.value) return
  const canvas = toolkit.get<Canvas>('canvas')
  const registry = toolkit.get<ElementRegistry>('elementRegistry')
  for (const id of markedIds) {
    if (!registry.get(id)) continue
    canvas.removeMarker(id, 'servicehub-bpmn-active')
    canvas.removeMarker(id, 'servicehub-bpmn-completed')
  }
  markedIds = new Set([...props.activeNodeIds, ...props.completedNodeIds])
  for (const id of props.completedNodeIds) {
    if (registry.get(id)) canvas.addMarker(id, 'servicehub-bpmn-completed')
  }
  for (const id of props.activeNodeIds) {
    if (registry.get(id)) canvas.addMarker(id, 'servicehub-bpmn-active')
  }
}

function fitViewport(): void {
  if (!toolkit || !ready.value || !host.value?.clientWidth || !host.value.clientHeight) return
  const canvas = toolkit.get<Canvas>('canvas')
  canvas.resized()
  const fitted = canvas.zoom('fit-viewport', { x: host.value.clientWidth / 2, y: host.value.clientHeight / 2 })
  // Leave breathing room around outermost events and center short legacy diagrams.
  zoomPercent.value = Math.round(canvas.zoom(fitted * 0.94) * 100)
}

function zoomBy(factor: number): void {
  if (!toolkit || !ready.value || loading.value) return
  const canvas = toolkit.get<Canvas>('canvas')
  zoomPercent.value = Math.round(canvas.zoom(Math.min(4, Math.max(0.2, canvas.zoom() * factor))) * 100)
}

function undo(): void {
  if (props.readOnly || !canUndo.value || loading.value) return
  toolkit?.get<CommandStack>('commandStack', false)?.undo()
}

function redo(): void {
  if (props.readOnly || !canRedo.value || loading.value) return
  toolkit?.get<CommandStack>('commandStack', false)?.redo()
}

function renameSelectedNode(name: string): void {
  if (props.readOnly || !toolkit || !selectedNode.value || !ready.value || loading.value) return
  const element = toolkit.get<ElementRegistry>('elementRegistry').get(selectedNode.value.id)
  if (!isBpmnElement(element)) return
  toolkit.get<Modeling>('modeling').updateLabel(element, name.trim().slice(0, 120))
}

async function getXml(): Promise<string> {
  await importQueue
  if (disposed || !toolkit || !ready.value || loading.value) throw new Error('流程图尚未就绪，暂时无法导出。')
  const current = toolkit
  const generation = requestGeneration
  // An edit may arrive while saveXML is serializing. Return only a current snapshot.
  for (let attempt = 0; attempt < 5; attempt++) {
    const version = changeVersion
    const result = await current.saveXML({ format: true })
    if (disposed || current !== toolkit || generation !== requestGeneration) {
      throw new Error('流程图已切换，请重新保存当前设计。')
    }
    if (version !== changeVersion) continue
    if (!result.xml) throw new Error('流程图导出失败，请重试。')
    validateBpmnXml(result.xml)
    return result.xml
  }
  throw new Error('流程仍在编辑中，请停止拖拽后再保存。')
}

function queueXmlUpdate(): void {
  clearTimeout(exportTimer)
  const generation = requestGeneration
  const version = changeVersion
  exportTimer = setTimeout(async () => {
    try {
      const xml = await getXml()
      if (disposed || generation !== requestGeneration || version !== changeVersion || props.readOnly) return
      emittedXml = xml
      loadedXml = xml
      emit('update:xml', xml)
    } catch (cause: unknown) {
      if (!disposed && generation === requestGeneration && version === changeVersion) {
        error.value = cause instanceof Error ? cause.message : '流程图导出失败，请重试。'
      }
    }
  }, 180)
}

function createToolkit(): void {
  toolkit?.destroy()
  if (!host.value) throw new Error('流程图容器尚未就绪。')
  toolkitReadOnly = props.readOnly
  const options = {
    container: host.value,
    additionalModules: [
      { translate: ['value', translateBpmn] },
      ...(props.readOnly ? [] : [designOnlyModule]),
    ],
  }
  toolkit = props.readOnly
    ? new NavigatedViewer(options)
    : new Modeler(options)
  const current = toolkit
  current.on('selection.changed', (event: { newSelection?: unknown[] }) => {
    if (current !== toolkit || !ready.value || loading.value || disposed) return
    selectNode(event.newSelection?.length === 1 ? event.newSelection[0] : null)
  })
  if (props.readOnly) {
    current.on('element.click', (event: { element?: unknown }) => {
      if (current === toolkit && ready.value && !loading.value && !disposed) selectNode(event.element)
    })
  }
  current.on('commandStack.changed', () => {
    if (current !== toolkit || props.readOnly || !ready.value || loading.value || disposed) return
    changeVersion++
    emit('dirty-change', true)
    error.value = ''
    reportNodes()
    updateHistory()
    queueXmlUpdate()
  })
  current.on('canvas.viewbox.changed', (event: { viewbox?: { scale?: number } }) => {
    if (current !== toolkit || disposed) return
    if (typeof event.viewbox?.scale === 'number') zoomPercent.value = Math.round(event.viewbox.scale * 100)
  })
}

function importXml(xml: string): Promise<boolean> {
  // A bad file must not cancel a pending edit export or destroy a valid canvas.
  try {
    validateBpmnXml(xml)
  } catch (cause: unknown) {
    if (!disposed) {
      error.value = cause instanceof Error ? cause.message : 'BPMN 文件不符合基础设计规则。'
      // A permission/mode change must never leave the previous editor interactive.
      // A normal failed file import in the same mode still preserves the live canvas.
      if (toolkit && toolkitReadOnly !== props.readOnly) {
        requestGeneration++
        clearTimeout(exportTimer)
        ready.value = false
        loading.value = false
        canUndo.value = false
        canRedo.value = false
        toolkit.detach()
      }
    }
    return Promise.resolve(false)
  }
  const hadValidDiagram = ready.value
  const generation = ++requestGeneration
  clearTimeout(exportTimer)
  loading.value = true
  ready.value = false
  error.value = ''
  canUndo.value = false
  canRedo.value = false
  selectNode(null)
  emit('diagram-nodes', [])
  let succeeded = false
  const operation = importQueue.then(async () => {
    if (disposed || generation !== requestGeneration) return
    let previousXml = loadedXml
    let importStarted = false
    try {
      if (toolkit && hadValidDiagram) {
        const snapshot = await toolkit.saveXML({ format: true })
        if (disposed || generation !== requestGeneration) return
        if (!snapshot.xml) throw new Error('无法保留当前图形，本次导入已取消。')
        previousXml = snapshot.xml
      }
      importStarted = true
      if (!toolkit || toolkitReadOnly !== props.readOnly) createToolkit()
      if (!toolkit) return
      const result = await toolkit.importXML(xml)
      if (disposed || generation !== requestGeneration) return
      if (result.warnings.length) {
        throw new Error(`流程图存在 ${result.warnings.length} 项无法完整解析的内容，请检查节点引用或 XML 兼容性。`)
      }
      loadedXml = xml
      markedIds.clear()
      ready.value = true
      reportNodes()
      updateHistory()
      applyMarkers()
      await nextTick()
      if (disposed || generation !== requestGeneration) return
      fitViewport()
      succeeded = true
      if (!props.readOnly) {
        emittedXml = xml
        emit('update:xml', xml)
      }
    } catch (cause: unknown) {
      if (disposed || generation !== requestGeneration) return
      const message = cause instanceof Error ? cause.message : '流程图加载失败，请检查 BPMN 文件后重试。'
      if (!importStarted && hadValidDiagram && toolkit) {
        ready.value = true
        reportNodes()
        updateHistory()
        error.value = `${message} 当前图形未被替换。`
        if (!props.readOnly) queueXmlUpdate()
        return
      }
      let restored = false
      if (toolkit && previousXml) {
        try {
          await toolkit.importXML(previousXml)
          if (disposed || generation !== requestGeneration) return
          loadedXml = previousXml
          markedIds.clear()
          ready.value = true
          reportNodes()
          updateHistory()
          applyMarkers()
          await nextTick()
          if (disposed || generation !== requestGeneration) return
          fitViewport()
          if (!props.readOnly) {
            emittedXml = previousXml
            emit('update:xml', previousXml)
          }
          restored = true
        } catch { /* Fall through to an explicit unavailable state if restoration failed. */ }
      }
      if (disposed || generation !== requestGeneration) return
      if (!restored) {
        toolkit?.clear()
        loadedXml = ''
        ready.value = false
        emit('diagram-nodes', [])
      }
      error.value = restored ? `${message} 已恢复导入前的图形与编辑内容（撤销历史已重建）。` : message
    } finally {
      if (!disposed && generation === requestGeneration) loading.value = false
    }
  })
  importQueue = operation.catch(() => undefined)
  return operation.then(() => succeeded)
}

watch(() => props.xml, (xml) => {
  if (!host.value || (ready.value && (xml === emittedXml || xml === loadedXml))) return
  void importXml(xml)
})
watch(() => props.readOnly, () => {
  if (host.value) void importXml(props.xml)
})
watch(() => [props.activeNodeIds, props.completedNodeIds], applyMarkers, { deep: true })

onMounted(() => {
  resizeObserver = new ResizeObserver(() => {
    if (!disposed && !loading.value) fitViewport()
  })
  if (host.value) resizeObserver.observe(host.value)
  void importXml(props.xml)
})

onBeforeUnmount(() => {
  disposed = true
  requestGeneration++
  clearTimeout(exportTimer)
  resizeObserver?.disconnect()
  // importXML is not abortable. Destroy after the serialized import has settled.
  const previous = toolkit
  toolkit = null
  previous?.detach()
  void importQueue.finally(() => previous?.destroy())
})

defineExpose<BpmnCanvasHandle>({ getXml, importXml, renameSelectedNode, fitViewport })
</script>

<template>
  <section class="servicehub-bpmn" :aria-busy="loading" :aria-label="readOnly ? 'BPMN 流程只读预览' : 'BPMN 流程设计器'">
    <div class="servicehub-bpmn__toolbar" role="toolbar" aria-label="流程图工具">
      <span class="servicehub-bpmn__mode">{{ readOnly ? 'BPMN 只读预览' : 'BPMN 基础拖拽设计 · 单流程' }}</span>
      <button type="button" :disabled="!ready || loading" title="缩小流程图" aria-label="缩小流程图" @click="zoomBy(1 / 1.2)">−</button>
      <span class="servicehub-bpmn__zoom" aria-live="off">{{ zoomPercent }}%</span>
      <button type="button" :disabled="!ready || loading" title="放大流程图" aria-label="放大流程图" @click="zoomBy(1.2)">＋</button>
      <button type="button" :disabled="!ready || loading" @click="fitViewport">适配画布</button>
      <template v-if="!readOnly">
        <button type="button" :disabled="!canUndo || loading" @click="undo">撤销</button>
        <button type="button" :disabled="!canRedo || loading" @click="redo">重做</button>
      </template>
    </div>
    <p v-if="loading" class="servicehub-bpmn__notice" role="status">正在加载流程图…</p>
    <div v-else-if="error" class="servicehub-bpmn__error" role="alert">
      <span>{{ error }}</span>
      <button v-if="!ready" type="button" @click="importXml(xml)">重新加载</button>
    </div>
    <div v-if="!readOnly" class="servicehub-bpmn__selection">
      <label>
        <span>节点名称</span>
        <input v-model="nodeName" :disabled="!selectedNode || !ready || loading" maxlength="120" placeholder="在画布中选择一个节点" @keydown.enter.prevent="renameSelectedNode(nodeName)" />
      </label>
      <button type="button" :disabled="!selectedNode || !ready || loading" @click="renameSelectedNode(nodeName)">应用名称</button>
      <span v-if="selectedNode" class="servicehub-bpmn__node-id">{{ selectedNode.id }}</span>
    </div>
    <div ref="host" class="servicehub-bpmn__host" :class="{ 'servicehub-bpmn__host--loading': loading || !ready, 'servicehub-bpmn__host--readonly': readOnly }" />
    <div class="servicehub-bpmn__footer">
      <span v-if="!readOnly">支持基础任务、网关、子流程和文字说明；暂不提供多实例、多泳池、数据对象或执行表达式。双击可改名，设计稿不部署。</span>
      <span v-else>可拖动画布、缩放查看；节点与连线来自 BPMN XML。</span>
      <span v-if="hasRuntimeMarkers" class="servicehub-bpmn__legend">
        <span><i class="servicehub-bpmn__dot--active" /> 当前活动节点</span>
        <span><i class="servicehub-bpmn__dot--completed" /> 已完成节点</span>
      </span>
    </div>
  </section>
</template>

<style scoped>
.servicehub-bpmn { min-width: 0; border: 1px solid #d9e3ef; border-radius: 8px; background: #fff; overflow: hidden; color: #33445b; }
.servicehub-bpmn__toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #e3eaf2; background: #f7faff; }
.servicehub-bpmn__mode { margin-right: auto; font-weight: 600; }
.servicehub-bpmn button { min-height: 32px; padding: 4px 10px; border: 1px solid #cbd8e8; border-radius: 4px; background: #fff; color: #284565; font: inherit; cursor: pointer; }
.servicehub-bpmn button:hover:not(:disabled) { border-color: #3c72bb; color: #245ca8; background: #f0f6ff; }
.servicehub-bpmn button:disabled { cursor: not-allowed; opacity: .5; }
.servicehub-bpmn button:focus-visible, .servicehub-bpmn input:focus-visible { outline: 2px solid #2f6fc0; outline-offset: 2px; }
.servicehub-bpmn__zoom { min-width: 44px; text-align: center; font-variant-numeric: tabular-nums; }
.servicehub-bpmn__notice, .servicehub-bpmn__error { margin: 0; padding: 10px 12px; font-size: 13px; }
.servicehub-bpmn__notice { color: #365d8c; background: #edf5ff; }
.servicehub-bpmn__error { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; background: #fff2ef; color: #a13a2b; }
.servicehub-bpmn__selection { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #e3eaf2; }
.servicehub-bpmn__selection label { display: flex; align-items: center; gap: 8px; }
.servicehub-bpmn__selection input { width: min(260px, 48vw); min-height: 32px; padding: 4px 8px; box-sizing: border-box; border: 1px solid #cbd8e8; border-radius: 4px; font: inherit; }
.servicehub-bpmn__node-id { color: #75849a; font-size: 12px; overflow-wrap: anywhere; }
.servicehub-bpmn__host { position: relative; width: 100%; height: 460px; min-height: 320px; background: #fff; }
.servicehub-bpmn__host--readonly { height: 320px; }
.servicehub-bpmn__host--loading { pointer-events: none; }
.servicehub-bpmn__host--loading :deep(.djs-element), .servicehub-bpmn__host--loading :deep(.djs-palette), .servicehub-bpmn__host--loading :deep(.djs-context-pad) { visibility: hidden; }
.servicehub-bpmn__footer { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; padding: 9px 12px; border-top: 1px solid #e3eaf2; color: #6c7e93; font-size: 12px; line-height: 1.6; }
.servicehub-bpmn__legend, .servicehub-bpmn__legend > span { display: inline-flex; align-items: center; gap: 6px; }
.servicehub-bpmn__legend { flex-wrap: wrap; gap: 12px; }
.servicehub-bpmn__legend i { display: inline-block; width: 10px; height: 10px; border-radius: 50%; }
.servicehub-bpmn__dot--active { background: #d38614; }
.servicehub-bpmn__dot--completed { background: #29845f; }
.servicehub-bpmn :deep(.servicehub-bpmn-completed .djs-visual > :is(rect, circle, polygon, path):first-child) { stroke: #27855e !important; stroke-width: 3px !important; }
.servicehub-bpmn :deep(.servicehub-bpmn-active .djs-visual > :is(rect, circle, polygon, path):first-child) { stroke: #cf800f !important; stroke-width: 4px !important; stroke-dasharray: 7 3 !important; }
@media (max-width: 640px) {
  .servicehub-bpmn__host:not(.servicehub-bpmn__host--readonly) { height: 360px; }
  .servicehub-bpmn__mode { width: 100%; }
  .servicehub-bpmn button { min-height: 40px; }
  .servicehub-bpmn__selection input { min-height: 40px; }
}
</style>
