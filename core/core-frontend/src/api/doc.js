import request from '@/utils/request'

export const docApi = {
  createDoc: (data) => {
    return request.post('/api/doc/create', data)
  },

  searchDocs: (params) => {
    return request.get('/api/doc/search', { params })
  },

  getCustomerData: (extChatId) => {
    return request.get('/api/chat-group/customer-data', { params: { extChatId } })
  },

  getAcceptanceStatus: (extChatId) => {
    return request.get('/api/chat-group/acceptance-status', { params: { extChatId } })
  },

  getMaintenanceRecords: (extChatId) => {
    return request.get('/api/chat-group/maintenance-records', { params: { extChatId } })
  },

  getServiceRecords: (extChatId) => {
    return request.get('/api/chat-group/service-records', { params: { extChatId } })
  },

  getTickets: (extChatId) => {
    return request.get('/api/chat-group/tickets', { params: { extChatId } })
  },

  getTicketLogs: (ticketId) => {
    return request.get('/api/chat-group/ticket-logs', { params: { ticketId } })
  },

  getStaffList: () => {
    return request.get('/api/chat-group/staff-list')
  },

  getIssueTickets: (extChatId) => {
    return request.get('/api/chat-group/issue-tickets', { params: { extChatId } })
  },

  getBugTickets: (extChatId) => {
    return request.get('/api/chat-group/bug-tickets', { params: { extChatId } })
  },

  updateTicket: (ticketId, data) => {
    return request.put(`/api/chat-group/tickets/${ticketId}`, data)
  },

  updateIssueTicket: (ticketId, data) => {
    return request.put(`/api/chat-group/issue-tickets/${ticketId}`, data)
  },

  updateBugTicket: (ticketId, data) => {
    return request.put(`/api/chat-group/bug-tickets/${ticketId}`, data)
  },

  // 新增维护记录
  createMaintenanceRecord: (data) => {
    return request.post('/api/chat-group/maintenance-records', data)
  },

  // 获取产品版本列表
  getProductVersions: (productId, extChatId) => {
    return request.get('/api/chat-group/product-versions', { params: { productId, extChatId } })
  },

  // 工具模块-发送邮件
  sendToolMail: (data) => {
    return request.post('/api/tools/send-mail', data)
  },

  // 工具模块-获取默认抄送邮箱
  getMailDefaultCc: (extChatId) => {
    return request.get('/api/tools/mail-default-cc', { params: { extChatId } })
  },

  // 工具模块-获取验收报告
  getAcceptanceReport: (data) => {
    return request.post('/api/tools/acceptance-report', data)
  }
}

export const jsapiApi = {
  getJsapiTicket: () => {
    return request.get('/api/jsapi/get-ticket')
  },

  getAgentJsapiTicket: () => {
    return request.get('/api/jsapi/get-agent-ticket')
  },

  getCorpId: () => {
    return request.get('/api/jsapi/get-corp-id')
  },

  getAgentId: () => {
    return request.get('/api/jsapi/get-agent-id')
  }
}
