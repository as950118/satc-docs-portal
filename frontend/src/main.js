import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import App from './App.vue'
import DocumentView from './views/DocumentView.vue'
import Home from './views/Home.vue'

const routes = [
  { path: '/', component: Home },
  { path: '/document/:id', component: DocumentView, props: true }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const app = createApp(App)

// Element Plus 아이콘 등록
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus)
app.use(router)
app.mount('#app')

