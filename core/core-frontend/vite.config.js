import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9999',
        changeOrigin: true,
        secure: false
      },
      '/wechat': {
        target: 'http://localhost:9999',
        changeOrigin: true,
        secure: false
      }
    }
  },
  plugins: [vue()]
})
