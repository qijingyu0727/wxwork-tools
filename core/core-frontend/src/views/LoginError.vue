<template>
  <div class="font-sans bg-gray-100 text-gray-800 min-h-screen flex items-center justify-center">
    <div class="bg-white p-10 rounded-lg shadow-lg text-center max-w-md w-full mx-4">
      <h1 class="text-2xl font-bold text-red-500 mb-5">登录失败</h1>
      <div class="text-gray-600 mb-8 p-4 bg-gray-50 border-l-4 border-red-500 text-left">
        {{ errorMessage }}
      </div>
      <a href="/wechat/work/login/generate-qrcode" class="inline-block px-6 py-3 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors">
        重新登录
      </a>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const errorMessage = ref('正在加载错误信息...')

onMounted(() => {
  const urlParams = new URLSearchParams(window.location.search)
  const error = urlParams.get('error')
  
  if (error) {
    errorMessage.value = decodeURIComponent(error)
  } else {
    const storedError = localStorage.getItem('login_error')
    if (storedError) {
      errorMessage.value = storedError
      localStorage.removeItem('login_error')
    } else {
      errorMessage.value = '未知的登录错误，请重试。'
    }
  }
})
</script>
