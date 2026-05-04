import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/styles/main.css'
import { loadGameAssetManifest } from './utils/gameAssetUrls'

const app = createApp(App)

app.use(createPinia())
app.use(router)

const manifestStartupDeadline = new Promise<void>(resolve => {
  setTimeout(resolve, 500)
})

void Promise.race([loadGameAssetManifest(), manifestStartupDeadline]).finally(() => {
  app.mount('#app')
})
