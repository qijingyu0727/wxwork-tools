<template>
  <div class="home-container">
    <div id="toast" :class="['toast', toast.show ? 'show' : '', toast.isSuccess ? 'toast-success' : 'toast-error']">
      <i :class="[toast.isSuccess ? 'fa fa-check-circle' : 'fa fa-exclamation-circle', 'mr-3 text-lg', toast.isSuccess ? 'text-green-500' : 'text-red-500']"></i>
      <span class="text-sm">{{ toast.message }}</span>
    </div>

    <main class="home-main">
      <div class="home-title">
        <h2>
          企业微信群聊 chatID 获取
        </h2>
      </div>

      <div class="home-actions">
        <div v-if="userStore.isLoggedIn" class="user-info">
          <img :src="userStore.userInfo?.avatar || defaultAvatar" alt="用户头像" class="user-avatar">
          <span>{{ userStore.userInfo?.name || userStore.userInfo?.UserId }}</span>
          <button @click="handleLogout" class="logout-btn">
            <i class="fa fa-sign-out"></i>
          </button>
        </div>
        <button v-if="!userStore.isLoggedIn" @click="handleLogin" class="login-btn">
          <i class="fa fa-wechat"></i>
          企业微信登录
        </button>
        <button v-if="userStore.isLoggedIn" @click="getCurExternalChat" :disabled="loading" :class="['get-chat-btn', loading ? 'opacity-50 cursor-not-allowed' : '']">
          <i v-if="loading" class="fa fa-spinner fa-spin"></i>
          <i v-else class="fa fa-comments"></i>
          {{ loading ? '获取中...' : '获取当前群聊 chatID' }}
        </button>
      </div>

      <div class="card">
        <div class="card-content">
          <div class="form-group">
            <label class="form-label">群聊 chatID</label>
            <div class="textarea-wrapper">
              <textarea 
                v-model="chatId" 
                readonly 
                class="chat-textarea"
                rows="8"
                placeholder="点击上方按钮获取当前群聊的 chatID"
              ></textarea>
              <button 
                v-if="chatId" 
                @click="copyChatId" 
                class="copy-btn"
              >
                <i class="fa fa-copy mr-1"></i>复制
              </button>
            </div>
          </div>
          <div v-if="chatId" class="info-text">
            <i class="fa fa-info-circle"></i>
            chatID 已获取，可用于企业微信 API 调用
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { jsapiApi } from '@/api/doc'
import CryptoJS from 'crypto-js'
import * as ww from '@wecom/jssdk'
import '@/styles/home.css'

const corpId = ref('')
const agentId = ref('')
const chatId = ref('')
const loading = ref(false)

const getCorpId = async () => {
  const res = await jsapiApi.getCorpId()
  if (res.success) {
    corpId.value = res.data
  }
}

const getAgentId = async () => {
  const res = await jsapiApi.getAgentId()
  if (res.success) {
    agentId.value = res.data
  }
}

const generateNonceStr = () => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let result = ''
  for (let i = 0; i < 16; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

const generateSignature = async (ticket) => {
  const nonceStr = generateNonceStr()
  const timestamp = Math.floor(Date.now() / 1000)
  
  const cleanUrl = window.location.href.split('#')[0]; 
  const stringToSign = `jsapi_ticket=${ticket}&noncestr=${nonceStr}&timestamp=${timestamp}&url=${cleanUrl}`
  const signature = CryptoJS.SHA1(stringToSign).toString()
  
  return { timestamp, nonceStr, signature }
}

async function getConfigSignature() {
  const res = await jsapiApi.getJsapiTicket()
  if (res.success) {
    const ticket = res.data.ticket
    return await generateSignature(ticket)
  }
  throw new Error('获取企业 jsapi_ticket 失败')
}

async function getAgentConfigSignature() {
  const res = await jsapiApi.getAgentJsapiTicket()
  if (res.success) {
    const ticket = res.data.ticket
    return await generateSignature(ticket)
  }
  throw new Error('获取应用 jsapi_ticket 失败')
}

const registerWxWork = async () => {
  await getCorpId()
  await getAgentId()
  
  if (corpId.value && agentId.value) {
    console.log('corpId:', corpId.value)
    console.log('agentId:', agentId.value)

    ww.register({
      corpId: corpId.value,
      agentId: agentId.value,
      jsApiList: ['getCurExternalChat'],
      getConfigSignature,
      getAgentConfigSignature
    })
  }
}

const getCurExternalChat = () => {
  loading.value = true
  try {
    ww.getCurExternalChat({
        success(result) {
          // 成功回调，result.errMsg 固定格式为“方法名:ok”
          chatId.value = result.chatId
          showToast('获取群聊 chatID 成功！', true)
          console.log('当前群聊ID:', result.chatId)
          loading.value = false
        },
        fail(result) {
          // 失败回调，通过 result.errMsg 查看失败详情
          showToast('获取群聊 chatID 失败！'+result.errMsg, false)
          console.error('调用失败:', result)
          loading.value = false
        }
      })
  } catch (err) {
    showToast('异常：'+(err.message || err), false)
    console.error('异常:', err)
    loading.value = false
  }
}

const copyChatId = () => {
  if (chatId.value) {
    navigator.clipboard.writeText(chatId.value).then(() => {
      showToast('已复制到剪贴板', true)
    }).catch(() => {
      showToast('复制失败', false)
    })
  }
}

const userStore = useUserStore()

const defaultAvatar = 'data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%2224%22%20height%3D%2224%22%20viewBox%3D%220%200%2024%2024%22%20fill%3D%22none%22%20stroke%3D%22%2394a3b8%22%20stroke-width%3D%222%22%20stroke-linecap%3D%22round%22%20stroke-linejoin%3D%22round%22%3E%3Cpath%20d%3D%22M20%2021v-2a4%204%200%200%200-4-4H8a4%204%200%200%200-4%204v2%22%3E%3C%2Fpath%3E%3Ccircle%20cx%3D%2212%22%20cy%3D%227%22%20r%3D%224%22%3E%3C%2Fcircle%3E%3C%2Fsvg%3E'

const toast = ref({
  show: false,
  message: '',
  isSuccess: true
})

const showToast = (message, isSuccess = true) => {
  toast.value.message = message
  toast.value.isSuccess = isSuccess
  toast.value.show = true
  setTimeout(() => {
    toast.value.show = false
  }, 5000)
}

const handleLogin = () => {
  userStore.login()
}

const handleLogout = () => {
  userStore.logout()
}

onMounted(() => {
  userStore.checkLogin()
  registerWxWork()
})
</script>
