import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useUserStore = defineStore('user', () => {
  const isLoggedIn = ref(false)
  const userInfo = ref(null)
  const isAdmin = ref(false)

  const checkLogin = async () => {
    // try {
    //   const response = await request.get('/wechat/work/login/check-login')
    //   if (response.success) {
    //     isLoggedIn.value = true
    //     userInfo.value = response.data
    //     isAdmin.value = response.data.isAdmin || false
    //     return true
    //   }
    //   return false
    // } catch (error) {
    //   console.error('Check login failed:', error)
    //   return false
    // }
    return true
  }

  const logout = () => {
    isLoggedIn.value = false
    userInfo.value = null
    isAdmin.value = false
    window.location.href = '/wechat/work/login/logout'
  }

  const login = () => {
    window.location.href = '/wechat/work/login/generate-qrcode'
  }

  return {
    isLoggedIn,
    userInfo,
    isAdmin,
    checkLogin,
    logout,
    login
  }
})
