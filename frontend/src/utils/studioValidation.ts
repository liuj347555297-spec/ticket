import type { NodeFormBinding, StudioFormRevision } from '../api/designer'
export function validateFormIdentities(forms: readonly StudioFormRevision[]): string[] {
  const errors: string[] = [], revisions = new Set<string>(), codes = new Map<string, string>(), owners = new Map<string, string>()
  for (const form of forms) {
    if (!form.name.trim() || form.name.length > 120 || /[<>\x00-\x1f\x7f]|\$\{|#\{/.test(form.name)) errors.push('表单名称须为 1–120 字符的单行纯文本。')
    if (!/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(form.code)) errors.push('表单编码须以字母开头，仅含字母、数字、下划线或短横线，最多 64 字符。')
    const key = JSON.stringify([form.formId, form.revision])
    if (!Number.isInteger(form.revision) || form.revision < 1 || revisions.has(key)) errors.push('表单修订号无效或重复。')
    if (codes.has(form.formId) && codes.get(form.formId) !== form.code) errors.push('同一表单的全部修订必须使用同一稳定编码。')
    if (owners.has(form.code) && owners.get(form.code) !== form.formId) errors.push('不同表单不能使用相同编码。')
    revisions.add(key); codes.set(form.formId, form.code); owners.set(form.code, form.formId)
  }
  return [...new Set(errors)]
}
export function hasBindableNode(binding: NodeFormBinding, nodes: readonly { id: string; type: string }[]): boolean {
  return nodes.some(node => node.id === binding.nodeId && ['bpmn:StartEvent', 'bpmn:UserTask'].includes(node.type))
}
