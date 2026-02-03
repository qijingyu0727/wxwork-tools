import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/login-error',
    name: 'LoginError',
    component: () => import('@/views/LoginError.vue')
  },
  {
    path: '/logout-success',
    name: 'LogoutSuccess',
    component: () => import('@/views/LogoutSuccess.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
