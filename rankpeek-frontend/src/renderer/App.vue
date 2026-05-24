<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import TitleBar from '@/components/layout/TitleBar.vue'
import Sidebar from '@/components/layout/Sidebar.vue'
import { createGameflowAutoNavigator } from '@/services/gameflowAutoNavigation'
import { useGameStore } from '@/stores/game'

const gameStore = useGameStore()
const router = useRouter()
const isStandaloneRoute = computed(() => isStandaloneRuntimeRoute())

let removeTrayNavigateListener: (() => void) | null = null
let stopGameflowAutoNavigation: (() => void) | null = null
let standaloneConnectionTimer: ReturnType<typeof setInterval> | null = null

if (!isStandaloneRuntimeRoute()) {
  void gameStore.initConnection()
}

function isStandaloneRuntimeRoute() {
  return router.currentRoute.value.meta.standalone === true || window.location.hash.startsWith('#/opgg')
}

onMounted(() => {
  if (isStandaloneRoute.value) {
    void gameStore.checkConnection()
    standaloneConnectionTimer = setInterval(() => {
      void gameStore.checkConnection()
    }, 5000)
  }

  if (!isStandaloneRoute.value) {
    stopGameflowAutoNavigation = createGameflowAutoNavigator(router)
  }

  if (isStandaloneRoute.value || !window.electronAPI?.onTrayNavigate) {
    return
  }

  removeTrayNavigateListener = window.electronAPI.onTrayNavigate((path) => {
    if (router.currentRoute.value.path === path) {
      return
    }

    void router.push(path)
  })
})

onBeforeUnmount(() => {
  removeTrayNavigateListener?.()
  removeTrayNavigateListener = null
  stopGameflowAutoNavigation?.()
  stopGameflowAutoNavigation = null
  if (standaloneConnectionTimer) {
    clearInterval(standaloneConnectionTimer)
    standaloneConnectionTimer = null
  }
})
</script>

<template>
  <div class="app-container">
    <TitleBar />
    <div class="app-content">
      <Sidebar v-if="!isStandaloneRoute" />
      <main class="main-content" :class="{ 'main-content-standalone': isStandaloneRoute }">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-family: var(--font-text);
}

.app-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.main-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 24px;
  background: var(--bg-primary);
}

.main-content-standalone {
  padding: 0;
  overflow: hidden;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 760px) {
  .main-content {
    padding: 14px;
  }
}
</style>
