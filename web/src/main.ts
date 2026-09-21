import '@/assets/main.css'

import { createApp } from 'vue'

import App from '@/App.vue'
import router from '@/router'

const app = createApp(App)

app.use(router)

// Built pages contain simplified crawlable markup. index.html hides that
// fallback only when JavaScript is available; mounting reveals the real page
// atomically because Vue adds data-v-app to the container during mount.
router.isReady().then(() => app.mount('#app'))
