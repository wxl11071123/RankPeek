import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页', keepAlive: true }
  },
  {
    path: '/gaming',
    name: 'Gaming',
    component: () => import('@/views/GamingView.vue'),
    meta: { title: '游戏进行中', keepAlive: false }
  },
  {
    path: '/summoner',
    name: 'Summoner',
    component: () => import('@/views/SummonerView.vue'),
    meta: { title: '召唤师查询', keepAlive: true }
  },
  {
    path: '/match-history',
    name: 'MatchHistory',
    component: () => import('@/views/MatchHistoryView.vue'),
    meta: { title: '战绩查询', keepAlive: false }
  },
  {
    path: '/user-tag',
    name: 'UserTag',
    component: () => import('@/views/UserTagView.vue'),
    meta: { title: '用户标签', keepAlive: true }
  },
  {
    path: '/tag-config',
    name: 'TagConfig',
    component: () => import('@/views/TagConfigView.vue'),
    meta: { title: '标签配置', keepAlive: false }
  },
  {
    path: '/automation',
    name: 'Automation',
    component: () => import('@/views/AutomationView.vue'),
    meta: { title: '自动化设置', keepAlive: false }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { title: '系统设置', keepAlive: false }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - RankPeek` : 'RankPeek'
  next()
})

export default router
