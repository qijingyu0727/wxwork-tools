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

      <!-- 指标卡区域 -->
      <div v-if="chatId && customerData" class="metrics-container">
        <!-- 第一行：客户名称、订阅到期、是否验收 -->
        <div class="metric-card info">
          <div class="metric-header">
            <h3>客户名称</h3>
            <span v-if="latestVersion" class="version-badge">{{ latestVersion }}</span>
          </div>
          <div class="metric-value">{{ customerData.name }}</div>
        </div>
        <div class="metric-card success">
          <h3>订阅到期</h3>
          <div class="metric-value">{{ customerData.subscriptionEndDate }}</div>
        </div>
        <div class="metric-card warning">
          <h3>是否验收</h3>
          <div class="metric-value">{{ customerData.isAccepted || '否' }}</div>
        </div>
        <!-- 第二行：工单、需求、缺陷 -->
        <div class="metric-card error">
          <h3>工单</h3>
          <div class="metric-value">{{ (customerData.notResolvedTicketCount || 0) }}/{{ (customerData.allTicketCount || 0) }}</div>
        </div>
        <div class="metric-card info">
          <h3>需求</h3>
          <div class="metric-value">{{ (customerData.notResolvedIssueCount || 0) }}/{{ (customerData.allIssueCount || 0) }}</div>
        </div>
        <div class="metric-card success">
          <h3>缺陷</h3>
          <div class="metric-value">{{ (customerData.notResolvedBugCount || 0) }}/{{ (customerData.allBugCount || 0) }}</div>
        </div>
      </div>

      <!-- Tab 页区域 -->
      <div class="tab-container">
        <div class="tab-header">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            :class="['tab-button', activeTab === tab.id ? 'active' : '']"
            @click="activeTab = tab.id"
          >
            {{ tab.name }}
          </button>
        </div>
        <div class="tab-content">
          <!-- 实施 Tab -->
          <div v-if="activeTab === 'implementation'" class="tab-pane active">
            <div v-if="maintenanceLoading" class="tab-placeholder">
              <i class="fa fa-spinner fa-spin text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">加载中...</p>
            </div>
            <div v-else-if="maintenanceRecords.length === 0" class="tab-placeholder">
              <i class="fa fa-inbox text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">暂无实施记录</p>
            </div>
            <div v-else class="maintenance-list">
              <div v-for="record in maintenanceRecords" :key="record.id" class="maintenance-card">
                <div class="maintenance-header">
                  <span class="maintenance-template">{{ record.template }}</span>
                  <span :class="['maintenance-status', 'status-' + (record.status || '').toLowerCase()]">{{ translateStatus(record.status) }}</span>
                </div>
                <div class="maintenance-info">
                  <div class="info-row">
                    <span class="info-label">版本</span>
                    <span class="info-value">{{ record.version || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">部署方式</span>
                    <span class="info-value">{{ translateDeploymentMethod(record.deploymentMethod) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">部署时间</span>
                    <span class="info-value">{{ record.deploymentTime || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">实施人</span>
                    <span class="info-value">{{ record.creatorName || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">创建时间</span>
                    <span class="info-value">{{ record.createTime || '-' }}</span>
                  </div>
                </div>
                <div v-if="record.content" class="maintenance-content">
                  <div class="info-label">实施内容</div>
                  <pre class="content-text">{{ record.content }}</pre>
                </div>
              </div>
            </div>
          </div>

          <!-- 维护 Tab -->
          <div v-if="activeTab === 'maintenance'" class="tab-pane active">
            <div v-if="serviceLoading" class="tab-placeholder">
              <i class="fa fa-spinner fa-spin text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">加载中...</p>
            </div>
            <div v-else-if="serviceRecords.length === 0" class="tab-placeholder">
              <i class="fa fa-inbox text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">暂无维护记录</p>
            </div>
            <div v-else class="maintenance-list">
              <div v-for="record in serviceRecords" :key="record.id" class="maintenance-card">
                <div class="maintenance-header">
                  <span class="maintenance-template">{{ record.maintenanceTitle }}</span>
                </div>
                <div class="maintenance-info">
                  <div class="info-row">
                    <span class="info-label">维护类型</span>
                    <span class="info-value">{{ record.maintenanceTypes || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">版本</span>
                    <span class="info-value">{{ record.maintenanceVersion || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">维护时间</span>
                    <span class="info-value">{{ record.maintenanceTime || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">创建人</span>
                    <span class="info-value">{{ record.creatorName || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">创建时间</span>
                    <span class="info-value">{{ record.createTime || '-' }}</span>
                  </div>
                </div>
                <div v-if="record.maintenanceContext" class="maintenance-content">
                  <div class="info-label">维护内容</div>
                  <pre class="content-text">{{ record.maintenanceContext }}</pre>
                </div>
              </div>
            </div>
          </div>

          <!-- 工单 Tab -->
          <div v-if="activeTab === 'ticket'" class="tab-pane active">
            <!-- 筛选栏 -->
            <div class="ticket-filter-bar">
              <div class="filter-label">筛选：</div>
              <button
                :class="['filter-btn', ticketFilter === 'all' ? 'active' : '']"
                @click="ticketFilter = 'all'"
              >
                全部 ({{ tickets.length }})
              </button>
              <button
                :class="['filter-btn', ticketFilter === 'unresolved' ? 'active' : '']"
                @click="ticketFilter = 'unresolved'"
              >
                未解决 ({{ tickets.filter(t => !t.resolved).length }})
              </button>
              <button
                :class="['filter-btn', ticketFilter === 'resolved' ? 'active' : '']"
                @click="ticketFilter = 'resolved'"
              >
                已解决 ({{ tickets.filter(t => t.resolved).length }})
              </button>
            </div>

            <div v-if="ticketsLoading" class="tab-placeholder">
              <i class="fa fa-spinner fa-spin text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">加载中...</p>
            </div>
            <div v-else-if="getFilteredTickets().length === 0" class="tab-placeholder">
              <i class="fa fa-inbox text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">暂无工单</p>
            </div>
            <div v-else class="maintenance-list">
              <div v-for="ticket in getFilteredTickets()" :key="ticket.id" class="maintenance-card">
                <div class="maintenance-header" @click="toggleTicketExpand(ticket.id)" style="cursor: pointer;">
                  <span class="maintenance-template">{{ ticket.title }}</span>
                  <span :class="['maintenance-status', ticket.resolved ? 'status-resolved' : 'status-pending']">
                    {{ ticket.resolved ? '已解决' : '未解决' }}
                  </span>
                  <i :class="['fa', expandedTickets.has(ticket.id) ? 'fa-chevron-up' : 'fa-chevron-down', 'ml-2']"></i>
                </div>
                <div class="maintenance-info">
                  <div class="info-row">
                    <span class="info-label">问题分类</span>
                    <span class="info-value">{{ ticket.issueCategory || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">负责人</span>
                    <span class="info-value">{{ ticket.ownerName || '-' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">紧急度</span>
                    <span class="info-value">{{ ticket.urgent ? '紧急' : '普通' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">客户情绪</span>
                    <span class="info-value">{{ translateSentiment(ticket.customerSentiment) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">创建时间</span>
                    <span class="info-value">{{ ticket.createdAt || '-' }}</span>
                  </div>
                </div>
                <div v-if="ticket.description" class="maintenance-content">
                  <div class="info-label">问题描述</div>
                  <pre class="content-text">{{ ticket.description }}</pre>
                </div>
                <!-- 工单日志 -->
                <div v-if="expandedTickets.has(ticket.id)" class="ticket-logs">
                  <div class="info-label" style="margin-top: 12px;">工单流转记录</div>
                  <div v-if="!ticketLogs[ticket.id] || ticketLogs[ticket.id].length === 0" class="text-gray-400 text-sm mt-2">
                    暂无流转记录
                  </div>
                  <div v-else class="logs-timeline">
                    <div v-for="log in getFilteredLogs(ticketLogs[ticket.id])" :key="log.id" class="log-item">
                      <div class="log-time">{{ log.createdAt }}</div>
                      <div class="log-content">
                        <span class="log-action">{{ formatLogEntry(log) }}</span>
                        <span v-if="log.modifiedByName" class="log-operator"> ({{ log.modifiedByName }})</span>
                      </div>
                      <div v-if="log.comment" class="log-comment">{{ log.comment }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 需求和缺陷 Tab 占位符 -->
          <div v-for="tab in tabs.filter(t => t.id !== 'implementation' && t.id !== 'maintenance' && t.id !== 'ticket')" :key="tab.id" :class="['tab-pane', activeTab === tab.id ? 'active' : '']">
            <div class="tab-placeholder">
              <i class="fa fa-hourglass-half text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">敬请期待</p>
            </div>
          </div>
        </div>
      </div>

    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { jsapiApi, docApi } from '@/api/doc'
import CryptoJS from 'crypto-js'
import * as ww from '@wecom/jssdk'
import '@/styles/home.css'

const corpId = ref('')
const agentId = ref('')
const chatId = ref('')
const loading = ref(false)
const customerData = ref(null)
const maintenanceRecords = ref([])
const maintenanceLoading = ref(false)
const serviceRecords = ref([])
const serviceLoading = ref(false)
const tickets = ref([])
const ticketsLoading = ref(false)
const ticketFilter = ref('unresolved') // 'all', 'resolved', 'unresolved'
const expandedTickets = ref(new Set())
const ticketLogs = ref({})
const activeTab = ref('implementation')
const tabs = ref([
  { id: 'implementation', name: '实施' },
  { id: 'maintenance', name: '维护' },
  { id: 'ticket', name: '工单' },
  { id: 'requirement', name: '需求' },
  { id: 'defect', name: '缺陷' }
])

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

const getCustomerData = async (extChatId) => {
  try {
    const res = await docApi.getCustomerData(extChatId)
    if (res.success) {
      customerData.value = res.data
    } else {
      showToast('获取客户数据失败：' + res.message, false)
      customerData.value = null
    }
  } catch (err) {
    showToast('获取客户数据异常：' + (err.message || err), false)
    customerData.value = null
  }
}

const getMaintenanceRecords = async (extChatId) => {
  maintenanceLoading.value = true
  try {
    const res = await docApi.getMaintenanceRecords(extChatId)
    if (res.success) {
      maintenanceRecords.value = res.data || []
    } else {
      maintenanceRecords.value = []
    }
  } catch (err) {
    maintenanceRecords.value = []
  } finally {
    maintenanceLoading.value = false
  }
}

const getServiceRecords = async (extChatId) => {
  serviceLoading.value = true
  try {
    const res = await docApi.getServiceRecords(extChatId)
    if (res.success) {
      serviceRecords.value = res.data || []
    } else {
      serviceRecords.value = []
    }
  } catch (err) {
    serviceRecords.value = []
  } finally {
    serviceLoading.value = false
  }
}

const getTickets = async (extChatId) => {
  ticketsLoading.value = true
  try {
    const res = await docApi.getTickets(extChatId)
    if (res.success) {
      tickets.value = res.data || []
    } else {
      tickets.value = []
    }
  } catch (err) {
    tickets.value = []
  } finally {
    ticketsLoading.value = false
  }
}

const getTicketLogs = async (ticketId) => {
  try {
    const res = await docApi.getTicketLogs(ticketId)
    if (res.success) {
      ticketLogs.value[ticketId] = res.data || []
    }
  } catch (err) {
    ticketLogs.value[ticketId] = []
  }
}

const toggleTicketExpand = (ticketId) => {
  if (expandedTickets.value.has(ticketId)) {
    expandedTickets.value.delete(ticketId)
  } else {
    expandedTickets.value.add(ticketId)
    if (!ticketLogs.value[ticketId]) {
      getTicketLogs(ticketId)
    }
  }
}

const getFilteredTickets = () => {
  if (ticketFilter.value === 'all') {
    return tickets.value
  } else if (ticketFilter.value === 'resolved') {
    return tickets.value.filter(t => t.resolved)
  } else if (ticketFilter.value === 'unresolved') {
    return tickets.value.filter(t => !t.resolved)
  }
  return tickets.value
}

const translateSentiment = (sentiment) => {
  const sentimentMap = {
    'negative': '消极',
    'positive': '积极',
    'neutral': '中立',
    'angry': '愤怒',
    'satisfied': '满意'
  }
  return sentimentMap[sentiment?.toLowerCase()] || sentiment || '-'
}

const translateStatus = (status) => {
  const statusMap = {
    'deployed': '已部署',
    'pending': '待处理',
    'in_progress': '进行中',
    'completed': '已完成',
    'failed': '失败'
  }
  return statusMap[status?.toLowerCase()] || status || '-'
}

const translateDeploymentMethod = (method) => {
  const methodMap = {
    'on_site': '现场部署',
    'remote': '远程部署',
    'cloud': '云部署',
    'hybrid': '混合部署'
  }
  return methodMap[method?.toLowerCase()] || method || '-'
}

const latestVersion = computed(() => {
  if (maintenanceRecords.value.length === 0) return ''
  const latest = maintenanceRecords.value[0]
  return latest?.version || ''
})

const ticketStatusMap = {
  '0': '无效工单',
  '1': '待确认',
  '2': '跟进中',
  '3': '跨团队跟进中',
  '4': '已归档'
}

const logActionMap = {
  'status_change': '状态变更',
  'status_changed': '状态变更',
  'assign': '分配',
  'assigned': '分配',
  'reassign': '重新分配',
  'reassigned': '重新分配',
  'comment': '评论',
  'commented': '评论',
  'resolve': '解决',
  'resolved': '已解决',
  'create': '创建',
  'created': '创建',
  'close': '关闭',
  'closed': '已关闭',
  'reopen': '重新打开',
  'reopened': '重新打开',
  'update': '更新',
  'updated': '更新',
  'follow_up': '跟进中',
  'follow': '跟进',
  'pending': '待处理',
  'archive': '归档',
  'archived': '已归档',
  'transfer': '转交',
  'transferred': '转交',
  'escalate': '升级',
  'escalated': '已升级',
  'invalid': '无效',
  'confirm': '确认',
  'confirmed': '已确认',
  'reject': '拒绝',
  'rejected': '已拒绝',
  'reply': '回复',
  'replied': '已回复',
  'merge': '合并',
  'merged': '已合并',
  'split': '拆分',
  'owner_change': '负责人变更',
  'owner_changed': '负责人变更',
  'priority_change': '优先级变更',
  'priority_changed': '优先级变更',
  'tag_add': '添加标签',
  'tag_remove': '移除标签',
  'customer_sentiment_change': '客户情绪变化',
  'customer_sentiment_changed': '客户情绪变化',
  'urgent_change': '紧急度变更',
  'urgent_changed': '紧急度变更',
  'category_change': '分类变更',
  'category_changed': '分类变更',
  'title_change': '标题变更',
  'title_changed': '标题变更',
  'description_change': '描述变更',
  'description_changed': '描述变更'
}

const sentimentMap = {
  'negative': '负面',
  'neutral': '中性',
  'positive': '正面',
  'angry': '愤怒',
  'satisfied': '满意',
  'frustrated': '沮丧',
  'happy': '开心',
  'confused': '困惑'
}

const translateLogAction = (action) => {
  return logActionMap[action?.toLowerCase()] || action || '-'
}

const translateLogValue = (action, value) => {
  if (ticketStatusMap[value] !== undefined) {
    return ticketStatusMap[value]
  }
  return value
}

const getFilteredLogs = (logs) => {
  if (!logs || logs.length === 0) return []
  return logs.filter(log => {
    const name = log.modifiedByName?.toLowerCase() || ''
    if (name.includes('系统') || name.includes('system') || name.includes('自动')) {
      return false
    }
    return true
  })
}

const formatLogEntry = (log) => {
  const action = log.action?.toLowerCase()
  const actionText = logActionMap[action] || log.action || '-'

  if (!log.value) {
    return actionText
  }

  try {
    const valueObj = JSON.parse(log.value)
    if (valueObj.old !== undefined && valueObj.new !== undefined) {
      const oldVal = String(valueObj.old)
      const newVal = String(valueObj.new)

      // 判断是状态变更还是情绪变更
      const isSentiment = action?.includes('sentiment')

      let oldText, newText
      if (isSentiment) {
        oldText = sentimentMap[oldVal.toLowerCase()] || oldVal
        newText = sentimentMap[newVal.toLowerCase()] || newVal
      } else {
        oldText = ticketStatusMap[oldVal] || sentimentMap[oldVal.toLowerCase()] || oldVal
        newText = ticketStatusMap[newVal] || sentimentMap[newVal.toLowerCase()] || newVal
      }

      return `${actionText}（${oldText} -> ${newText}）`
    }
    return `${actionText}（${log.value}）`
  } catch (e) {
    if (ticketStatusMap[log.value] !== undefined) {
      return `${actionText}（${ticketStatusMap[log.value]}）`
    }
    if (sentimentMap[log.value?.toLowerCase()] !== undefined) {
      return `${actionText}（${sentimentMap[log.value.toLowerCase()]}）`
    }
    return `${actionText}（${log.value}）`
  }
}

const getCurExternalChat = () => {
  loading.value = true
  try {
      // ww.getCurExternalChat({
      //   success(result) {
      //     // 成功回调，result.errMsg 固定格式为"方法名:ok"
      //     chatId.value = result.chatId
      //     showToast('获取群聊 chatID 成功！', true)
      //     console.log('当前群聊ID:', result.chatId)
      //     loading.value = false
      //   },
      //   fail(result) {
      //     // 失败回调，通过 result.errMsg 查看失败详情
      //     showToast('获取群聊 chatID 失败！'+result.errMsg, false)
      //     console.error('调用失败:', result)
      //     loading.value = false
      //   }
      // })
    chatId.value = 'wrVkCUDAAAD4yxwdCwcUTogAWeBxwo1A'
    showToast('获取群聊 chatID 成功！', true)
    console.log('当前群聊ID:', chatId.value)
    // 获取客户数据
    getCustomerData(chatId.value)
    // 获取实施记录
    getMaintenanceRecords(chatId.value)
    // 获取维护记录
    getServiceRecords(chatId.value)
    // 获取工单
    getTickets(chatId.value)
  } catch (err) {
    showToast('异常：'+(err.message || err), false)
    console.error('异常:', err)
  } finally {
    loading.value = false
  }
}

getCurExternalChat();

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
