import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { t, type MessageKey } from '@/i18n'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomeView.vue'),
    meta: { titleKey: 'nav.home', keepAlive: true }
  },
  {
    path: '/gaming',
    name: 'Gaming',
    component: () => import('@/views/GamingView.vue'),
    meta: { titleKey: 'nav.gaming', keepAlive: false }
  },
  {
    path: '/summoner',
    name: 'Summoner',
    component: () => import('@/views/SummonerView.vue'),
    meta: { titleKey: 'nav.summoner', keepAlive: true }
  },
  {
    path: '/match-history',
    name: 'MatchHistory',
    component: () => import('@/views/MatchHistoryView.vue'),
    meta: { titleKey: 'nav.matchHistory', keepAlive: false }
  },
  {
    path: '/user-tag',
    name: 'UserTag',
    component: () => import('@/views/UserTagView.vue'),
    meta: { titleKey: 'nav.userTag', keepAlive: true }
  },
  {
    path: '/tag-config',
    name: 'TagConfig',
    component: () => import('@/views/TagConfigView.vue'),
    meta: { titleKey: 'nav.tagConfig', keepAlive: false }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SettingsView.vue'),
    meta: { titleKey: 'nav.settings', keepAlive: false }
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const titleKey = to.meta.titleKey as MessageKey | undefined
  document.title = titleKey ? `${t(titleKey)} - RankPeek` : 'RankPeek'
  next()
})

export default router
