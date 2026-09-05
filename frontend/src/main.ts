import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/base.css'
import './styles/workbench-ux.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia).use(router).use(ElementPlus, { locale: zhCn })
app.mount('#app')
