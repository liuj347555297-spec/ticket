<script setup lang="ts">
import { onBeforeUnmount, shallowRef, watch } from 'vue'
import { Editor, EditorContent } from '@tiptap/vue-3'
import { Extension } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import { TextStyle } from '@tiptap/extension-text-style'

const props = defineProps<{ modelValue: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string]; 'plain-text-change': [value: string]; 'request-image': [] }>()
const fontSizes = [{ value: '12', label: '小（12）' }, { value: '14', label: '常规（14）' }, { value: '16', label: '大（16）' }, { value: '18', label: '特大（18）' }]
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

const editor = shallowRef(new Editor({
  content: props.modelValue,
  editable: !props.disabled,
  extensions: [StarterKit.configure({ link: false }), TextStyle, RestrictedFontSize, Link.configure({ openOnClick: false, autolink: false, linkOnPaste: true, HTMLAttributes: { target: null, rel: 'nofollow noopener noreferrer' } })],
  editorProps: { attributes: { class: 'ticket-rich-editor__content', 'aria-label': '问题现象或服务说明' } },
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

function selectedFontSize(): string { return String(editor.value.getAttributes('textStyle').fontSize ?? '') }
function setFontSize(value: string): void {
  if (!['12', '14', '16', '18'].includes(value)) return
  editor.value.chain().focus().setMark('textStyle', { fontSize: value }).run()
}
</script>

<template>
  <div class="ticket-rich-editor" :class="{ 'is-disabled': disabled }">
    <div class="ticket-rich-editor__toolbar" role="toolbar" aria-label="文本格式工具栏">
      <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('bold') }" @click="editor.chain().focus().toggleBold().run()"><b>B</b></el-button>
      <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('italic') }" @click="editor.chain().focus().toggleItalic().run()"><i>I</i></el-button>
      <el-select class="ticket-rich-editor__font-size" :model-value="selectedFontSize()" :disabled="disabled" placeholder="字号" @update:model-value="setFontSize(String($event))"><el-option v-for="item in fontSizes" :key="item.value" :label="item.label" :value="item.value" /></el-select>
      <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('bulletList') }" @click="editor.chain().focus().toggleBulletList().run()">• 列表</el-button>
      <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('orderedList') }" @click="editor.chain().focus().toggleOrderedList().run()">1. 列表</el-button>
      <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('codeBlock') }" @click="editor.chain().focus().toggleCodeBlock().run()">代码</el-button>
      <el-button size="small" plain :disabled="disabled" :class="{ 'is-active': editor.isActive('link') }" @click="setLink">链接</el-button>
      <el-button size="small" plain :disabled="disabled" @click="emit('request-image')">插入图片</el-button>
      <el-button v-if="editor.isActive('link')" size="small" text :disabled="disabled" @click="editor.chain().focus().unsetLink().run()">取消链接</el-button>
    </div>
    <EditorContent :editor="editor" />
  </div>
</template>
