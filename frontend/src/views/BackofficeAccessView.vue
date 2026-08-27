<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ApiError } from '@/api/client'
import { backofficeAccessApi, type BackofficeAccessResponse, type BackofficeDataScope, type BackofficeRole, type BackofficeScopeType } from '@/api/backoffice-access'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const iamUserId = ref('')
const result = ref<BackofficeAccessResponse | null>(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const roleOptions: Array<{ code: BackofficeRole; label: string; note: string }> = [
  { code: 'ROLE_FIRST_LINE_SUPPORT', label: '一线支持', note: '受理、抢单和一线处理' },
  { code: 'ROLE_SECOND_LINE_SUPPORT', label: '二线支持', note: '专业处理与协作' },
  { code: 'ROLE_APPROVER', label: '审批人', note: '受控流程审批候选' },
  { code: 'ROLE_SERVICE_MANAGER', label: '服务经理', note: '分派、升级与服务治理' },
  { code: 'ROLE_SLA_MANAGER', label: 'SLA 管理员', note: 'SLA 规则管理' },
  { code: 'ROLE_AUDITOR', label: '审计员', note: '审计与受限报表访问' },
  { code: 'ROLE_PLATFORM_ADMIN', label: '平台管理员', note: '平台级高危管理权限' },
]
const scopeTypes: Array<{ code: BackofficeScopeType; label: string }> = [
  { code: 'ORGANIZATION', label: '组织' }, { code: 'SERVICE', label: '服务目录' }, { code: 'QUEUE', label: '队列' }, { code: 'CONFIGURATION_ITEM', label: '配置项（CI）' },
]
const form = reactive<{ enabled: boolean; roleCodes: BackofficeRole[]; dataScopes: BackofficeDataScope[]; version: number }>({ enabled: false, roleCodes: [], dataScopes: [], version: 0 })
const canManage = computed(() => session.authorization?.roles.includes('PLATFORM_ADMIN') ?? false)
const isSelf = computed(() => Boolean(result.value && session.currentUser?.iamUserId === result.value.user.iamUserId))

function resetForm(response: BackofficeAccessResponse): void {
  form.enabled = response.access.enabled
  form.roleCodes = [...response.access.roleCodes]
  form.dataScopes = response.access.dataScopes.map((scope) => ({ ...scope }))
  form.version = response.access.version
}
async function load(): Promise<void> {
  const id = iamUserId.value.trim()
  if (!/^[A-Za-z0-9._:-]{1,128}$/.test(id)) { error.value = '请输入已同步 IAM 用户的不可变 ID。'; return }
  loading.value = true; error.value = ''; notice.value = ''; result.value = null
  try { const response = await backofficeAccessApi.get(id); result.value = response; resetForm(response) }
  catch (cause) { error.value = cause instanceof ApiError ? cause.message : '查询失败，请检查 IAM 投影与后台管理权限。' }
  finally { loading.value = false }
}
function toggleRole(role: BackofficeRole): void { form.roleCodes = form.roleCodes.includes(role) ? form.roleCodes.filter((item) => item !== role) : [...form.roleCodes, role] }
function addScope(): void { form.dataScopes.push({ scopeType: 'ORGANIZATION', scopeId: '' }) }
function removeScope(index: number): void { form.dataScopes.splice(index, 1) }
async function save(): Promise<void> {
  if (!result.value) return
  if (isSelf.value) { error.value = '为避免自我提权，不能修改当前登录人的后台授权。'; return }
  if (form.dataScopes.some((scope) => !/^[A-Za-z0-9._:-]{1,128}$/.test(scope.scopeId))) { error.value = '请补全数据范围 ID，仅允许字母、数字、点、下划线、短横线和冒号。'; return }
  saving.value = true; error.value = ''; notice.value = ''
  try {
    const response = await backofficeAccessApi.replace(result.value.user.iamUserId, { enabled: form.enabled, roleCodes: form.roleCodes, dataScopes: form.dataScopes, expectedVersion: form.version })
    result.value = response; resetForm(response); notice.value = '后台授权已保存并进入审计。该人员下次 IAM SSO 登录或会话刷新后按新权限生效。'
  } catch (cause) { error.value = cause instanceof ApiError ? cause.message : '保存失败，系统未假定授权已生效。' }
  finally { saving.value = false }
}
</script>

<template>
  <div class="page-heading"><div><h2>后台人员授权</h2><p>IAM 负责身份；平台只管理后台角色和数据范围，不保存密码、不改 IAM 组织。</p></div><span class="readonly-badge">🔐 受控配置</span></div>
  <p class="projection-notice"><b>授权边界：</b>所有 IAM 登录用户默认是普通提单人。仅将已同步、启用的 IAM ID 配置为后台人员后，才会获得处理、审批或治理权限。IAM ID 不是登录凭据。</p>
  <section class="panel access-search-panel"><form class="ticket-filter" @submit.prevent="load"><label class="field field--grow"><span>IAM 用户 ID</span><input v-model.trim="iamUserId" maxlength="128" placeholder="例如 iam-u-100231" autocomplete="off" /></label><button class="button button--primary" type="submit" :disabled="loading || !canManage">{{ loading ? '查询中…' : '读取 IAM 投影' }}</button></form><p v-if="!canManage" class="form-alert form-alert--error">当前会话不是平台管理员，不能查看或配置后台授权。</p></section>
  <p v-if="error" class="form-alert form-alert--error access-alert">{{ error }}</p><p v-if="notice" class="form-alert form-alert--success access-alert">{{ notice }}</p>
  <section v-if="result" class="access-workspace">
    <article class="panel access-subject-panel"><div class="panel-header"><div><h3>IAM 身份（只读）</h3><p>所有字段来自同步投影，不能在本页面修改。</p></div><span class="readonly-badge">只读</span></div><dl class="projection-definition"><div><dt>姓名</dt><dd>{{ result.user.displayName }}</dd></div><div><dt>登录名</dt><dd>{{ result.user.loginName }}</dd></div><div><dt>IAM 用户 ID</dt><dd class="mono-text">{{ result.user.iamUserId }}</dd></div><div><dt>所属组织</dt><dd>{{ result.user.organizationName }}</dd></div><div><dt>组织 IAM ID</dt><dd class="mono-text">{{ result.user.organizationIamId }}</dd></div><div><dt>授权版本</dt><dd>v{{ form.version || '未配置' }}</dd></div></dl></article>
    <form class="panel form-panel access-editor" @submit.prevent="save"><div class="panel-header"><div><h3>后台访问授权</h3><p>保存时由服务端再次验证管理员身份、IAM 账号状态、版本和最小权限规则。</p></div><span class="status-pill" :class="form.enabled ? 'status-pill--resolved' : 'status-pill--cancelled'">{{ form.enabled ? '后台已启用' : '未启用' }}</span></div><label class="checkbox-field access-enabled"><input v-model="form.enabled" :disabled="isSelf" type="checkbox" /> 启用后台访问 <small>关闭后该 IAM 用户仍可正常作为普通用户提单。</small></label><div class="access-section"><h4>平台角色 <small>普通提单人是隐含基础角色，无需配置</small></h4><div class="access-role-grid"><label v-for="option in roleOptions" :key="option.code" class="access-role-choice" :class="{ 'is-selected': form.roleCodes.includes(option.code) }"><input :checked="form.roleCodes.includes(option.code)" :disabled="isSelf" type="checkbox" @change="toggleRole(option.code)" /><span><b>{{ option.label }}</b><small>{{ option.note }}</small></span></label></div></div><div class="access-section"><h4>数据范围 <small>角色不自动获得全局数据；按需授权组织、服务、队列或 CI。</small></h4><div class="access-scope-list"><div v-for="(scope, index) in form.dataScopes" :key="`${scope.scopeType}-${index}`" class="access-scope-row"><select v-model="scope.scopeType" :disabled="isSelf"><option v-for="type in scopeTypes" :key="type.code" :value="type.code">{{ type.label }}</option></select><input v-model.trim="scope.scopeId" :disabled="isSelf" maxlength="128" placeholder="范围 ID" /><button class="button button--secondary" :disabled="isSelf" type="button" @click="removeScope(index)">移除</button></div><button class="button button--secondary" :disabled="isSelf" type="button" @click="addScope">+ 添加数据范围</button></div></div><div class="form-actions"><span v-if="isSelf" class="form-alert form-alert--error">不能修改自己的后台授权。</span><button class="button button--primary" type="submit" :disabled="saving || isSelf || !canManage">{{ saving ? '保存中…' : '保存并审计' }}</button></div></form>
  </section>
  <section class="panel field-source-panel"><div class="panel-header"><div><h3>生效与安全规则</h3><p>平台授权与 IAM 主数据分离，避免把身份同步等同于权限授予。</p></div></div><div class="table-scroll"><table><thead><tr><th>规则</th><th>平台处理</th></tr></thead><tbody><tr><td>IAM 登录</td><td>默认仅普通提单人；平台不使用“手填 IAM ID”认证。</td></tr><tr><td>后台角色</td><td>仅平台管理员对已同步、启用 IAM 用户授予；角色变更具备版本与审计。</td></tr><tr><td>数据范围</td><td>按组织、服务目录、队列、CI 收敛对象可见性；每个请求由服务端校验。</td></tr><tr><td>高危变更</td><td>禁止自我提权，禁止移除最后一名平台管理员；生产环境应对平台管理员授权启用双人复核。</td></tr></tbody></table></div></section>
</template>
