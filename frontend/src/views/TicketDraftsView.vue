<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useSessionStore } from '@/stores/session'
import { ticketDraftApi, type PersonalDraftSummary } from '@/api/ticket-drafts'
const session=useSessionStore(),rows=ref<PersonalDraftSummary[]>([]),page=ref(1),total=ref(0),loading=ref(false),error=ref(''),deleting=ref('')
let generation=0,disposed=false
async function load(){const request=++generation;loading.value=true;error.value='';try{const result=await ticketDraftApi.list(page.value);if(!disposed&&request===generation){rows.value=result.items;total.value=result.total}}catch{if(!disposed&&request===generation)error.value='草稿箱加载失败，请重试。'}finally{if(!disposed&&request===generation)loading.value=false}}
async function remove(row:PersonalDraftSummary){if(deleting.value)return;try{await ElMessageBox.confirm(`删除草稿“${row.title}”？`,'删除草稿',{type:'warning',confirmButtonText:'删除',cancelButtonText:'保留'});deleting.value=row.id;await ticketDraftApi.delete(row.id,row.version);await load()}catch(e){if(e!=='cancel'&&e!=='close')error.value='删除失败，草稿可能已被修改，请刷新核对。'}finally{deleting.value=''}}
watch(()=>[session.currentUser?.iamUserId,session.loading],()=>{generation++;rows.value=[];total.value=0;page.value=1;if(session.currentUser&&!session.loading)void load()},{immediate:true})
onBeforeUnmount(()=>{disposed=true;generation++})
</script>
<template>
<div class="page-heading"><div><h2>草稿箱</h2><p>继续填写已暂存的工单，填写完成后发起。</p></div><RouterLink class="button button--primary" to="/tickets/new">新建工单</RouterLink></div>
<section class="panel drafts-panel"><p v-if="error" class="form-alert form-alert--error" role="alert">{{error}}</p><div class="drafts-toolbar"><span>{{total}} 份个人草稿</span><button class="button button--secondary" :disabled="loading" @click="load">刷新</button></div><el-table v-loading="loading" :data="rows" stripe><el-table-column label="主题" min-width="240"><template #default="{row}"><RouterLink :to="{path:'/tickets/new',query:{draftId:row.id}}">{{row.title}}</RouterLink></template></el-table-column><el-table-column prop="systemCode" label="系统" min-width="120"/><el-table-column prop="catalogId" label="服务目录" min-width="180"/><el-table-column label="暂存时间" width="180"><template #default="{row}">{{new Date(row.updatedAt).toLocaleString('zh-CN',{hour12:false})}}</template></el-table-column><el-table-column label="操作" width="150"><template #default="{row}"><RouterLink :to="{path:'/tickets/new',query:{draftId:row.id}}">继续填写</RouterLink><el-button link type="danger" :disabled="Boolean(deleting)" @click="remove(row)">删除</el-button></template></el-table-column><template #empty>{{error?'暂不可用':'还没有暂存的工单'}}</template></el-table><el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total,prev,pager,next" @current-change="load"/><p class="drafts-note">附件需重新选择；系统或表单更新后，发起前会重新校验。</p></section>
</template>
<style scoped>.drafts-panel{padding:18px}.drafts-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:14px;color:#69829b}.drafts-panel :deep(.el-pagination){justify-content:flex-end;margin-top:18px}.drafts-note{color:#8393a4;font-size:12px;margin:18px 0 0}</style>
