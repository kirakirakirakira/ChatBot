import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

/**
 * 后端地址：默认 8089（application.properties 里的 server.port）。
 * 允许用环境变量 BACKEND_URL 覆盖，是为了「本机已经有一个后端在跑 8089、又想用另一份代码验证」的场景：
 * 把第二个后端起在别的端口（例如 --server.port=8099），再 BACKEND_URL=http://localhost:8099 npm run dev，
 * 两边互不干扰，不用去动别人正在跑的那个进程。
 */
const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8089'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // REST 和 SSE 都走 /api 代理，前端代码里不写死后端地址（地址见上面的 backendUrl）。
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
    },
  },
})
