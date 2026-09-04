<script setup lang="ts">
import { onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { Editor, EditorContent } from '@tiptap/vue-3'
import { Extension, Node } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import { TextStyle } from '@tiptap/extension-text-style'

const props = withDefaults(defineProps<{ modelValue: string; disabled?: boolean; ariaLabel?: string; allowImages?: boolean }>(), {
  ariaLabel: '问题现象或服务说明',
  allowImages: true,
})
const emit = defineEmits<{ 'update:modelValue': [value: string]; 'plain-text-change': [value: string]; 'request-image': [] }>()
const blockedContentNotice = ref('')
function rejectFiles(count: number | undefined): boolean {
  if (props.allowImages || !count) return false
  blockedContentNotice.value = '当前编辑区仅支持文字，不支持粘贴或拖入图片、文件。请在工单附件区处理附件。'
  return true
}
const fontSizes = [{ value: '12', label: '小号 12px' }, { value: '14', label: '正文 14px' }, { value: '16', label: '大号 16px' }, { value: '18', label: '特大 18px' }]
const RestrictedFontSize = Extension.create({
  name: 'restrictedFontSize',
  addGlobalAttributes() {
    return [{
      types: ['textStyle'],
      attributes: {
        fontSize: {
          default: null,
          parseHTML: (element: HTMLElement) => element.getAttribute('data-font-size'),
          renderHTML: (attributes: { fontSize?: string | null }) => attributes.fontSize ? { 'data-font-size': attributes.fontSize } : {},
        },
      },
    }]
  },
})
const ManagedImage = Node.create({
  name: 'managedImage', group: 'inline', inline: true, atom: true, selectable: true, draggable: false,
  addAttributes() {
    return {
      src: { default: null }, alt: { default: '' },
      pendingImage: {
        default: null,
        parseHTML: (element: HTMLElement) => element.getAttribute('data-pending-image'),
        renderHTML: (attributes: { pendingImage?: string | null }) => attributes.pendingImage ? { 'data-pending-image': attributes.pendingImage } : {},
      },
    }
  },
  parseHTML() { return props.allowImages ? [{ tag: 'img[src]' }] : [] },
  renderHTML({ HTMLAttributes }) { return ['img', HTMLAttributes] },
})

const editor = shallowRef(new Editor({
  content: props.modelValue,
  editable: !props.disabled,
  extensions: [StarterKit.configure({ link: false }), TextStyle, RestrictedFontSize, ...(props.allowImages ? [ManagedImage] : []), Link.configure({ openOnClick: false, autolink: false, linkOnPaste: true, HTMLAttributes: { target: null, rel: 'nofollow noopener noreferrer' } })],
  editorProps: {
    attributes: { class: 'ticket-rich-editor__content', 'aria-label': props.ariaLabel, role: 'textbox', 'aria-multiline': 'true' },
    handlePaste: (_view, event) => rejectFiles(event.clipboardData?.files.length),
    handleDrop: (_view, event) => rejectFiles(event.dataTransfer?.files.length),
    transformPastedHTML: (html) => {
      if (props.allowImages) return html
      const document = new DOMParser().parseFromString(html, 'text/html')
      const media = document.querySelectorAll('img, picture, video, audio, object, embed, iframe')
      if (media.length) blockedContentNotice.value = '已跳过图片或媒体，仅粘贴文字内容。当前接口不支持保存图片或文件。'
      media.forEach((node) => node.remove())
      return document.body.innerHTML
    },
  },
  onUpdate: ({ editor: current }) => {
    emit('update:modelValue', current.getHTML())
    emit('plain-text-change', current.getText())
  },
}))

watch(() => props.modelValue, (value) => {
  if (value !== editor.value.getHTML()) {
    editor.value.commands.setContent(value, { emitUpdate: false })
    emit('plain-text-change', editor.value.getText())
  }
})
watch(() => props.disabled, (disabled) => editor.value.setEditable(!disabled))
watch(() => props.ariaLabel, (label) => editor.value.setOptions({ editorProps: { attributes: { class: 'ticket-rich-editor__content', 'aria-label': label, role: 'textbox', 'aria-multiline': 'true' } } }))
watch(() => props.allowImages, (allowed) => {
  if (allowed) return
  const transaction = editor.value.state.tr
  editor.value.state.doc.descendants((node, position) => {
    if (node.type.name === 'managedImage') transaction.delete(transaction.mapping.map(position), transaction.mapping.map(position + node.nodeSize))
  })
  if (transaction.docChanged) editor.value.view.dispatch(transaction)
})
onBeforeUnmount(() => editor.value.destroy())

function setLink(): void {
  const entered = window.prompt('请输入 HTTP/HTTPS 链接')?.trim()
  if (!entered) return
  try {
    const url = new URL(entered)
    if (!['http:', 'https:'].includes(url.protocol)) throw new Error('unsupported protocol')
    editor.value.chain().focus().extendMarkRange('link').setLink({ href: url.toString() }).run()
  } catch {
    window.alert('仅允许 HTTP 或 HTTPS 链接。')
  }
}

function selectedFontSize(): string { return String(editor.value.getAttributes('textStyle').fontSize ?? '14') }
function setFontSize(value: string): void {
  if (!['12', '14', '16', '18'].includes(value)) return
  editor.value.chain().focus().setMark('textStyle', { fontSize: value }).run()
}
function requestImage(): void {
  if (!props.allowImages || props.disabled) return
  emit('request-image')
}
function insertPendingImage(token: string, previewUrl: string, filename: string): void {
  if (!props.allowImages || props.disabled) return
  editor.value.chain().focus().insertContent({ type: 'managedImage', attrs: { src: previewUrl, alt: filename, pendingImage: token } }).run()
}
function focus(): void { editor.value.commands.focus() }
defineExpose({ insertPendingImage, focus })
</script>

<template>
  <div class="ticket-rich-editor" :class="{ 'is-disabled': disabled }">
    <div class="ticket-rich-editor__toolbar" role="toolbar" aria-label="文本格式工具栏">
      <div class="ticket-rich-editor__toolbar-group" aria-label="文字格式">
        <el-button size="small" plain title="加粗" aria-label="加粗" :disabled="disabled" :class="{ 'is-active': editor.isActive('bold') }" @click="editor.chain().focus().toggleBold().run()"><b>B</b></el-button>
        <el-button size="small" plain title="斜体" aria-label="斜体" :disabled="disabled" :class="{ 'is-active': editor.isActive('italic') }" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></el-button>
        <el-select class="ticket-rich-editor__font-size" size="small" :model-value="selectedFontSize()" :disabled="disabled" aria-label="字号" @update:model-value="setFontSize(String($event))"><el-option v-for="item in fontSizes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      </div>
      <div class="ticket-rich-editor__toolbar-group" aria-label="段落格式">
        <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('bulletList') }" @click="editor.chain().focus().toggleBulletList().run()">• 列表</el-button>
        <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('orderedList') }" @click="editor.chain().focus().toggleOrderedList().run()">1. 列表</el-button>
        <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('codeBlock') }" @click="editor.chain().focus().toggleCodeBlock().run()">代码</el-button>
      </div>
      <div class="ticket-rich-editor__toolbar-group" aria-label="插入内容">
        <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('link') }" @click="setLink">链接</el-button>
        <el-button v-if="allowImages" size="small" plain :disabled="disabled" @click="requestImage">插入图片</el-button>
        <el-button v-if="editor.isActive('link')" size="small" text :disabled="disabled" @click="editor.chain().focus().unsetLink().run()">取消链接</el-button>
      </div>
    </div>
    <EditorContent :editor="editor" />
    <p v-if="!allowImages && blockedContentNotice" class="ticket-rich-editor__notice" role="status">{{ blockedContentNotice }}</p>
  </div>
</template>

<style scoped>
.ticket-rich-editor__notice { margin: 0; padding: 8px 10px; border-top: 1px solid #e5eaf0; background: #fff8e8; color: #855c1d; font-size: 11px; line-height: 1.5; }
</style>
