import '@/assets/main.css'

import { createApp } from 'vue'

import App from '@/App.vue'
import router from '@/router'

const app = createApp(App)

app.use(router)

// Keep the prerendered page in place until the initial lazy route is ready.
// Otherwise the layout briefly contains only the header and footer, and the
// route content pushes the footer out of the viewport when it arrives.
router.isReady().then(() => app.mount('#app'))
