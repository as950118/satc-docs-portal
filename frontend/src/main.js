import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'

import { authUtils } from './api/auth'
import App from './App.vue'
import DocumentView from './views/DocumentView.vue'
import Home from './views/Home.vue'
import Login from './views/Login.vue'
import SpecEditor from './views/SpecEditor.vue'
import SpecList from './views/SpecList.vue'
import TechDocView from './views/TechDocView.vue'

// 라우트 가드 함수
const requireAuth = (to, from, next) => {
  if (authUtils.isAuthenticated()) {
    next()
  } else {
    next('/login')
  }
}

const redirectIfAuthenticated = (to, from, next) => {
  if (authUtils.isAuthenticated()) {
    next('/')
  } else {
    next()
  }
}

const routes = [
  { 
    path: '/login', 
    component: Login,
    beforeEnter: redirectIfAuthenticated
  },
  { 
    path: '/', 
    component: Home,
    beforeEnter: requireAuth
  },
  { 
    path: '/document/:id', 
    component: DocumentView, 
    props: true,
    beforeEnter: requireAuth
  },
  { path: '/specs', component: SpecList, beforeEnter: requireAuth },
  { path: '/specs/new', component: SpecEditor, beforeEnter: requireAuth },
  { path: '/specs/:id', component: SpecEditor, beforeEnter: requireAuth },
  { path: '/specs/:prdId/tech/new', component: TechDocView, beforeEnter: requireAuth },
  { path: '/tech-docs/:id', component: TechDocView, beforeEnter: requireAuth }
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

