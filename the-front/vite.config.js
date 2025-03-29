import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  define: {
    // 为sockjs-client提供global对象
    global: {}
  },
  optimizeDeps: {
    esbuildOptions: {
      // 定义全局变量
      define: {
        global: 'globalThis'
      }
    }
  },
  server: {  
    proxy: {  
      '/ws': {  
          target: 'http://localhost:8080', // 后端地址  
          changeOrigin: true,  
          secure: false,  
          ws: true // 如果需要代理 WebSocket  
      }  
    }  
  }  
})
