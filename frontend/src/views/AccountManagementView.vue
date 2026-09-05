<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { ApiError } from '@/api/client'
import { authApi, type LocalAccount, type LocalAccountCreate, type LocalAccountUpdate } from '@/api/auth'
import type { PlatformRole } from '@/api/identity'
import { serviceSystemAdminApi, type ServiceSystem } from '@/api/service-systems'
import { useSessionStore } from '@/stores/session'
const session = useSessionStore()
const rows = ref<LocalAccount[]>([]), systems = ref<ServiceSystem[]>([]), total = ref(0)
const page = ref(1), pageSize = ref(20), q = ref(''), status = ref(''), loading = ref(false), saving = ref(false)
const error = ref(''), notice = ref(''), dialogMode = ref<false|'CREATE'|'EDIT'|'PASSWORD'>(false), selected = ref<LocalAccount>()
const dialog = computed<false|''|'CREATE'|'EDIT'|'PASSWORD'>({ get:()=>dialogMode.value, set:value=>{dialogMode.value=value||false} })
const roleOptions: Array<{ value: PlatformRole; label: string }> = [
  { value:'FIRST_LINE_SUPPORT', label:'一线运维' }, { value:'SECOND_LINE_SUPPORT', label:'二线运维' },
  { value:'SERVICE_MANAGER', label:'服务经理' }, { value:'SLA_MANAGER', label:'SLA 管理员' },
  { value:'AUDITOR', label:'审计员' }, { value:'PLATFORM_ADMIN', label:'平台管理员' },
]
const form = reactive({ loginName:'', displayName:'', organizationId:'', password:'', enabled:true, roles:[] as PlatformRole[], systemCodes:[] as string[], reason:'' })
let generation = 0, disposed = false, searchTimer: ReturnType<typeof setTimeout> | undefined
const canManage = computed(() => session.authorization?.roles.includes('PLATFORM_ADMIN') ?? false)
function message(cause: unknown, fallback: string): string {
  if (cause instanceof ApiError && cause.status === 409) return '账号已被其他管理员更新，请刷新后重试。'
  if (cause instanceof ApiError && cause.status === 403) return '当前账号没有本地账号管理权限。'
  return fallback
}
async function load(): Promise<void> {
  const current = ++generation; loading.value = true; error.value = ''
  try { const result = await authApi.accounts({ page:page.value, pageSize:pageSize.value, q:q.value.trim() || undefined, status:status.value || undefined }); if (!disposed && current === generation) { rows.value=result.items; total.value=result.total } }
  catch (cause) { if (!disposed && current === generation) { rows.value=[]; total.value=0; error.value=message(cause,'账号列表读取失败，请稍后重试。') } }
  finally { if (!disposed && current === generation) loading.value=false }
}
function resetForm(): void { Object.assign(form,{ loginName:'',displayName:'',organizationId:session.currentUser?.organizationIamOrganizationId ?? '',password:'',enabled:true,roles:[],systemCodes:[],reason:'' }) }
function openCreate(): void { selected.value=undefined; resetForm(); dialog.value='CREATE'; error.value=''; notice.value='' }
function openEdit(item: LocalAccount): void { if(item.id===session.currentUser?.iamUserId){error.value='不能在当前会话中修改自己的角色或状态；可使用“重置密码”。';return}selected.value=item; Object.assign(form,{loginName:item.loginName,displayName:item.displayName,organizationId:item.organizationId,password:'',enabled:item.enabled,roles:item.roles.filter(role=>role!=='REQUESTER'),systemCodes:[...item.systemCodes],reason:''}); dialog.value='EDIT'; error.value=''; notice.value='' }
function openPassword(item: LocalAccount): void { selected.value=item; resetForm(); form.loginName=item.loginName; dialog.value='PASSWORD'; error.value=''; notice.value='' }
function validate(): string | undefined {
  if (form.reason.trim().length < 4) return '请填写至少 4 字的变更原因。'
  if (dialog.value!=='PASSWORD' && (!form.displayName.trim() || !form.organizationId.trim())) return '请填写显示名称和所属组织。'
  if (dialog.value==='CREATE' && (!/^[A-Za-z][A-Za-z0-9._-]{2,63}$/.test(form.loginName) || form.password.length<12 || form.password.length>128)) return '账号须为 3–64 位规范字符，初始密码须为 12–128 个字符。'
  if (dialog.value==='PASSWORD' && (form.password.length<12 || form.password.length>128)) return '新密码须为 12–128 个字符。'
}
async function save(): Promise<void> {
  const issue=validate(); if(issue){error.value=issue;return} saving.value=true;error.value='';notice.value=''
  try {
    if(dialog.value==='CREATE') await authApi.createAccount({loginName:form.loginName.trim(),displayName:form.displayName.trim(),organizationId:form.organizationId.trim(),password:form.password,roles:form.roles,systemCodes:form.systemCodes,reason:form.reason.trim()} satisfies LocalAccountCreate)
    if(dialog.value==='EDIT'&&selected.value) await authApi.updateAccount(selected.value.id,{version:selected.value.version,displayName:form.displayName.trim(),organizationId:form.organizationId.trim(),enabled:form.enabled,roles:form.roles,systemCodes:form.systemCodes,reason:form.reason.trim()} satisfies LocalAccountUpdate)
    if(dialog.value==='PASSWORD'&&selected.value) await authApi.resetPassword(selected.value.id,selected.value.version,form.password,form.reason.trim())
    form.password=''; dialog.value=''; notice.value='账号配置已保存，权限与负责系统将在服务端后续请求中重新计算。'; await load()
  } catch(cause){error.value=message(cause,'账号保存失败，请检查字段和权限范围。')} finally{saving.value=false}
}
async function disable(item: LocalAccount): Promise<void> {
  if(item.id===session.currentUser?.iamUserId){error.value='不能停用当前登录账号。';return}
  try { const reason=await ElMessageBox.prompt(`停用账号 ${item.loginName}。历史工单保留，账号将不能登录或抢单。`,'停用账号',{confirmButtonText:'确认停用',cancelButtonText:'取消',inputPlaceholder:'填写至少4字原因',inputValidator:value=>value.trim().length>=4||'至少4个字符'}); saving.value=true; await authApi.disableAccount(item.id,item.version,reason.value.trim()); notice.value='账号已停用。'; await load() }
  catch(cause){const action=typeof cause==='object'&&cause!==null&&'action' in cause?String((cause as {action?:unknown}).action):String(cause);if(!['cancel','close'].includes(action))error.value=message(cause,'账号停用失败。')} finally{saving.value=false}
}
function roleNames(item: LocalAccount): string { return item.roles.filter(role=>role!=='REQUESTER').map(role=>roleOptions.find(option=>option.value===role)?.label??role).join('、')||'普通用户' }
watch([q,status],()=>{if(searchTimer)clearTimeout(searchTimer);searchTimer=setTimeout(()=>{page.value=1;void load()},300)})
onMounted(async()=>{try{systems.value=(await serviceSystemAdminApi.list()).items}catch{/* Account list remains usable. */}await load()})
onBeforeUnmount(()=>{disposed=true;generation++;if(searchTimer)clearTimeout(searchTimer)})
</script>
<template>
  <div class="page-heading"><div><span class="eyebrow">平台安全</span><h2>账号管理</h2><p>管理本地登录账号、平台角色和负责系统；普通用户默认具备提单和知识阅读权限。</p></div><button v-if="canManage" class="button button--primary" type="button" @click="openCreate">＋ 新建账号</button></div>
  <p v-if="notice" class="form-alert form-alert--success" role="status">{{notice}}</p><p v-if="error" class="form-alert form-alert--error" role="alert">{{error}}</p>
  <section class="panel account-table-panel">
    <div class="account-filter"><el-input v-model="q" clearable placeholder="搜索账号或显示名称"/><el-select v-model="status" clearable placeholder="全部状态"><el-option label="正常" value="ACTIVE"/><el-option label="已停用" value="DISABLED"/><el-option label="已锁定" value="LOCKED"/></el-select><button class="button button--secondary" type="button" :disabled="loading" @click="load">刷新</button></div>
    <el-table v-loading="loading" :data="rows" row-key="id" stripe><el-table-column prop="loginName" label="登录账号" min-width="145"/><el-table-column prop="displayName" label="显示名称" min-width="130"/><el-table-column prop="organizationId" label="所属组织" min-width="150"/><el-table-column label="角色" min-width="180"><template #default="{row}">{{roleNames(row)}}</template></el-table-column><el-table-column label="负责系统" min-width="160"><template #default="{row}">{{row.systemCodes.join('、')||'—'}}</template></el-table-column><el-table-column label="状态" width="95"><template #default="{row}"><span class="tag" :class="row.enabled&&!row.lockedUntil?'tag--green':'tag--muted'">{{!row.enabled?'已停用':row.lockedUntil?'已锁定':'正常'}}</span></template></el-table-column><el-table-column prop="version" label="版本" width="75"/><el-table-column fixed="right" label="操作" width="220"><template #default="{row}"><button class="link-button" type="button" @click="openEdit(row)">编辑</button><button class="link-button" type="button" @click="openPassword(row)">重置密码</button><button class="text-danger-button" type="button" :disabled="!row.enabled||saving" @click="disable(row)">停用</button></template></el-table-column><template #empty><el-empty description="当前筛选条件下没有账号"/></template></el-table>
    <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="page=1;load()"/>
  </section>
  <el-dialog v-model="dialog" :title="dialog==='CREATE'?'新建账号':dialog==='EDIT'?'编辑账号':'重置密码'" width="min(680px,calc(100vw - 32px))" :close-on-click-modal="!saving"><el-form label-position="top"><div class="account-form-grid"><el-form-item v-if="dialog!=='PASSWORD'" label="登录账号" required><el-input v-model="form.loginName" :disabled="dialog==='EDIT'" maxlength="64"/></el-form-item><el-form-item v-if="dialog!=='PASSWORD'" label="显示名称" required><el-input v-model="form.displayName" maxlength="100"/></el-form-item><el-form-item v-if="dialog!=='PASSWORD'" label="所属组织" required><el-input v-model="form.organizationId" :disabled="dialog==='EDIT'" maxlength="128"/></el-form-item><el-form-item v-if="dialog==='CREATE'||dialog==='PASSWORD'" label="密码" required><el-input v-model="form.password" type="password" show-password maxlength="128" autocomplete="new-password"/><small>12–128 个字符，保存后不会再次显示。</small></el-form-item><el-form-item v-if="dialog==='EDIT'" label="账号状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用"/></el-form-item><el-form-item v-if="dialog!=='PASSWORD'" class="account-form-wide" label="平台角色"><el-checkbox-group v-model="form.roles"><el-checkbox v-for="role in roleOptions" :key="role.value" :value="role.value">{{role.label}}</el-checkbox></el-checkbox-group></el-form-item><el-form-item v-if="dialog!=='PASSWORD'" class="account-form-wide" label="负责系统"><el-select v-model="form.systemCodes" multiple filterable placeholder="普通用户可不选；运维按系统授权"><el-option v-for="system in systems" :key="system.systemCode" :label="`${system.systemName}（${system.systemCode}）`" :value="system.systemCode"/></el-select></el-form-item><el-form-item class="account-form-wide" label="变更原因" required><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500"/></el-form-item></div></el-form><template #footer><button class="button button--secondary" type="button" :disabled="saving" @click="dialog=''">取消</button><button class="button button--primary" type="button" :disabled="saving" @click="save">{{saving?'保存中…':'保存'}}</button></template></el-dialog>
</template>
<style scoped>
.account-table-panel{padding:16px}.account-filter{display:grid;grid-template-columns:minmax(220px,1fr) 180px auto;gap:10px;margin-bottom:14px}.account-table-panel :deep(.el-pagination){justify-content:flex-end;margin-top:16px}.link-button,.text-danger-button{margin-right:10px;border:0;background:none;cursor:pointer}.link-button{color:#176dc1}.text-danger-button{color:#b64747}.text-danger-button:disabled{color:#aab4bd;cursor:not-allowed}.account-form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 14px}.account-form-wide{grid-column:1/-1}.account-form-grid small{color:#8796a6}.account-form-grid :deep(.el-select){width:100%}@media(max-width:720px){.account-filter,.account-form-grid{grid-template-columns:1fr}.account-form-wide{grid-column:auto}.account-table-panel{overflow-x:auto}}
</style>
