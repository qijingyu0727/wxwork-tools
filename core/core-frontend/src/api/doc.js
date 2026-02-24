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

  updateTicket: (ticketId, data) => {
    return request.put(`/api/chat-group/tickets/${ticketId}`, data)
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
