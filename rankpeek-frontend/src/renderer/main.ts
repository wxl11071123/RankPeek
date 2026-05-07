import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/styles/main.css'
import {
  loadGameAssetManifest,
  loadGameAssetMetadata,
  loadLcuGameAssetMetadataOverlay
} from './utils/gameAssetUrls'

const app = createApp(App)

app.use(createPinia())
app.use(router)

void Promise.all([loadGameAssetManifest(), loadGameAssetMetadata()])
  .finally(() => {
    app.mount('#app')
    void loadLcuGameAssetMetadataOverlay()
  })
