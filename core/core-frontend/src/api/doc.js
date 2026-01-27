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
