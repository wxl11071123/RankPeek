<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import TitleBar from '@/components/layout/TitleBar.vue'
import Sidebar from '@/components/layout/Sidebar.vue'
import { useGameStore } from '@/stores/game'

const gameStore = useGameStore()
const router = useRouter()

let removeTrayNavigateListener: (() => void) | null = null

gameStore.initConnection()

onMounted(() => {
  if (!window.electronAPI?.onTrayNavigate) {
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
})
</script>

<template>
  <div class="app-container">
    <TitleBar />
    <div class="app-content">
      <Sidebar />
      <main class="main-content">
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
  overflow-y: auto;
  padding: 24px;
  background: var(--bg-primary);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
