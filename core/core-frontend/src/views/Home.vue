<template>
  <div class="home-container">
    <div id="toast" :class="['toast', toast.show ? 'show' : '', toast.isSuccess ? 'toast-success' : 'toast-error']">
      <i :class="[toast.isSuccess ? 'fa fa-check-circle' : 'fa fa-exclamation-circle', 'mr-3 text-lg', toast.isSuccess ? 'text-green-500' : 'text-red-500']"></i>
      <span class="text-sm">{{ toast.message }}</span>
    </div>

    <main class="home-main">
      <div class="home-title">
        <h2>
          企业微信群聊侧边栏工具
        </h2>
        <div v-if="userStore.isLoggedIn" class="title-avatar">
          <img :src="userStore.userInfo?.avatar || defaultAvatar" alt="用户头像" class="user-avatar">
        </div>
      </div>

      <div class="home-actions">
        <div v-if="userStore.isLoggedIn" class="user-info">
          <span>{{ userStore.userInfo?.name || userStore.userInfo?.UserId }}</span>
        </div>
        <button v-if="!userStore.isLoggedIn" @click="handleLogin" class="login-btn">
          <i class="fa fa-wechat"></i>
          企业微信登录
        </button>
      </div>

      <!-- 客户信息区域 -->
      <div v-if="chatId" class="customer-info-section">
        <!-- 客户名称和标签 -->
        <div class="customer-header">
          <h2 class="customer-name">{{ customerData?.name || '-' }}</h2>
          <div class="customer-badges">
            <span v-if="customerData?.isAccepted" class="badge badge-success">{{ customerData.isAccepted }}</span>
            <span v-if="latestVersion" class="badge badge-primary badge-clickable" @click="handleVersionClick">{{ latestVersion }}</span>
          </div>
        </div>

        <!-- 统计数据 -->
        <div class="stats-section">
          <div class="stat-item stat-info">
            <div class="stat-icon">
              <i class="fa fa-calendar"></i>
            </div>
            <div class="stat-content">
              <div class="stat-label">服务到期</div>
              <div class="stat-value">
                <span class="stat-primary">{{ customerData?.subscriptionEndDate || '-' }}</span>
              </div>
            </div>
          </div>
          <div class="stat-item stat-error stat-item-clickable" @click="handleTicketRowClick">
            <div class="stat-icon">
              <i class="fa fa-ticket"></i>
            </div>
            <div class="stat-content">
              <div class="stat-label stat-label-clickable" @click="handleTicketLabelClick">工单</div>
              <div class="stat-value">
                <span class="stat-critical clickable" @click.stop="handleCriticalClick">{{ getCriticalCount(getTicketBaseList()) }}</span>
                <span class="stat-separator">/</span>
                <span class="stat-warning clickable" @click.stop="handleUnresolvedClick">{{ getUnresolvedCount(getTicketBaseList()) }}</span>
                <span class="stat-separator">/</span>
                <span class="stat-all clickable" @click.stop="handleAllClick">{{ getResolvedCount(getTicketBaseList()) }}</span>
              </div>
            </div>
          </div>

          <div class="stat-item stat-info stat-item-clickable" @click="handleRequirementRowClick">
            <div class="stat-icon">
              <i class="fa fa-lightbulb-o"></i>
            </div>
            <div class="stat-content">
              <div class="stat-label stat-label-clickable" @click="handleRequirementLabelClick">需求</div>
              <div class="stat-value">
                <span class="stat-critical clickable" @click.stop="handleRequirementCriticalClick">{{ getCriticalCount(issueTickets) }}</span>
                <span class="stat-separator">/</span>
                <span class="stat-warning clickable" @click.stop="handleRequirementUnresolvedClick">{{ getUnresolvedCount(issueTickets) }}</span>
                <span class="stat-separator">/</span>
                <span class="stat-all clickable" @click.stop="handleRequirementResolvedClick">{{ getResolvedCount(issueTickets) }}</span>
              </div>
            </div>
          </div>

          <div class="stat-item stat-warning stat-item-clickable" @click="handleDefectRowClick">
            <div class="stat-icon">
              <i class="fa fa-bug"></i>
            </div>
            <div class="stat-content">
              <div class="stat-label stat-label-clickable" @click="handleDefectLabelClick">缺陷</div>
              <div class="stat-value">
                <span class="stat-critical clickable" @click.stop="handleDefectCriticalClick">{{ getCriticalCount(bugTickets) }}</span>
                <span class="stat-separator">/</span>
                <span class="stat-warning clickable" @click.stop="handleDefectUnresolvedClick">{{ getUnresolvedCount(bugTickets) }}</span>
                <span class="stat-separator">/</span>
                <span class="stat-all clickable" @click.stop="handleDefectResolvedClick">{{ getResolvedCount(bugTickets) }}</span>
              </div>
            </div>
          </div>
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
            <!-- 新增维护按钮 -->
            <div class="add-maintenance-section">
              <button class="btn-add-maintenance" @click="handleAddMaintenance">
                <span>+ 新增</span>
              </button>
            </div>

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
                全部 ({{ getTicketBaseList().length }})
              </button>
              <button
                :class="['filter-btn', ticketFilter === 'critical' ? 'active' : '']"
                @click="ticketFilter = 'critical'"
              >
                重点事件 ({{ getCriticalCount(getTicketBaseList()) }})
              </button>
              <button
                :class="['filter-btn', ticketFilter === 'unresolved' ? 'active' : '']"
                @click="ticketFilter = 'unresolved'"
              >
                未解决 ({{ getUnresolvedCount(getTicketBaseList()) }})
              </button>
              <button
                :class="['filter-btn', ticketFilter === 'resolved' ? 'active' : '']"
                @click="ticketFilter = 'resolved'"
              >
                已解决 ({{ getResolvedCount(getTicketBaseList()) }})
              </button>
            </div>

            <div class="ticket-search-bar">
              <input
                v-model="ticketSearchInput"
                type="text"
                class="ticket-search-input"
                placeholder="请输入工单标题关键词"
                @keyup.enter="applyTicketSearch"
              />
              <button class="ticket-search-btn" @click="applyTicketSearch">
                <i class="fa fa-search"></i>
                <span>搜索</span>
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
              <div v-for="ticket in getFilteredTickets()" :key="ticket.id" class="maintenance-card" @click="toggleTicketExpand(ticket.id)" style="cursor: pointer;">
                <div class="maintenance-header">
                  <span class="maintenance-template">{{ ticket.title }}</span>
                  <div class="maintenance-header-right">
                    <span :class="['maintenance-status', ticket.resolved ? 'status-resolved' : 'status-pending']">
                      {{ ticket.resolved ? '已解决' : '未解决' }}
                    </span>
                    <button class="update-btn" @click.stop="handleUpdateTicket(ticket.id)" title="更新工单">
                      <i class="fa fa-pencil"></i>
                    </button>
                  </div>
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
                  <div class="info-row info-row-inline">
                    <span class="info-label">创建时间</span>
                    <span class="info-value info-value-time">{{ ticket.createdAt || '-' }}</span>
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
                      <div class="log-row log-row-top">
                        <span class="log-time">{{ log.createdAt }}</span>
                        <span :class="['log-action', getLogActionClass(log.action)]">{{ formatLogEntry(log) }}</span>
                      </div>
                      <div v-if="log.comment || log.modifiedByName" class="log-row log-row-bottom">
                        <span v-if="log.comment" class="log-comment-inline">{{ log.comment }}</span>
                        <span v-else></span>
                        <span v-if="log.modifiedByName" class="log-operator">{{ log.modifiedByName }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 需求 Tab -->
          <div v-if="activeTab === 'requirement'" class="tab-pane active">
            <!-- 筛选栏 -->
            <div class="ticket-filter-bar">
              <div class="filter-label">筛选：</div>
              <button
                :class="['filter-btn', issueFilter === 'all' ? 'active' : '']"
                @click="issueFilter = 'all'"
              >
                全部 ({{ issueTickets.length }})
              </button>
              <button
                :class="['filter-btn', issueFilter === 'critical' ? 'active' : '']"
                @click="issueFilter = 'critical'"
              >
                重点事件 ({{ getCriticalCount(issueTickets) }})
              </button>
              <button
                :class="['filter-btn', issueFilter === 'unresolved' ? 'active' : '']"
                @click="issueFilter = 'unresolved'"
              >
                未解决 ({{ getUnresolvedCount(issueTickets) }})
              </button>
              <button
                :class="['filter-btn', issueFilter === 'resolved' ? 'active' : '']"
                @click="issueFilter = 'resolved'"
              >
                已解决 ({{ getResolvedCount(issueTickets) }})
              </button>
            </div>

            <div class="ticket-search-bar">
              <input
                v-model="issueSearchInput"
                type="text"
                class="ticket-search-input"
                placeholder="请输入需求标题关键词"
                @keyup.enter="applyIssueSearch"
              />
              <button class="ticket-search-btn" @click="applyIssueSearch">
                <i class="fa fa-search"></i>
                <span>搜索</span>
              </button>
            </div>

            <div v-if="issueTicketsLoading" class="tab-placeholder">
              <i class="fa fa-spinner fa-spin text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">加载中...</p>
            </div>
            <div v-else-if="getFilteredIssueTickets().length === 0" class="tab-placeholder">
              <i class="fa fa-inbox text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">暂无需求工单</p>
            </div>
            <div v-else class="maintenance-list">
              <div v-for="ticket in getFilteredIssueTickets()" :key="ticket.id" class="maintenance-card" @click="toggleIssueTicketExpand(ticket.id)" style="cursor: pointer;">
                <div class="maintenance-header">
                  <span class="maintenance-template">{{ ticket.title }}</span>
                  <div class="maintenance-header-right">
                    <span :class="['maintenance-status', ticket.resolved ? 'status-resolved' : 'status-pending']">
                      {{ ticket.resolved ? '已解决' : '未解决' }}
                    </span>
                    <button class="update-btn" @click.stop="handleUpdateTicket(ticket.id)" title="更新工单">
                      <i class="fa fa-pencil"></i>
                    </button>
                  </div>
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
                  <div class="info-row info-row-inline">
                    <span class="info-label">创建时间</span>
                    <span class="info-value info-value-time">{{ ticket.createdAt || '-' }}</span>
                  </div>
                  <div class="info-row info-row-inline info-row-full">
                    <span class="info-label">跟踪链接</span>
                    <div class="info-value">
                      <template v-if="ticket.trackingLinks">
                        <div v-for="(link, index) in ticket.trackingLinks.split('\n')" :key="index" v-if="link.trim()">
                          <a :href="link.trim()" target="_blank" rel="noopener noreferrer" class="link">{{ link.trim() }}</a>
                        </div>
                      </template>
                      <span v-else>-</span>
                    </div>
                  </div>
                </div>
                <div v-if="ticket.description" class="maintenance-content">
                  <div class="info-label">问题描述</div>
                  <pre class="content-text">{{ ticket.description }}</pre>
                </div>
                <!-- 工单日志 -->
                <div v-if="expandedIssueTickets.has(ticket.id)" class="ticket-logs">
                  <div class="info-label" style="margin-top: 12px;">工单流转记录</div>
                  <div v-if="!ticketLogs[ticket.id] || ticketLogs[ticket.id].length === 0" class="text-gray-400 text-sm mt-2">
                    暂无流转记录
                  </div>
                  <div v-else class="logs-timeline">
                    <div v-for="log in getFilteredLogs(ticketLogs[ticket.id])" :key="log.id" class="log-item">
                      <div class="log-row log-row-top">
                        <span class="log-time">{{ log.createdAt }}</span>
                        <span :class="['log-action', getLogActionClass(log.action)]">{{ formatLogEntry(log) }}</span>
                      </div>
                      <div v-if="log.comment || log.modifiedByName" class="log-row log-row-bottom">
                        <span v-if="log.comment" class="log-comment-inline">{{ log.comment }}</span>
                        <span v-else></span>
                        <span v-if="log.modifiedByName" class="log-operator">{{ log.modifiedByName }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 缺陷 Tab -->
          <div v-if="activeTab === 'defect'" class="tab-pane active">
            <!-- 筛选栏 -->
            <div class="ticket-filter-bar">
              <div class="filter-label">筛选：</div>
              <button
                :class="['filter-btn', bugFilter === 'all' ? 'active' : '']"
                @click="bugFilter = 'all'"
              >
                全部 ({{ bugTickets.length }})
              </button>
              <button
                :class="['filter-btn', bugFilter === 'critical' ? 'active' : '']"
                @click="bugFilter = 'critical'"
              >
                重点事件 ({{ getCriticalCount(bugTickets) }})
              </button>
              <button
                :class="['filter-btn', bugFilter === 'unresolved' ? 'active' : '']"
                @click="bugFilter = 'unresolved'"
              >
                未解决 ({{ getUnresolvedCount(bugTickets) }})
              </button>
              <button
                :class="['filter-btn', bugFilter === 'resolved' ? 'active' : '']"
                @click="bugFilter = 'resolved'"
              >
                已解决 ({{ getResolvedCount(bugTickets) }})
              </button>
            </div>

            <div class="ticket-search-bar">
              <input
                v-model="bugSearchInput"
                type="text"
                class="ticket-search-input"
                placeholder="请输入缺陷标题关键词"
                @keyup.enter="applyBugSearch"
              />
              <button class="ticket-search-btn" @click="applyBugSearch">
                <i class="fa fa-search"></i>
                <span>搜索</span>
              </button>
            </div>

            <div v-if="bugTicketsLoading" class="tab-placeholder">
              <i class="fa fa-spinner fa-spin text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">加载中...</p>
            </div>
            <div v-else-if="getFilteredBugTickets().length === 0" class="tab-placeholder">
              <i class="fa fa-inbox text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">暂无缺陷工单</p>
            </div>
            <div v-else class="maintenance-list">
              <div v-for="ticket in getFilteredBugTickets()" :key="ticket.id" class="maintenance-card" @click="toggleBugTicketExpand(ticket.id)" style="cursor: pointer;">
                <div class="maintenance-header">
                  <span class="maintenance-template">{{ ticket.title }}</span>
                  <div class="maintenance-header-right">
                    <span :class="['maintenance-status', ticket.resolved ? 'status-resolved' : 'status-pending']">
                      {{ ticket.resolved ? '已解决' : '未解决' }}
                    </span>
                    <button class="update-btn" @click.stop="handleUpdateTicket(ticket.id)" title="更新工单">
                      <i class="fa fa-pencil"></i>
                    </button>
                  </div>
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
                  <div class="info-row info-row-inline">
                    <span class="info-label">创建时间</span>
                    <span class="info-value info-value-time">{{ ticket.createdAt || '-' }}</span>
                  </div>
                  <div class="info-row info-row-inline info-row-full">
                    <span class="info-label">跟踪链接</span>
                    <div class="info-value">
                      <template v-if="ticket.trackingLinks">
                        <div v-for="(link, index) in ticket.trackingLinks.split('\n')" :key="index" v-if="link.trim()">
                          <a :href="link.trim()" target="_blank" rel="noopener noreferrer" class="link">{{ link.trim() }}</a>
                        </div>
                      </template>
                      <span v-else>-</span>
                    </div>
                  </div>
                </div>
                <div v-if="ticket.description" class="maintenance-content">
                  <div class="info-label">问题描述</div>
                  <pre class="content-text">{{ ticket.description }}</pre>
                </div>
                <!-- 工单日志 -->
                <div v-if="expandedBugTickets.has(ticket.id)" class="ticket-logs">
                  <div class="info-label" style="margin-top: 12px;">工单流转记录</div>
                  <div v-if="!ticketLogs[ticket.id] || ticketLogs[ticket.id].length === 0" class="text-gray-400 text-sm mt-2">
                    暂无流转记录
                  </div>
                  <div v-else class="logs-timeline">
                    <div v-for="log in getFilteredLogs(ticketLogs[ticket.id])" :key="log.id" class="log-item">
                      <div class="log-row log-row-top">
                        <span class="log-time">{{ log.createdAt }}</span>
                        <span :class="['log-action', getLogActionClass(log.action)]">{{ formatLogEntry(log) }}</span>
                      </div>
                      <div v-if="log.comment || log.modifiedByName" class="log-row log-row-bottom">
                        <span v-if="log.comment" class="log-comment-inline">{{ log.comment }}</span>
                        <span v-else></span>
                        <span v-if="log.modifiedByName" class="log-operator">{{ log.modifiedByName }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

    </main>

    <!-- 新增维护弹窗 -->
    <div v-if="showAddMaintenanceModal" class="modal-overlay" @click="closeAddMaintenanceModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>新增维护记录</h3>
          <button class="modal-close" @click="closeAddMaintenanceModal">
            <i class="fa fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">维护日期 <span class="required">*</span></label>
            <input type="date" v-model="addMaintenanceForm.maintenanceTime" class="form-input" required />
          </div>

          <div class="form-group">
            <label class="form-label">维护类型 <span class="required">*</span></label>
            <select v-model="addMaintenanceForm.maintenanceTypes" class="form-input" required>
              <option value="升级">升级</option>
              <option value="巡检">巡检</option>
              <option value="需求变更">需求变更</option>
              <option value="问题处理">问题处理</option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">概述 <span class="required">*</span></label>
            <textarea
              v-model="addMaintenanceForm.maintenanceTitle"
              class="form-textarea maintenance-summary-textarea"
              placeholder="请输入维护概述"
              rows="2"
              required
            ></textarea>
          </div>

          <div class="form-group">
            <label class="form-label">选择版本 <span class="required">*</span></label>
            <select v-model="addMaintenanceForm.maintenanceVersion" class="form-input" :disabled="versionsLoading" required>
              <option v-for="version in productVersions" :key="version" :value="version">{{ version }}</option>
            </select>
            <div v-if="versionsLoading" class="text-gray-400 text-sm mt-2">版本加载中...</div>
          </div>

          <div class="form-group">
            <label class="form-label">详细过程记录 <span class="required">*</span></label>
            <textarea v-model="addMaintenanceForm.maintenanceContext" class="form-textarea" placeholder="请输入详细过程记录" rows="3" required></textarea>
          </div>
        </div>
        <div class="modal-footer modal-footer-actions add-maintenance-footer">
          <button class="btn btn-secondary btn-uniform" @click="closeAddMaintenanceModal">取消</button>
          <button class="btn btn-primary btn-resolve btn-uniform" :disabled="addMaintenanceSubmitting" @click="submitAddMaintenance">
            {{ addMaintenanceSubmitting ? '提交中...' : '提交' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 更新工单弹窗 -->
    <div v-if="showUpdateModal" class="modal-overlay" @click="closeUpdateModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ currentTicketTitle }}</h3>
          <button class="modal-close" @click="closeUpdateModal">
            <i class="fa fa-times"></i>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">是否紧急</label>
            <div class="form-radio-group">
              <label class="radio-label">
                <input type="radio" :value="true" v-model="updateForm.urgent" />
                <span>紧急</span>
              </label>
              <label class="radio-label">
                <input type="radio" :value="false" v-model="updateForm.urgent" />
                <span>普通</span>
              </label>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">客户情绪</label>
            <div class="form-radio-group">
              <label class="radio-label">
                <input type="radio" value="positive" v-model="updateForm.customerSentiment" />
                <span>积极</span>
              </label>
              <label class="radio-label">
                <input type="radio" value="neutral" v-model="updateForm.customerSentiment" />
                <span>中性</span>
              </label>
              <label class="radio-label">
                <input type="radio" value="negative" v-model="updateForm.customerSentiment" />
                <span>负面</span>
              </label>
            </div>
          </div>

          <div v-if="activeTab === 'requirement' || activeTab === 'defect'" class="form-group">
            <label class="form-label">跟踪链接</label>
            <textarea
              v-model="updateForm.trackingLinks"
              class="form-textarea"
              placeholder="GitHub/TAPD链接，多个链接请换行"
              rows="3"
            ></textarea>
          </div>

          <div class="form-group">
            <label class="form-label">处理人 <span class="required">*</span></label>
            <div class="autocomplete-wrapper">
              <input
                type="text"
                v-model="updateForm.ownerName"
                @input="handleOwnerInput"
                @focus="showStaffDropdown = true"
                @blur="handleOwnerBlur"
                class="form-input autocomplete-input"
                placeholder="请输入处理人姓名"
                autocomplete="off"
              />
              <i
                class="fa fa-chevron-down autocomplete-icon"
                @mousedown.prevent="toggleStaffDropdown"
              ></i>
              <div v-if="showStaffDropdown && filteredStaffList.length > 0" class="autocomplete-dropdown">
                <div
                  v-for="staff in filteredStaffList"
                  :key="staff"
                  class="autocomplete-item"
                  @mousedown.prevent="selectStaff(staff)"
                >
                  {{ staff }}
                </div>
              </div>
              <div v-if="showStaffDropdown && filteredStaffList.length === 0 && staffList.length === 0" class="autocomplete-dropdown">
                <div class="autocomplete-item autocomplete-empty">
                  加载中...
                </div>
              </div>
              <div v-if="showStaffDropdown && filteredStaffList.length === 0 && staffList.length > 0" class="autocomplete-dropdown">
                <div class="autocomplete-item autocomplete-empty">
                  未找到匹配的员工
                </div>
              </div>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">处理备注 <span class="required">*</span></label>
            <textarea
              v-model="updateForm.comment"
              class="form-textarea"
              placeholder="请输入处理备注"
              rows="4"
            ></textarea>
          </div>
        </div>
        <div class="modal-footer modal-footer-actions">
          <button class="btn btn-action btn-uniform" @click="submitUpdateTicket('follow')">跟进</button>
          <button class="btn btn-action btn-uniform" @click="submitUpdateTicket('cross-team')">跨团队跟进</button>
          <button class="btn btn-primary btn-resolve btn-uniform" @click="submitUpdateTicket('resolve')">确认解决</button>
        </div>
      </div>
    </div>
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
const customerData = ref({})
const maintenanceRecords = ref([])
const maintenanceLoading = ref(false)
const serviceRecords = ref([])
const serviceLoading = ref(false)
const tickets = ref([])
const ticketsLoading = ref(false)
const ticketFilter = ref('unresolved') // 'all', 'resolved', 'unresolved'
const ticketSearchInput = ref('')
const ticketSearchKeyword = ref('')
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

// 需求和缺陷工单相关状态
const issueTickets = ref([])
const issueTicketsLoading = ref(false)
const bugTickets = ref([])
const bugTicketsLoading = ref(false)
const issueFilter = ref('unresolved') // 'all', 'resolved', 'unresolved'
const bugFilter = ref('unresolved') // 'all', 'resolved', 'unresolved'
const issueSearchInput = ref('')
const issueSearchKeyword = ref('')
const bugSearchInput = ref('')
const bugSearchKeyword = ref('')
const expandedIssueTickets = ref(new Set())
const expandedBugTickets = ref(new Set())

// 更新工单弹窗相关状态
const showUpdateModal = ref(false)
const currentTicketId = ref(null)
const currentTicketTitle = ref('')
const staffList = ref([])
const showStaffDropdown = ref(false)
const updateForm = ref({
  urgent: false,
  customerSentiment: 'neutral',
  ownerName: '',
  comment: '',
  trackingLinks: ''
})
const showAddMaintenanceModal = ref(false)
const addMaintenanceSubmitting = ref(false)
const versionsLoading = ref(false)
const productVersions = ref([])
const versionsLoadedProductId = ref(null)
const versionPreloadPromise = ref(null)
const addMaintenanceForm = ref({
  maintenanceTime: '',
  maintenanceTypes: '',
  maintenanceTitle: '',
  maintenanceVersion: '',
  maintenanceContext: ''
})

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
    // console.log('corpId:', corpId.value)
    // console.log('agentId:', agentId.value)

    ww.register({
      corpId: corpId.value,
      agentId: agentId.value,
      jsApiList: ['getCurExternalChat'],
      getConfigSignature,
      getAgentConfigSignature
    })
    getCurExternalChat()
  }
}

const getCustomerData = async (extChatId) => {
  try {
    const res = await docApi.getCustomerData(extChatId)
    if (res.success) {
      customerData.value = res.data
    } else {
      showToast('获取客户数据失败：' + res.message, false)
      customerData.value = {}
    }
  } catch (err) {
    showToast('获取客户数据异常：' + (err.message || err), false)
    customerData.value = {}
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

const syncIssueAndBugTickets = () => {
  issueTickets.value = tickets.value.filter(t => /需求/.test(t?.issueCategory || ''))
  bugTickets.value = tickets.value.filter(t => (t?.issueCategory || '') === '产品缺陷')
}

const resetTicketViewState = () => {
  ticketFilter.value = 'unresolved'
  issueFilter.value = 'unresolved'
  bugFilter.value = 'unresolved'
  ticketSearchInput.value = ''
  ticketSearchKeyword.value = ''
  issueSearchInput.value = ''
  issueSearchKeyword.value = ''
  bugSearchInput.value = ''
  bugSearchKeyword.value = ''
}

const getTickets = async (extChatId) => {
  ticketsLoading.value = true
  try {
    const res = await docApi.getTickets(extChatId)
    if (res.success) {
      tickets.value = res.data || []
      syncIssueAndBugTickets()
    } else {
      tickets.value = []
      syncIssueAndBugTickets()
    }
  } catch (err) {
    tickets.value = []
    syncIssueAndBugTickets()
  } finally {
    ticketsLoading.value = false
  }
}

const getIssueTickets = async (extChatId) => {
  issueTicketsLoading.value = true
  try {
    const res = await docApi.getIssueTickets(extChatId)
    if (res.success) {
      const list = Array.isArray(res.data) ? res.data : []
      issueTickets.value = list.length > 0
        ? list
        : tickets.value.filter(t => /需求/.test(t?.issueCategory || ''))
    } else {
      issueTickets.value = tickets.value.filter(t => /需求/.test(t?.issueCategory || ''))
    }
  } catch (err) {
    issueTickets.value = tickets.value.filter(t => /需求/.test(t?.issueCategory || ''))
  } finally {
    issueTicketsLoading.value = false
  }
}

const getBugTickets = async (extChatId) => {
  bugTicketsLoading.value = true
  try {
    const res = await docApi.getBugTickets(extChatId)
    if (res.success) {
      const list = Array.isArray(res.data) ? res.data : []
      bugTickets.value = list.length > 0
        ? list
        : tickets.value.filter(t => (t?.issueCategory || '') === '产品缺陷')
    } else {
      bugTickets.value = tickets.value.filter(t => (t?.issueCategory || '') === '产品缺陷')
    }
  } catch (err) {
    bugTickets.value = tickets.value.filter(t => (t?.issueCategory || '') === '产品缺陷')
  } finally {
    bugTicketsLoading.value = false
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

const applyTicketSearch = () => {
  ticketSearchKeyword.value = ticketSearchInput.value.trim().toLowerCase()
}

const applyIssueSearch = () => {
  issueSearchKeyword.value = issueSearchInput.value.trim().toLowerCase()
}

const applyBugSearch = () => {
  bugSearchKeyword.value = bugSearchInput.value.trim().toLowerCase()
}

const isResolvedTicket = (ticket) => {
  const value = ticket?.resolved
  if (value === true || value === 1 || value === '1') return true
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase()
    if (normalized === 'true') return true
    if (normalized === 'false' || normalized === '') return false
  }
  return false
}

const isCriticalTicket = (ticket) => Number(ticket?.status) === 3
const isIssueCategory = (ticket) => (ticket?.issueCategory || '') === '功能需求'
const isBugCategory = (ticket) => (ticket?.issueCategory || '') === '产品缺陷'
const getTicketBaseList = () => tickets.value.filter(t => !isIssueCategory(t) && !isBugCategory(t))

const getResolvedCount = (list) => (Array.isArray(list) ? list.filter(isResolvedTicket).length : 0)
const getUnresolvedCount = (list) => (Array.isArray(list) ? list.filter(t => !isResolvedTicket(t)).length : 0)
const getCriticalCount = (list) => (Array.isArray(list) ? list.filter(isCriticalTicket).length : 0)

const getFilteredTickets = () => {
  const ticketBaseList = getTicketBaseList()
  let filtered = ticketBaseList

  if (ticketFilter.value === 'all') {
    filtered = ticketBaseList
  } else if (ticketFilter.value === 'resolved') {
    filtered = ticketBaseList.filter(isResolvedTicket)
  } else if (ticketFilter.value === 'unresolved') {
    filtered = ticketBaseList.filter(t => !isResolvedTicket(t))
  } else if (ticketFilter.value === 'critical') {
    filtered = ticketBaseList.filter(isCriticalTicket)
  }

  if (!ticketSearchKeyword.value) {
    return filtered
  }

  return filtered.filter(ticket =>
    (ticket.title || '').toLowerCase().includes(ticketSearchKeyword.value)
  )
}

const getFilteredIssueTickets = () => {
  let filtered = issueTickets.value

  if (issueFilter.value === 'all') {
    filtered = issueTickets.value
  } else if (issueFilter.value === 'resolved') {
    filtered = issueTickets.value.filter(isResolvedTicket)
  } else if (issueFilter.value === 'unresolved') {
    filtered = issueTickets.value.filter(t => !isResolvedTicket(t))
  } else if (issueFilter.value === 'critical') {
    filtered = issueTickets.value.filter(isCriticalTicket)
  }

  if (!issueSearchKeyword.value) {
    return filtered
  }

  return filtered.filter(ticket =>
    (ticket.title || '').toLowerCase().includes(issueSearchKeyword.value)
  )
}

const getFilteredBugTickets = () => {
  let filtered = bugTickets.value

  if (bugFilter.value === 'all') {
    filtered = bugTickets.value
  } else if (bugFilter.value === 'resolved') {
    filtered = bugTickets.value.filter(isResolvedTicket)
  } else if (bugFilter.value === 'unresolved') {
    filtered = bugTickets.value.filter(t => !isResolvedTicket(t))
  } else if (bugFilter.value === 'critical') {
    filtered = bugTickets.value.filter(isCriticalTicket)
  }

  if (!bugSearchKeyword.value) {
    return filtered
  }

  return filtered.filter(ticket =>
    (ticket.title || '').toLowerCase().includes(bugSearchKeyword.value)
  )
}

const toggleIssueTicketExpand = (ticketId) => {
  if (expandedIssueTickets.value.has(ticketId)) {
    expandedIssueTickets.value.delete(ticketId)
  } else {
    expandedIssueTickets.value.add(ticketId)
    if (!ticketLogs.value[ticketId]) {
      getTicketLogs(ticketId)
    }
  }
}

const toggleBugTicketExpand = (ticketId) => {
  if (expandedBugTickets.value.has(ticketId)) {
    expandedBugTickets.value.delete(ticketId)
  } else {
    expandedBugTickets.value.add(ticketId)
    if (!ticketLogs.value[ticketId]) {
      getTicketLogs(ticketId)
    }
  }
}

const translateSentiment = (sentiment) => {
  const sentimentMap = {
    'negative': '负面',
    'positive': '积极',
    'neutral': '中性',
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
  const version = latest?.version || ''
  if (!version) return ''

  // 提取纯版本号 (如 v3.5.7 或 3.5.7)
  const versionMatch = version.match(/v?(\d+\.\d+\.\d+)/)
  const versionNum = versionMatch ? `v${versionMatch[1]}` : version

  // 判断架构类型
  const lowerVersion = version.toLowerCase()
  if (lowerVersion.includes('arm')) {
    return `${versionNum}-arm64`
  } else if (lowerVersion.includes('x86') || lowerVersion.includes('amd')) {
    return `${versionNum}-x86`
  }
  // 纯版本号，不加架构后缀
  return versionNum
})

const ticketStatusMap = {
  '0': '无效工单',
  '1': '待确认',
  '2': '跟进中',
  '3': '跨团队跟进中',
  '4': '已处理'
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
  'comment_added': '添加备注',
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
  'positive': '积极',
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
  // 倒序排列，最近的在前
  const sorted = [...logs].sort((a, b) => {
    return new Date(b.createdAt) - new Date(a.createdAt)
  })
  return sorted.filter(log => {
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
    // 过滤空对象 {}
    if (Object.keys(valueObj).length === 0) {
      return actionText
    }
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
    // 过滤 "{}" 字符串
    if (log.value === '{}') {
      return actionText
    }
    if (ticketStatusMap[log.value] !== undefined) {
      return `${actionText}（${ticketStatusMap[log.value]}）`
    }
    if (sentimentMap[log.value?.toLowerCase()] !== undefined) {
      return `${actionText}（${sentimentMap[log.value.toLowerCase()]}）`
    }
    return `${actionText}（${log.value}）`
  }
}

const getLogActionClass = (action) => {
  const a = action?.toLowerCase()
  if (a?.includes('sentiment')) return 'action-sentiment'
  if (a?.includes('status') || a === 'follow_up' || a === 'follow') return 'action-status'
  if (a === 'create' || a === 'created') return 'action-create'
  if (a === 'resolve' || a === 'resolved' || a === 'close' || a === 'closed' || a === 'archive' || a === 'archived') return 'action-resolve'
  if (a === 'assign' || a === 'assigned' || a === 'reassign' || a === 'reassigned' || a === 'owner_change' || a === 'owner_changed') return 'action-assign'
  if (a === 'urgent_change' || a === 'urgent_changed' || a === 'priority_change' || a === 'priority_changed') return 'action-urgent'
  return ''
}

// 本地调试开关：true 时使用写死 chatId，false 时走企业微信 getCurExternalChat
const LOCAL_DEBUG_CHAT = false
const DEBUG_CHAT_ID = 'wrVkCUDAAAUhda0surI_P9YfNkbCoEvw'

const loadChatData = async (targetChatId) => {
  resetTicketViewState()
  await getCustomerData(targetChatId)
  await Promise.all([
    getMaintenanceRecords(targetChatId),
    getServiceRecords(targetChatId),
    getTickets(targetChatId),
    getIssueTickets(targetChatId),
    getBugTickets(targetChatId)
  ])
  // 群聊加载时即预取版本，避免打开新增维护才开始请求
  prefetchProductVersions()
}

const getCurExternalChat = () => {
  loading.value = true

  if (LOCAL_DEBUG_CHAT) {
    ;(async () => {
      try {
        chatId.value = DEBUG_CHAT_ID
        showToast('本地调试模式：使用写死 chatID', true)
        await loadChatData(chatId.value)
      } catch (err) {
        showToast('异常：' + (err.message || err), false)
        console.error('异常:', err)
      } finally {
        loading.value = false
      }
    })()
    return
  }

  try {
    ww.getCurExternalChat({
      async success(result) {
        try {
          // 成功回调，result.errMsg 固定格式为"方法名:ok"
          chatId.value = result.chatId
          await loadChatData(chatId.value)
        } catch (err) {
          showToast('异常：' + (err.message || err), false)
          console.error('异常:', err)
        } finally {
          loading.value = false
        }
      },
      fail(result) {
        // 失败回调，通过 result.errMsg 查看失败详情
        showToast('获取群聊 chatID 失败！' + result.errMsg, false)
        console.error('调用失败:', result)
        loading.value = false
      }
    })
  } catch (err) {
    showToast('异常：' + (err.message || err), false)
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


const handleLogin = () => {
  userStore.login()
}

const handleLogout = () => {
  userStore.logout()
}

const handleCriticalClick = () => {
  activeTab.value = 'ticket'
  ticketFilter.value = 'critical'
}

const handleUnresolvedClick = () => {
  activeTab.value = 'ticket'
  ticketFilter.value = 'unresolved'
}

const handleAllClick = () => {
  activeTab.value = 'ticket'
  ticketFilter.value = 'resolved'
}

const handleTicketRowClick = () => {
  activeTab.value = 'ticket'
}

const handleTicketLabelClick = () => {
  activeTab.value = 'ticket'
}

const handleRequirementLabelClick = () => {
  activeTab.value = 'requirement'
}

const handleRequirementRowClick = () => {
  activeTab.value = 'requirement'
}

const handleRequirementCriticalClick = () => {
  activeTab.value = 'requirement'
  issueFilter.value = 'critical'
}

const handleRequirementUnresolvedClick = () => {
  activeTab.value = 'requirement'
  issueFilter.value = 'unresolved'
}

const handleRequirementResolvedClick = () => {
  activeTab.value = 'requirement'
  issueFilter.value = 'resolved'
}

const handleDefectLabelClick = () => {
  activeTab.value = 'defect'
}

const handleDefectRowClick = () => {
  activeTab.value = 'defect'
}

const handleDefectCriticalClick = () => {
  activeTab.value = 'defect'
  bugFilter.value = 'critical'
}

const handleDefectUnresolvedClick = () => {
  activeTab.value = 'defect'
  bugFilter.value = 'unresolved'
}

const handleDefectResolvedClick = () => {
  activeTab.value = 'defect'
  bugFilter.value = 'resolved'
}

const handleVersionClick = () => {
  activeTab.value = 'implementation'
}

const formatDateInputValue = (date = new Date()) => {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

const getCurrentProductId = () => {
  return customerData.value?.productId || null
}

const getEditorUserId = () => {
  return userStore.userInfo?.userid || userStore.userInfo?.UserId || userStore.userInfo?.user_id || ''
}

const loadProductVersions = async ({ silent = false, force = false } = {}) => {
  const productId = getCurrentProductId()
  if (!productId) {
    productVersions.value = []
    versionsLoadedProductId.value = null
    if (!silent) {
      showToast('未识别到当前客户产品，无法加载版本列表', false)
    }
    return
  }
  if (!force && versionsLoadedProductId.value === productId && productVersions.value.length > 0) {
    return
  }

  versionsLoading.value = true
  try {
    const result = await Promise.race([
      docApi.getProductVersions(productId, chatId.value),
      new Promise((_, reject) => setTimeout(() => reject(new Error('获取版本列表超时')), 12000))
    ])
    if (result.success) {
      const rawVersions = Array.isArray(result.data) ? result.data : (result.data?.items || [])
      const resolvedProductId = result.data?.productId
      if (!customerData.value?.productId && resolvedProductId) {
        customerData.value = {
          ...(customerData.value || {}),
          productId: resolvedProductId
        }
      }
      productVersions.value = rawVersions.map(item => {
        if (typeof item === 'string') return item
        return item?.version || item?.name || ''
      }).filter(Boolean)
      versionsLoadedProductId.value = resolvedProductId || productId
      return
    }
    productVersions.value = []
    versionsLoadedProductId.value = null
    if (!silent) {
      showToast(result.message || '获取版本列表失败', false)
    }
  } catch (error) {
    productVersions.value = []
    versionsLoadedProductId.value = null
    if (!silent) {
      showToast('获取版本列表失败: ' + (error.message || error), false)
    }
  } finally {
    versionsLoading.value = false
  }
}

const prefetchProductVersions = () => {
  if (versionPreloadPromise.value) {
    return versionPreloadPromise.value
  }
  versionPreloadPromise.value = (async () => {
    await loadProductVersions({ silent: true, force: false })
    if (productVersions.value.length === 0 && getCurrentProductId()) {
      await new Promise(resolve => setTimeout(resolve, 1200))
      await loadProductVersions({ silent: true, force: true })
    }
  })().finally(() => {
    versionPreloadPromise.value = null
  })
  return versionPreloadPromise.value
}

const resetAddMaintenanceForm = () => {
  addMaintenanceForm.value = {
    maintenanceTime: formatDateInputValue(),
    maintenanceTypes: '',
    maintenanceTitle: '',
    maintenanceVersion: '',
    maintenanceContext: ''
  }
}

const closeAddMaintenanceModal = () => {
  showAddMaintenanceModal.value = false
  addMaintenanceSubmitting.value = false
}

const handleAddMaintenance = async () => {
  resetAddMaintenanceForm()
  showAddMaintenanceModal.value = true
  if (productVersions.value.length === 0) {
    prefetchProductVersions()
  }
}

const submitAddMaintenance = async () => {
  if (!customerData.value?.clientId) {
    showToast('缺少客户ID，无法提交维护记录', false)
    return
  }
  if (!getCurrentProductId()) {
    showToast('缺少产品ID，无法提交维护记录', false)
    return
  }
  if (!addMaintenanceForm.value.maintenanceTime || !addMaintenanceForm.value.maintenanceTypes ||
      !addMaintenanceForm.value.maintenanceTitle || !addMaintenanceForm.value.maintenanceVersion ||
      !addMaintenanceForm.value.maintenanceContext) {
    showToast('请完整填写必填项', false)
    return
  }

  addMaintenanceSubmitting.value = true
  try {
    const payload = {
      clientId: customerData.value.clientId,
      ownerId: getEditorUserId(),
      editorUserId: getEditorUserId(),
      maintenanceTypes: addMaintenanceForm.value.maintenanceTypes,
      maintenanceTitle: addMaintenanceForm.value.maintenanceTitle.trim(),
      maintenanceTime: new Date(`${addMaintenanceForm.value.maintenanceTime}T00:00:00`).getTime(),
      regionId: customerData.value.regionId,
      maintenanceVersion: addMaintenanceForm.value.maintenanceVersion,
      maintenanceContext: addMaintenanceForm.value.maintenanceContext.trim(),
      productId: getCurrentProductId(),
      extChatId: chatId.value
    }

    const result = await docApi.createMaintenanceRecord(payload)
    if (result.success || result.code === 0) {
      const nowDate = formatDateInputValue()
      const optimisticRecord = {
        id: result.data?.id || `tmp-${Date.now()}`,
        maintenanceTitle: payload.maintenanceTitle,
        maintenanceTypes: payload.maintenanceTypes,
        maintenanceVersion: payload.maintenanceVersion,
        maintenanceTime: addMaintenanceForm.value.maintenanceTime || nowDate,
        creatorName: userStore.userInfo?.name || userStore.userInfo?.UserId || '-',
        createTime: nowDate,
        maintenanceContext: payload.maintenanceContext
      }
      serviceRecords.value = [optimisticRecord, ...(serviceRecords.value || [])]
      showToast('新增维护记录成功', true)
      closeAddMaintenanceModal()
      // 再从后端拉取最新数据，确保列表与服务端一致
      if (chatId.value) {
        await getServiceRecords(chatId.value)
      }
      return
    }
    showToast(result.message || result.msg || '新增维护记录失败', false)
  } catch (error) {
    showToast('新增维护记录失败: ' + (error.message || error), false)
  } finally {
    addMaintenanceSubmitting.value = false
  }
}

const handleUpdateTicket = async (ticketId) => {
  currentTicketId.value = ticketId
  let ticket = null

  // 根据当前活跃的标签页查找对应的工单
  if (activeTab.value === 'ticket') {
    ticket = tickets.value.find(t => t.id === ticketId)
  } else if (activeTab.value === 'requirement') {
    ticket = issueTickets.value.find(t => t.id === ticketId)
  } else if (activeTab.value === 'defect') {
    ticket = bugTickets.value.find(t => t.id === ticketId)
  }

  currentTicketTitle.value = ticket ? ticket.title : '更新工单'

  // 初始化表单，使用当前工单的数据
  updateForm.value = {
    urgent: ticket?.urgent || false,
    customerSentiment: ticket?.customerSentiment || 'neutral',
    ownerName: '',
    comment: '',
    trackingLinks: ticket?.trackingLinks || ''
  }

  // 加载员工列表
  await loadStaffList()
  showUpdateModal.value = true
}

const loadStaffList = async () => {
  try {
    const result = await docApi.getStaffList()
    // console.log('员工列表响应:', result)
    if (result.success) {
      staffList.value = result.data || []
      // console.log('员工列表加载成功，共', staffList.value.length, '人')
    } else {
      console.error('加载员工列表失败:', result.message)
      showToast('加载员工列表失败: ' + result.message, false)
    }
  } catch (error) {
    console.error('加载员工列表失败:', error)
    showToast('加载员工列表失败', false)
  }
}

// 过滤员工列表
const filteredStaffList = computed(() => {
  if (!updateForm.value.ownerName) {
    return staffList.value
  }
  return staffList.value.filter(staff =>
    staff.toLowerCase().includes(updateForm.value.ownerName.toLowerCase())
  )
})

// 处理输入事件
const handleOwnerInput = () => {
  showStaffDropdown.value = true
}

// 处理失焦事件
const handleOwnerBlur = () => {
  setTimeout(() => {
    showStaffDropdown.value = false
  }, 200)
}

// 选择员工
const selectStaff = (staff) => {
  updateForm.value.ownerName = staff
  showStaffDropdown.value = false
}

// 切换下拉框显示
const toggleStaffDropdown = () => {
  showStaffDropdown.value = !showStaffDropdown.value
}

const closeUpdateModal = () => {
  showUpdateModal.value = false
  currentTicketId.value = null
}

const submitUpdateTicket = async (action) => {
  try {
    if (!updateForm.value.ownerName) {
      showToast('请选择处理人', false)
      return
    }

    if (!updateForm.value.comment) {
      showToast('请填写处理备注', false)
      return
    }

    // 根据操作类型设置不同的参数
    const requestData = {
      ...updateForm.value
    }

    if (action === 'resolve') {
      // 确认解决
      requestData.resolved = true
      requestData.status = 4
    } else if (action === 'cross-team') {
      // 跨团队跟进
      requestData.resolved = false
      requestData.status = 3
    } else if (action === 'follow') {
      // 跟进
      requestData.resolved = false
      requestData.status = 2
    }

    const result = await docApi.updateTicket(currentTicketId.value, requestData)

    if (result.success) {
      showToast('工单更新成功', true)
      closeUpdateModal()
      // 重新加载工单/需求/缺陷列表
      await getTickets(chatId.value)
      await Promise.all([
        getIssueTickets(chatId.value),
        getBugTickets(chatId.value)
      ])
      // 重新加载该工单的流转记录
      await getTicketLogs(currentTicketId.value)
    } else {
      showToast(result.message || '工单更新失败', false)
    }
  } catch (error) {
    console.error('更新工单失败:', error)
    showToast('工单更新失败: ' + error.message, false)
  }
}

onMounted(() => {
  userStore.checkLogin()
  registerWxWork()

})
</script>
