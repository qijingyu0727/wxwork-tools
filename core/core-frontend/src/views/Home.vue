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
          <h2 class="customer-name">{{ customerDisplayName }}</h2>
          <div class="customer-badges">
            <span
              v-if="customerData?.isAccepted"
              :class="['badge', getAcceptanceBadgeClass(customerData?.acceptanceStatusCode), 'badge-clickable']"
              @click="handleAcceptanceStatusClick"
            >
              {{ customerData.isAccepted }}
            </span>
            <span
              v-if="customerServiceStatusText"
              :class="['badge', getCustomerServiceStatusBadgeClass(customerServiceStatusText), 'badge-clickable']"
              @click="handleCustomerServiceStatusClick"
            >
              {{ customerServiceStatusText }}
            </span>
            <span v-if="versionBadgeText" class="badge badge-primary badge-clickable" @click="handleVersionClick">{{ versionBadgeText }}</span>
          </div>
        </div>
        <div
          v-if="showCustomerDataCompletionGuide"
          class="customer-data-guide"
        >
          未识别到客户名称。请前往 CSM -> 客户管理 -> 许可列表，搜索客户全称，编辑补充客户群聊名称后点击“同步”按钮。
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
                <span class="stat-primary">{{ customerSubscriptionEndDateText || '-' }}</span>
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
          <div v-if="activeTab === 'analysis'" class="tab-pane active">
            <div :class="['realtime-analysis-live', { 'is-streaming': realtimeAnalysisStatus === 'streaming' }]">
              <div class="realtime-analysis-live-head">
                <div class="realtime-analysis-live-main">
                  <div class="realtime-analysis-live-title-row">
                    <h3 class="realtime-analysis-live-title">实时聊天分析</h3>
                  </div>
                  <div class="realtime-analysis-range-switch" aria-label="实时分析时间范围">
                    <button
                      v-for="option in realtimeAnalysisRangeOptions"
                      :key="option.value"
                      type="button"
                      :class="['realtime-analysis-range-option', { active: realtimeAnalysisRange === option.value }]"
                      :disabled="realtimeAnalysisStatus === 'streaming'"
                      @click="selectRealtimeAnalysisRange(option.value)"
                    >
                      {{ option.label }}
                    </button>
                  </div>
                </div>
                <button
                  type="button"
                  :class="['realtime-analysis-icon-action', { 'is-streaming': realtimeAnalysisStatus === 'streaming' }]"
                  :disabled="realtimeAnalysisStatus === 'streaming' || !chatId"
                  @click="startRealtimeAnalysisStream"
                  title="开始分析"
                  aria-label="开始分析"
                >
                  <i :class="['fa', realtimeAnalysisStatus === 'streaming' ? 'fa-spinner fa-spin' : 'fa-magic']"></i>
                  <span>{{ realtimeAnalysisStatus === 'streaming' ? '分析中' : '开始分析' }}</span>
                </button>
              </div>

              <div v-if="realtimeAnalysisError" class="realtime-analysis-alert">
                {{ realtimeAnalysisError }}
              </div>

              <section class="realtime-analysis-overview-card">
                <div class="realtime-analysis-section-title-row">
                  <div>
                    <div class="realtime-analysis-section-title">问题概览</div>
                  </div>
                </div>
                <p :class="['realtime-analysis-problem-text', { 'is-typing': realtimeAnalysisStatus === 'streaming' }]">
                  {{ realtimeAnalysisProblemSummary }}
                </p>
                <div class="realtime-analysis-tag-row">
                  <span
                    v-for="tag in realtimeAnalysisOverviewTags"
                    :key="tag.label"
                    :class="['realtime-analysis-tag', tag.type ? `is-${tag.type}` : '']"
                  >
                    {{ tag.label }}: {{ tag.value }}
                  </span>
                </div>
              </section>

              <section class="realtime-analysis-section-card">
                <div class="realtime-analysis-section-heading">
                  <i class="fa fa-comments-o"></i>
                  <span>回复建议</span>
                </div>
                <div v-if="realtimeAnalysisReplyOptions.length" class="realtime-analysis-reply-list">
                  <article
                    v-for="(option, index) in realtimeAnalysisReplyOptions"
                    :key="`${option.approach || 'reply'}-${index}`"
                    class="realtime-analysis-reply-card"
                  >
                    <div class="realtime-analysis-reply-head">
                      <strong>{{ option.approach || `推荐回复 ${index + 1}` }}</strong>
                      <div class="realtime-analysis-reply-actions">
                        <span
                          v-for="tag in option.toneTags"
                          :key="tag"
                          class="realtime-analysis-tone-chip"
                        >
                          {{ tag }}
                        </span>
                        <button
                          type="button"
                          class="realtime-analysis-inline-copy"
                          @click="copyRealtimeAnalysisText(option.content, '推荐回复已复制')"
                        >
                          <i class="fa fa-copy"></i>
                          <span>复制</span>
                        </button>
                      </div>
                    </div>
                    <p :class="['realtime-analysis-reply-content', { 'is-typing': realtimeAnalysisStatus === 'streaming' }]">{{ option.content || '-' }}</p>
                  </article>
                </div>
                <div v-else class="realtime-analysis-empty">
                  {{ realtimeAnalysisReplyEmptyText }}
                </div>
              </section>

              <div v-if="realtimeAnalysisReferences.length || realtimeAnalysisOtherFields.length" class="realtime-analysis-grid">
                <section v-if="realtimeAnalysisReferences.length" class="realtime-analysis-section-card">
                  <div class="realtime-analysis-section-heading">
                    <i class="fa fa-book"></i>
                    <span>知识引用</span>
                  </div>
                  <div class="realtime-analysis-reference-list">
                    <a
                      v-for="(reference, index) in realtimeAnalysisReferences"
                      :key="`reference-${index}`"
                      class="realtime-analysis-reference"
                      :href="reference.url"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <strong>{{ reference.title || reference.url }}</strong>
                      <span v-if="reference.relevance">{{ reference.relevance }}</span>
                    </a>
                  </div>
                </section>

                <section v-if="realtimeAnalysisOtherFields.length" class="realtime-analysis-section-card">
                  <div class="realtime-analysis-section-heading">
                    <i class="fa fa-info-circle"></i>
                    <span>其他内容</span>
                  </div>
                  <div class="realtime-analysis-other-list">
                    <div v-for="field in realtimeAnalysisOtherFields" :key="field.key" class="realtime-analysis-other-item">
                      <strong>{{ field.key }}</strong>
                      <pre>{{ field.value }}</pre>
                    </div>
                  </div>
                </section>
              </div>
            </div>
          </div>

          <!-- 实施 Tab -->
          <div v-if="activeTab === 'implementation'" class="tab-pane active">
            <div v-if="!maintenanceLoading && maintenanceRecords.length === 0" class="add-maintenance-section">
              <button class="btn-add-maintenance" @click="handleAddImplementation">
                <span>+ 新增</span>
              </button>
            </div>

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

          <!-- 合同 Tab -->
          <div v-if="activeTab === 'contract'" class="tab-pane active">
            <div v-if="contractsLoading" class="tab-placeholder">
              <i class="fa fa-spinner fa-spin text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">加载中...</p>
            </div>
            <div v-else-if="contractSubscriptions.length === 0" class="tab-placeholder">
              <i class="fa fa-file-text-o text-3xl text-gray-400 mb-4"></i>
              <p class="text-gray-500">暂无合同信息</p>
            </div>
            <div v-else class="contract-list">
              <div v-for="contract in contractSubscriptions" :key="contract.id || contract.contractNumber" class="maintenance-card contract-card">
                <div class="contract-card-top">
                  <div class="contract-title-block">
                    <span class="maintenance-template contract-title">{{ contract.productServiceName || contract.contractNumber || '-' }}</span>
                  </div>
                  <span class="contract-service-tag">{{ formatContractServiceTag(contract) }}</span>
                </div>
                <div class="contract-detail-list">
                  <div class="contract-detail-row">
                    <span class="contract-detail-label">授权数量</span>
                    <span class="contract-detail-value contract-license-value">{{ formatContractLicenseAmount(contract) }}</span>
                  </div>
                  <div class="contract-detail-row">
                    <span class="contract-detail-label">合同编号</span>
                    <span class="contract-detail-value">{{ contract.contractNumber || '-' }}</span>
                  </div>
                  <div class="contract-detail-row">
                    <span class="contract-detail-label">订阅类型</span>
                    <span class="contract-detail-value">{{ contract.subscriptionTypeName || '-' }}</span>
                  </div>
                  <div class="contract-detail-row">
                    <span class="contract-detail-label">实施顾问</span>
                    <span class="contract-detail-value">{{ contract.supportUser || '-' }}</span>
                  </div>
                  <div class="contract-detail-row">
                    <span class="contract-detail-label">销售</span>
                    <span class="contract-detail-value">{{ contract.salesUser || '-' }}</span>
                  </div>
                  <div class="contract-detail-row">
                    <span class="contract-detail-label">许可周期</span>
                    <span class="contract-detail-value contract-period">{{ formatContractPeriod(contract) }}</span>
                  </div>
                  <div class="contract-detail-row">
                    <span class="contract-detail-label">序列号</span>
                    <span class="contract-detail-value">{{ contract.serialNo || '-' }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 工具 Tab -->
          <div v-if="activeTab === 'tools'" class="tab-pane active">
            <div class="tools-pane">
              <div class="tools-grid">
                <section class="tool-card tool-card-download">
                  <div class="tool-card-title-row">
                    <span class="tool-card-icon">
                      <i class="fa fa-download"></i>
                    </span>
                    <div class="tool-card-heading">
                      <h4 class="tool-card-title">获取下载链接</h4>
                    </div>
                  </div>

                  <div class="tool-card-body">
                    <div class="tool-form-row">
                      <select
                        v-model="downloadVersion"
                        class="tool-download-select"
                        :disabled="versionsLoading || productVersions.length === 0"
                        @focus="prefetchProductVersions"
                        @change="handleDownloadVersionChange"
                      >
                        <option value="">{{ versionsLoading ? '版本加载中...' : '请选择版本' }}</option>
                        <option v-for="version in productVersions" :key="version" :value="version">{{ version }}</option>
                      </select>
                    </div>

                    <div v-if="downloadUrl" class="tool-download-result">
                      <a :href="downloadUrl" target="_blank" rel="noopener noreferrer" class="tool-download-link">
                        {{ downloadUrl }}
                      </a>
                      <button
                        type="button"
                        class="tool-download-copy"
                        title="复制下载链接"
                        aria-label="复制下载链接"
                        @click="copyDownloadUrl"
                      >
                        <i class="fa fa-copy"></i>
                      </button>
                    </div>
                  </div>

                  <div class="tool-card-footer">
                    <button
                      class="tool-action-btn tool-action-btn-download"
                      :disabled="downloadUrlLoading || !downloadVersion"
                      @click="handleGenerateDownloadUrl"
                    >
                      {{ downloadUrlLoading ? '生成中...' : '获取链接' }}
                    </button>
                  </div>
                </section>

                <section class="tool-card tool-card-report">
                  <div class="tool-card-title-row">
                    <span class="tool-card-icon">
                      <i class="fa fa-file-word-o"></i>
                    </span>
                    <div class="tool-card-heading">
                      <h4 class="tool-card-title">获取验收报告</h4>
                    </div>
                  </div>

                  <div class="tool-card-body tool-card-body-report"></div>

                  <div class="tool-card-footer">
                    <button
                      class="tool-action-btn tool-action-btn-report"
                      :disabled="toolReportSubmitting"
                      @click="handleGetAcceptanceReport"
                    >
                      {{ toolReportSubmitting ? '请求中...' : '获取验收报告' }}
                    </button>
                  </div>
                </section>

                <section class="tool-card tool-card-email">
                  <div class="tool-card-title-row">
                    <span class="tool-card-icon">
                      <i class="fa fa-envelope"></i>
                    </span>
                    <div class="tool-card-heading">
                      <h4 class="tool-card-title">邮件发送</h4>
                    </div>
                  </div>

                  <div class="tool-card-body">
                    <div class="tool-form-row">
                      <textarea
                        ref="toolEmailTextareaRef"
                        v-model.trim="toolEmail"
                        class="tool-email-input tool-email-textarea tool-email-textarea-single"
                        rows="1"
                        placeholder="填写客户邮箱，多个邮箱用分号或逗号隔开"
                        @input="adjustToolEmailTextarea"
                      ></textarea>
                    </div>

                    <div class="tool-form-row tool-cc-row">
                      <textarea
                        ref="toolCcTextareaRef"
                        v-model.trim="toolCcEmails"
                        class="tool-email-input tool-cc-textarea"
                        rows="3"
                        placeholder="填写抄送邮箱，多个邮箱用分号隔开"
                        @input="adjustToolCcTextarea"
                      ></textarea>
                    </div>
                  </div>

                  <div class="tool-card-footer">
                    <button
                      class="tool-action-btn tool-action-btn-email"
                      :disabled="toolMailSubmitting"
                      @click="handleSendToolMail"
                    >
                      {{ toolMailSubmitting ? '发送中...' : '发送邮件' }}
                    </button>
                  </div>
                </section>
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
                      <template v-if="getTrackingLinkList(ticket).length > 0">
                        <div v-for="(link, index) in getTrackingLinkList(ticket)" :key="`${ticket.id}-${index}`">
                          <a :href="link" target="_blank" rel="noopener noreferrer" class="link">{{ link }}</a>
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
                      <template v-if="getTrackingLinkList(ticket).length > 0">
                        <div v-for="(link, index) in getTrackingLinkList(ticket)" :key="`${ticket.id}-${index}`">
                          <a :href="link" target="_blank" rel="noopener noreferrer" class="link">{{ link }}</a>
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
    <div v-if="showAddImplementationModal" class="modal-overlay" @click="closeAddImplementationModal">
      <div class="modal-content implementation-modal-content" @click.stop>
        <div class="modal-header">
          <h3>新增实施记录</h3>
          <button class="modal-close" @click="closeAddImplementationModal">
            <i class="fa fa-times"></i>
          </button>
        </div>
        <div class="modal-body implementation-modal-body">
          <div v-if="implementationContextLoading" class="implementation-loading-state">
            <i class="fa fa-spinner fa-spin"></i>
            <span>加载实施上下文中...</span>
          </div>
          <template v-else>
            <section v-if="shouldShowImplementationBasicInfo" class="implementation-section implementation-basic-section">
              <div class="implementation-section-header">
                <h4>基本信息</h4>
              </div>
              <div class="form-group">
                <label class="form-label">订阅（客户-产品-序列号）</label>
                <input
                  type="text"
                  class="form-input implementation-readonly-input"
                  :value="implementationContext.subscriptionDisplayText || '-'"
                  readonly
                />
              </div>
              <div class="implementation-basic-table">
                <div
                  v-for="item in implementationBasicInfoRows"
                  :key="item.label"
                  class="implementation-basic-row"
                >
                  <span class="implementation-basic-label">{{ item.label }}</span>
                  <span class="implementation-basic-value">{{ item.value || '-' }}</span>
                </div>
              </div>
            </section>
            <section class="implementation-section">
              <div class="implementation-section-header">
                <h4>部署信息</h4>
              </div>
              <div class="implementation-form-grid">
                <div v-if="isImplementationDraftMode" class="form-group implementation-form-span-2">
                  <label class="form-label">产品 <span class="required">*</span></label>
                  <select
                    v-model="addImplementationForm.selectedProductId"
                    class="form-input"
                    @change="handleImplementationProductChange"
                  >
                    <option value="" disabled>请选择产品</option>
                    <option
                      v-for="option in implementationProductOptions"
                      :key="option.productId || option.template || option.productName"
                      :value="String(option.productId || '')"
                      :disabled="!option.productId"
                    >
                      {{ option.productName }}
                    </option>
                  </select>
                </div>
                <div class="form-group">
                  <label class="form-label">部署日期 <span class="required">*</span></label>
                  <div class="maintenance-date-picker" @click.stop>
                    <button
                      type="button"
                      class="form-input maintenance-date-input maintenance-date-trigger"
                      @click="toggleImplementationCalendar"
                    >
                      <span :class="['maintenance-date-trigger-text', { 'is-placeholder': !addImplementationForm.deploymentDate }]">
                        {{ addImplementationForm.deploymentDate || '请选择部署日期' }}
                      </span>
                      <i class="fa fa-calendar maintenance-date-trigger-icon"></i>
                    </button>
                    <div v-if="showImplementationCalendar" class="maintenance-calendar-popover">
                      <div class="maintenance-calendar-toolbar">
                        <button type="button" class="maintenance-calendar-nav" @click="changeImplementationCalendarMonth(-1)">
                          <i class="fa fa-chevron-left"></i>
                        </button>
                        <div class="maintenance-calendar-title">{{ implementationCalendarTitle }}</div>
                        <button type="button" class="maintenance-calendar-nav" @click="changeImplementationCalendarMonth(1)">
                          <i class="fa fa-chevron-right"></i>
                        </button>
                      </div>
                      <div class="maintenance-calendar-weekdays">
                        <span v-for="day in maintenanceCalendarWeekdays" :key="day">{{ day }}</span>
                      </div>
                      <div class="maintenance-calendar-grid">
                        <button
                          v-for="day in implementationCalendarDays"
                          :key="day.key"
                          type="button"
                          :class="[
                            'maintenance-calendar-day',
                            { 'is-outside': !day.inCurrentMonth, 'is-today': day.isToday, 'is-selected': day.isSelected }
                          ]"
                          @click="selectImplementationCalendarDate(day.value)"
                        >
                          {{ day.label }}
                        </button>
                      </div>
                      <div class="maintenance-calendar-footer">
                        <button type="button" class="maintenance-calendar-link" @click="selectImplementationCalendarToday">今天</button>
                        <button type="button" class="maintenance-calendar-link" @click="closeImplementationCalendar">关闭</button>
                      </div>
                    </div>
                  </div>
                </div>
                <div class="form-group">
                  <label class="form-label">部署方式 <span class="required">*</span></label>
                  <select v-model="addImplementationForm.deploymentMethod" class="form-input">
                    <option value="" disabled>请选择部署方式</option>
                    <option v-for="option in IMPLEMENTATION_DEPLOYMENT_METHOD_OPTIONS" :key="option" :value="option">{{ option }}</option>
                  </select>
                </div>
                <div class="form-group implementation-form-span-2">
                  <label class="form-label">实施人 <span class="required">*</span></label>
                  <div class="autocomplete-wrapper">
                    <input
                      type="text"
                      v-model="addImplementationForm.submitterName"
                      @input="handleImplementationSubmitterInput"
                      @focus="showImplementationSubmitterDropdown = true"
                      @blur="handleImplementationSubmitterBlur"
                      class="form-input autocomplete-input"
                      placeholder="请输入实施人姓名"
                      autocomplete="off"
                    />
                    <i
                      class="fa fa-chevron-down autocomplete-icon"
                      @mousedown.prevent="toggleImplementationSubmitterDropdown"
                    ></i>
                    <div v-if="showImplementationSubmitterDropdown && filteredImplementationSubmitterList.length > 0" class="autocomplete-dropdown">
                      <div
                        v-for="staff in filteredImplementationSubmitterList"
                        :key="staff"
                        class="autocomplete-item"
                        @mousedown.prevent="selectImplementationSubmitter(staff)"
                      >
                        {{ staff }}
                      </div>
                    </div>
                    <div v-if="showImplementationSubmitterDropdown && filteredImplementationSubmitterList.length === 0 && implementationStaffList.length === 0 && !implementationStaffListLoaded" class="autocomplete-dropdown">
                      <div class="autocomplete-item autocomplete-empty">
                        加载中...
                      </div>
                    </div>
                    <div v-if="showImplementationSubmitterDropdown && filteredImplementationSubmitterList.length === 0 && (implementationStaffList.length > 0 || implementationStaffListLoaded)" class="autocomplete-dropdown">
                      <div class="autocomplete-item autocomplete-empty">
                        未找到匹配的员工
                      </div>
                    </div>
                  </div>
                </div>
                <div class="form-group implementation-form-span-2">
                  <label class="form-label">软件版本 <span class="required">*</span></label>
                  <select v-model="addImplementationForm.version" class="form-input" :disabled="versionsLoading || productVersions.length === 0">
                    <option value="" disabled>{{ versionsLoading ? '版本加载中...' : '请选择版本' }}</option>
                    <option v-for="version in productVersions" :key="version" :value="version">{{ version }}</option>
                  </select>
                  <div v-if="versionsLoading" class="text-gray-400 text-sm mt-2">版本加载中...</div>
                </div>
                <template v-if="isJumpServerImplementation">
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">纳管资产类型 <span class="required">*</span></label>
                    <div class="implementation-chip-group">
                      <button
                        v-for="option in IMPLEMENTATION_ASSET_TYPE_OPTIONS"
                        :key="option"
                        type="button"
                        :class="['implementation-chip', addImplementationForm.assetTypes.includes(option) ? 'active' : '']"
                        @click="toggleImplementationAssetType(option)"
                      >
                        {{ option }}
                      </button>
                    </div>
                  </div>
                  <div class="form-group">
                    <label class="form-label">管理资产数 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.assetCount" class="form-input">
                      <option value="" disabled>请选择管理资产数</option>
                      <option v-for="option in IMPLEMENTATION_ASSET_COUNT_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">虚拟化类型 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.virtualizationType" class="form-input">
                      <option value="" disabled>请选择虚拟化类型</option>
                      <option v-for="option in IMPLEMENTATION_VIRTUALIZATION_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">应用发布服务器 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.applicationServer" class="form-input">
                      <option value="" disabled>请选择</option>
                      <option v-for="option in IMPLEMENTATION_APPLICATION_SERVER_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">是否涉及到数据同步 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.databaseSync" class="form-input">
                      <option value="" disabled>请选择</option>
                      <option v-for="option in IMPLEMENTATION_DATABASE_SYNC_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">数据库是否外置 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.databaseExternal" class="form-input">
                      <option value="" disabled>请选择</option>
                      <option v-for="option in IMPLEMENTATION_DATABASE_EXTERNAL_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">Redis是否外置部署 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.redisExternal" class="form-input">
                      <option value="" disabled>请选择</option>
                      <option v-for="option in IMPLEMENTATION_REDIS_EXTERNAL_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">共享存储NFS <span class="required">*</span></label>
                    <select v-model="addImplementationForm.sharedNfs" class="form-input">
                      <option value="" disabled>请选择</option>
                      <option v-for="option in IMPLEMENTATION_SHARED_NFS_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">客户核心关注点</label>
                    <textarea
                      ref="implementationCustomerFocusTextareaRef"
                      v-model.trim="addImplementationForm.customerFocus"
                      class="form-textarea implementation-customer-focus-textarea"
                      placeholder="请输入客户核心关注点"
                      rows="1"
                      @input="adjustImplementationCustomerFocusTextarea"
                    ></textarea>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">部署架构 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.deploymentArchitecture" class="form-input">
                      <option value="" disabled>请选择部署架构</option>
                      <option v-for="option in IMPLEMENTATION_ARCHITECTURE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                </template>
                <template v-else-if="isMaxKbImplementation">
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">部署架构 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.deploymentArchitecture" class="form-input">
                      <option value="" disabled>请选择部署架构</option>
                      <option v-for="option in IMPLEMENTATION_ARCHITECTURE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">认证方式 <span class="required">*</span></label>
                    <div class="implementation-chip-group">
                      <button
                        v-for="option in IMPLEMENTATION_MAXKB_AUTH_OPTIONS"
                        :key="option"
                        type="button"
                        :class="['implementation-chip', addImplementationForm.authMethods.includes(option) ? 'active' : '']"
                        @click="toggleImplementationAuthMethod(option)"
                      >
                        {{ option }}
                      </button>
                    </div>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">业务方向 <span class="required">*</span></label>
                    <div class="implementation-chip-group">
                      <button
                        v-for="option in IMPLEMENTATION_MAXKB_DIRECTION_OPTIONS"
                        :key="option"
                        type="button"
                        :class="['implementation-chip', addImplementationForm.businessDirections.includes(option) ? 'active' : '']"
                        @click="toggleImplementationBusinessDirection(option)"
                      >
                        {{ option }}
                      </button>
                    </div>
                  </div>
                </template>
                <template v-else-if="isDataEaseImplementation">
                  <div class="form-group">
                    <label class="form-label">备份方式 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.backupMethod" class="form-input">
                      <option value="" disabled>请选择备份方式</option>
                      <option v-for="option in IMPLEMENTATION_DATAEASE_BACKUP_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">数据库配置 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.dataEaseDatabase" class="form-input">
                      <option value="" disabled>请选择数据库配置</option>
                      <option v-for="option in IMPLEMENTATION_DATAEASE_DATABASE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">Doris配置 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.dorisUsage" class="form-input">
                      <option value="" disabled>请选择 Doris 配置</option>
                      <option v-for="option in IMPLEMENTATION_DATAEASE_DORIS_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">部署架构 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.deploymentArchitecture" class="form-input">
                      <option value="" disabled>请选择部署架构</option>
                      <option v-for="option in IMPLEMENTATION_ARCHITECTURE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">数据源类型 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.dataSourceType" class="form-input">
                      <option value="" disabled>请选择数据源类型</option>
                      <option v-for="option in IMPLEMENTATION_DATAEASE_DATA_SOURCE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">数据量规模 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.dataScale" class="form-input">
                      <option value="" disabled>请选择数据量规模</option>
                      <option v-for="option in IMPLEMENTATION_DATAEASE_DATA_SCALE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">认证方式 <span class="required">*</span></label>
                    <div class="implementation-chip-group">
                      <button
                        v-for="option in IMPLEMENTATION_DATAEASE_AUTH_OPTIONS"
                        :key="option"
                        type="button"
                        :class="['implementation-chip', addImplementationForm.authMethods.includes(option) ? 'active' : '']"
                        @click="toggleImplementationAuthMethod(option)"
                      >
                        {{ option }}
                      </button>
                    </div>
                  </div>
                  <div class="form-group">
                    <label class="form-label">嵌入方式 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.embeddedMode" class="form-input">
                      <option value="" disabled>请选择嵌入方式</option>
                      <option v-for="option in IMPLEMENTATION_DATAEASE_EMBEDDED_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">客户接入状态 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.customerJoined" class="form-input">
                      <option value="" disabled>请选择客户接入状态</option>
                      <option v-for="option in IMPLEMENTATION_DATAEASE_CUSTOMER_JOINED_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">分析及展示方向 <span class="required">*</span></label>
                    <textarea
                      v-model.trim="addImplementationForm.analysisDirection"
                      class="form-textarea implementation-compact-textarea"
                      placeholder="请输入分析及展示方向"
                      rows="1"
                    ></textarea>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">客户核心关注点 <span class="required">*</span></label>
                    <textarea
                      ref="implementationCustomerFocusTextareaRef"
                      v-model.trim="addImplementationForm.customerFocus"
                      class="form-textarea implementation-customer-focus-textarea"
                      placeholder="请输入客户核心关注点"
                      rows="1"
                      @input="adjustImplementationCustomerFocusTextarea"
                    ></textarea>
                  </div>
                </template>
                <template v-else-if="isSqlBotImplementation">
                  <div class="form-group">
                    <label class="form-label">备份方式 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.backupMethod" class="form-input">
                      <option value="" disabled>请选择备份方式</option>
                      <option v-for="option in IMPLEMENTATION_SQLBOT_BACKUP_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">PostgreSQL 是否外置 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.databaseExternal" class="form-input">
                      <option value="" disabled>请选择</option>
                      <option v-for="option in IMPLEMENTATION_SQLBOT_POSTGRES_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">部署架构 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.deploymentArchitecture" class="form-input">
                      <option value="" disabled>请选择部署架构</option>
                      <option v-for="option in IMPLEMENTATION_SQLBOT_ARCHITECTURE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">数据源类型 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.dataSourceType" class="form-input">
                      <option value="" disabled>请选择数据源类型</option>
                      <option v-for="option in IMPLEMENTATION_SQLBOT_DATA_SOURCE_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">AI模型类型 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.aiModelType" class="form-input">
                      <option value="" disabled>请选择 AI 模型类型</option>
                      <option v-for="option in IMPLEMENTATION_SQLBOT_AI_MODEL_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group">
                    <label class="form-label">是否嵌入集成 <span class="required">*</span></label>
                    <select v-model="addImplementationForm.embeddedMode" class="form-input">
                      <option value="" disabled>请选择</option>
                      <option v-for="option in IMPLEMENTATION_SQLBOT_EMBEDDED_OPTIONS" :key="option" :value="option">{{ option }}</option>
                    </select>
                  </div>
                  <div class="form-group implementation-form-span-2">
                    <label class="form-label">第三方平台对接 <span class="required">*</span></label>
                    <div class="implementation-chip-group">
                      <button
                        v-for="option in IMPLEMENTATION_SQLBOT_PLATFORM_OPTIONS"
                        :key="option"
                        type="button"
                        :class="['implementation-chip', addImplementationForm.authMethods.includes(option) ? 'active' : '']"
                        @click="toggleImplementationAuthMethod(option)"
                      >
                        {{ option }}
                      </button>
                    </div>
                  </div>
                </template>
              </div>
            </section>

            <section v-if="isJumpServerImplementation" class="implementation-section">
              <div class="implementation-section-header">
                <h4>部署记录</h4>
              </div>
              <div class="form-group">
                <label class="form-label">记录内容 <span class="required">*</span></label>
                <textarea
                  ref="implementationDeploymentRecordTextareaRef"
                  v-model.trim="addImplementationForm.deploymentRecord"
                  class="form-textarea implementation-record-textarea"
                  placeholder="请输入部署记录内容"
                  rows="2"
                  @input="adjustImplementationDeploymentRecordTextarea"
                ></textarea>
              </div>
            </section>

            <section class="implementation-section">
              <div class="implementation-section-header">
                <h4>其他信息</h4>
              </div>
              <div class="form-group">
                <label class="form-label">遗留问题</label>
                <textarea
                  ref="implementationRemainingIssuesTextareaRef"
                  v-model.trim="addImplementationForm.remainingIssues"
                  class="form-textarea implementation-compact-textarea"
                  placeholder="请输入遗留问题"
                  rows="1"
                  @input="adjustImplementationRemainingIssuesTextarea"
                ></textarea>
              </div>
              <div class="form-group">
                <label class="form-label">备注</label>
                <textarea
                  ref="implementationRemarkTextareaRef"
                  v-model.trim="addImplementationForm.remark"
                  class="form-textarea implementation-compact-textarea"
                  placeholder="请输入备注"
                  rows="1"
                  @input="adjustImplementationRemarkTextarea"
                ></textarea>
              </div>
            </section>
          </template>
        </div>
        <div class="modal-footer modal-footer-actions">
          <button class="btn btn-secondary btn-uniform" @click="closeAddImplementationModal">取消</button>
          <button
            class="btn btn-primary btn-resolve btn-uniform"
            :disabled="addImplementationSubmitting || implementationContextLoading"
            @click="submitAddImplementation"
          >
            {{ addImplementationSubmitting ? '提交中...' : '提交' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 新增维护弹窗 -->
    <div v-if="showAddMaintenanceModal" class="modal-overlay" @click="closeAddMaintenanceModal">
      <div class="modal-content maintenance-modal-content" @click.stop>
        <div class="modal-header">
          <h3>新增维护记录</h3>
          <button class="modal-close" @click="closeAddMaintenanceModal">
            <i class="fa fa-times"></i>
          </button>
        </div>
        <div class="modal-body maintenance-modal-body">
          <div class="maintenance-panel maintenance-panel-primary">
            <div class="maintenance-panel-header">
              <h4>基础信息</h4>
            </div>
            <div class="maintenance-form-grid">
              <div class="form-group maintenance-primary-field">
                <label class="form-label">维护日期 <span class="required">*</span></label>
                <div class="maintenance-date-picker" @click.stop>
                  <button
                    type="button"
                    class="form-input maintenance-date-input maintenance-date-trigger"
                    @click="toggleMaintenanceCalendar"
                  >
                    <span :class="['maintenance-date-trigger-text', { 'is-placeholder': !addMaintenanceForm.maintenanceTime }]">
                      {{ addMaintenanceForm.maintenanceTime || '请选择维护日期' }}
                    </span>
                    <i class="fa fa-calendar maintenance-date-trigger-icon"></i>
                  </button>
                  <div v-if="showMaintenanceCalendar" class="maintenance-calendar-popover">
                    <div class="maintenance-calendar-toolbar">
                      <button type="button" class="maintenance-calendar-nav" @click="changeMaintenanceCalendarMonth(-1)">
                        <i class="fa fa-chevron-left"></i>
                      </button>
                      <div class="maintenance-calendar-title">{{ maintenanceCalendarTitle }}</div>
                      <button type="button" class="maintenance-calendar-nav" @click="changeMaintenanceCalendarMonth(1)">
                        <i class="fa fa-chevron-right"></i>
                      </button>
                    </div>
                    <div class="maintenance-calendar-weekdays">
                      <span v-for="day in maintenanceCalendarWeekdays" :key="day">{{ day }}</span>
                    </div>
                    <div class="maintenance-calendar-grid">
                      <button
                        v-for="day in maintenanceCalendarDays"
                        :key="day.key"
                        type="button"
                        :class="[
                          'maintenance-calendar-day',
                          { 'is-outside': !day.inCurrentMonth, 'is-today': day.isToday, 'is-selected': day.isSelected }
                        ]"
                        @click="selectMaintenanceCalendarDate(day.value)"
                      >
                        {{ day.label }}
                      </button>
                    </div>
                    <div class="maintenance-calendar-footer">
                      <button type="button" class="maintenance-calendar-link" @click="selectMaintenanceCalendarToday">今天</button>
                      <button type="button" class="maintenance-calendar-link" @click="closeMaintenanceCalendar">关闭</button>
                    </div>
                  </div>
                </div>
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
                <label class="form-label">提交人 <span class="required">*</span></label>
                <div class="autocomplete-wrapper">
                  <input
                    type="text"
                    v-model="addMaintenanceForm.submitterName"
                    @input="handleMaintenanceSubmitterInput"
                    @focus="showMaintenanceSubmitterDropdown = true"
                    @blur="handleMaintenanceSubmitterBlur"
                    class="form-input autocomplete-input"
                    placeholder="请输入提交人姓名"
                    autocomplete="off"
                  />
                  <i
                    class="fa fa-chevron-down autocomplete-icon"
                    @mousedown.prevent="toggleMaintenanceSubmitterDropdown"
                  ></i>
                  <div v-if="showMaintenanceSubmitterDropdown && filteredMaintenanceSubmitterList.length > 0" class="autocomplete-dropdown">
                    <div
                      v-for="staff in filteredMaintenanceSubmitterList"
                      :key="staff"
                      class="autocomplete-item"
                      @mousedown.prevent="selectMaintenanceSubmitter(staff)"
                    >
                      {{ staff }}
                    </div>
                  </div>
                  <div v-if="showMaintenanceSubmitterDropdown && filteredMaintenanceSubmitterList.length === 0 && implementationStaffList.length === 0 && !implementationStaffListLoaded" class="autocomplete-dropdown">
                    <div class="autocomplete-item autocomplete-empty">
                      加载中...
                    </div>
                  </div>
                  <div v-if="showMaintenanceSubmitterDropdown && filteredMaintenanceSubmitterList.length === 0 && (implementationStaffList.length > 0 || implementationStaffListLoaded)" class="autocomplete-dropdown">
                    <div class="autocomplete-item autocomplete-empty">
                      未找到匹配的员工
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="maintenance-panel">
            <div class="maintenance-panel-header">
              <h4>维护内容</h4>
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
              <select v-model="addMaintenanceForm.maintenanceVersion" class="form-input" :disabled="versionsLoading || productVersions.length === 0" required>
                <option value="" disabled>{{ versionsLoading ? '版本加载中...' : '请选择版本' }}</option>
                <option v-for="version in productVersions" :key="version" :value="version">{{ version }}</option>
              </select>
              <div v-if="versionsLoading" class="text-gray-400 text-sm mt-2">版本加载中...</div>
            </div>

            <div class="form-group">
              <label class="form-label">详细过程记录 <span class="required">*</span></label>
              <textarea v-model="addMaintenanceForm.maintenanceContext" class="form-textarea maintenance-detail-textarea" placeholder="请输入详细过程记录" rows="3" required></textarea>
            </div>
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
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { jsapiApi, docApi } from '@/api/doc'
import CryptoJS from 'crypto-js'
import * as ww from '@wecom/jssdk'
import '@/styles/home.css'

// 本地调试开关：true 时使用配置 chatId，false 时走企业微信 getCurExternalChat
const LOCAL_DEBUG_CHAT = import.meta.env.VITE_LOCAL_DEBUG_CHAT === 'true'
const DEBUG_CHAT_ID = import.meta.env.VITE_DEBUG_CHAT_ID || ''
const DEBUG_EDITOR_USER_ID = import.meta.env.VITE_DEBUG_EDITOR_USER_ID || ''
const DEFAULT_TOOL_MAIL_CC = 'ec_cssc@fit2cloud.com'

const corpId = ref('')
const agentId = ref('')
const chatId = ref('')
const loading = ref(false)
const customerData = ref({})
const customerDataLoading = ref(false)
const acceptanceStatusLoading = ref(false)
const maintenanceRecords = ref([])
const maintenanceLoading = ref(false)
const serviceRecords = ref([])
const serviceLoading = ref(false)
const contractSubscriptions = ref([])
const contractsLoading = ref(false)
const contractsLoadedChatId = ref('')
const contractsPreloadPromise = ref(null)
const contractsPreloadChatId = ref('')
const tickets = ref([])
const ticketsLoading = ref(false)
const ticketFilter = ref('unresolved') // 'all', 'resolved', 'unresolved'
const ticketSearchInput = ref('')
const ticketSearchKeyword = ref('')
const expandedTickets = ref(new Set())
const ticketLogs = ref({})
const activeTab = ref('ticket')
const tabs = ref([
  { id: 'analysis', name: '实时分析' },
  { id: 'ticket', name: '工单' },
  { id: 'requirement', name: '需求' },
  { id: 'defect', name: '缺陷' },
  { id: 'contract', name: '合同' },
  { id: 'implementation', name: '实施' },
  { id: 'maintenance', name: '维护' },
  { id: 'tools', name: '工具' }
])
const toolEmail = ref('')
const toolCcEmails = ref(DEFAULT_TOOL_MAIL_CC)
const toolEmailTextareaRef = ref(null)
const toolCcTextareaRef = ref(null)
const implementationCustomerFocusTextareaRef = ref(null)
const implementationDeploymentRecordTextareaRef = ref(null)
const implementationRemainingIssuesTextareaRef = ref(null)
const implementationRemarkTextareaRef = ref(null)
const toolMailSubmitting = ref(false)
const toolReportSubmitting = ref(false)
let acceptanceStatusRequestToken = 0
let acceptanceStatusRefreshTimers = []

const createTabLoadState = () => ({
  analysis: true,
  implementation: false,
  maintenance: false,
  contract: false,
  tools: false,
  ticket: false,
  requirement: false,
  defect: false
})

const tabLoadState = ref(createTabLoadState())

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
const staffListPreloadPromise = ref(null)
const implementationStaffList = ref([])
const implementationStaffListLoaded = ref(false)
const implementationStaffListPreloadPromise = ref(null)
const showStaffDropdown = ref(false)
const showMaintenanceSubmitterDropdown = ref(false)
const showImplementationSubmitterDropdown = ref(false)
const updateForm = ref({
  urgent: false,
  customerSentiment: 'neutral',
  ownerName: '',
  comment: '',
  trackingLinks: ''
})
const showAddMaintenanceModal = ref(false)
const addMaintenanceSubmitting = ref(false)
const showMaintenanceCalendar = ref(false)
const maintenanceCalendarCursor = ref(new Date())
const showImplementationCalendar = ref(false)
const implementationCalendarCursor = ref(new Date())
const versionsLoading = ref(false)
const productVersions = ref([])
const versionsLoadedProductId = ref(null)
const versionsLoadedChatId = ref('')
const versionsLoadedKey = ref('')
const versionPreloadPromise = ref(null)
const versionPreloadKey = ref('')
const downloadVersion = ref('')
const downloadVersionTouched = ref(false)
const downloadUrl = ref('')
const downloadUrlLoading = ref(false)
const maintenanceCreateContext = ref(null)
const maintenanceContextLoadedChatId = ref('')
const maintenanceContextPreloadPromise = ref(null)
const maintenanceContextPreloadChatId = ref('')
const addMaintenanceForm = ref({
  maintenanceTime: '',
  maintenanceTypes: '',
  submitterName: '',
  maintenanceTitle: '',
  maintenanceVersion: '',
  maintenanceContext: ''
})
const showAddImplementationModal = ref(false)
const implementationContextLoading = ref(false)
const addImplementationSubmitting = ref(false)
const implementationContext = ref(null)
const implementationContextLoadedChatId = ref('')
const implementationContextPreloadPromise = ref(null)
const implementationContextPreloadChatId = ref('')
const addImplementationForm = ref({
  selectedProductId: '',
  template: '',
  formType: '',
  productAlias: '',
  deploymentDate: '',
  deploymentMethod: '远程部署',
  version: '',
  assetTypes: [],
  assetCount: '',
  virtualizationType: '',
  applicationServer: '',
  databaseSync: '',
  databaseExternal: '',
  redisExternal: '',
  sharedNfs: '',
  authMethods: [],
  businessDirections: [],
  backupMethod: '',
  dataEaseDatabase: '',
  dorisUsage: '',
  dataSourceType: '',
  aiModelType: '',
  dataScale: '',
  embeddedMode: '',
  customerJoined: '',
  analysisDirection: '',
  customerFocus: '',
  deploymentArchitecture: '',
  deploymentRecord: '',
  remainingIssues: '',
  remark: '',
  submitterUserId: '',
  submitterName: ''
})

const IMPLEMENTATION_DEPLOYMENT_METHOD_OPTIONS = ['远程部署', '客户自行部署', '现场部署', '代理商部署']
const IMPLEMENTATION_ASSET_TYPE_OPTIONS = ['Linux', 'Windows', '交换机', '数据库', 'K8S']
const IMPLEMENTATION_ASSET_COUNT_OPTIONS = ['50左右', '100', '500以下', '1000', '3000', '3000以上']
const MAINTENANCE_CALENDAR_WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']
const IMPLEMENTATION_VIRTUALIZATION_OPTIONS = ['Vmware', '公有阿里云', '私有华为云', '无']
const IMPLEMENTATION_APPLICATION_SERVER_OPTIONS = ['是，多台', '是，单台', '否，不涉及']
const IMPLEMENTATION_DATABASE_SYNC_OPTIONS = ['是，从云管同步', '是，从客户机程序推送', '是，从其他软件同步', '否，不涉及']
const IMPLEMENTATION_DATABASE_EXTERNAL_OPTIONS = ['是，主备模式', '是，云上数据库', '是，客户内部提供', '是，部署主主模式', '是，部署PXC集群', '否，使用JumpServer自带数据库', '否，在应用服务器上部署数据库']
const IMPLEMENTATION_REDIS_EXTERNAL_OPTIONS = ['是，云上Redis服务', '是，客户内部提供', '是，部署主备模式', '是，部署哨兵模式', '否，使用JumpServer自带的Redis']
const IMPLEMENTATION_SHARED_NFS_OPTIONS = ['是，云上共享存储', '是，客户内部提供', '否，手动部署NFS', '否，使用rsync同步', '否，不使用NFS', '否，使用PV']
const IMPLEMENTATION_ARCHITECTURE_OPTIONS = ['无', '主备模式', '单节点', '集群模式', '分布式模式', 'K8S部署']
const IMPLEMENTATION_MAXKB_AUTH_OPTIONS = ['OIDC', 'CAS', 'LDAP', '企业微信', '钉钉', '飞书', '无']
const IMPLEMENTATION_MAXKB_DIRECTION_OPTIONS = ['产品咨询', '客户引导', '售后服务', '智能办公', '知识管理', '文档助手', '流程自动化', '数据分析', '智能推荐', '业务系统+大模型', '内部问答', '智能客服', '未知']
const IMPLEMENTATION_DATAEASE_BACKUP_OPTIONS = ['快照备份', '备份脚本本地备份', '备份脚本异地备份']
const IMPLEMENTATION_DATAEASE_DATABASE_OPTIONS = ['是，云上数据库', '是，客户内部提供', '是，部署主备模式', '否，使用 DataEase 自带数据库', '否，在应用服务器上部署数据库']
const IMPLEMENTATION_DATAEASE_DORIS_OPTIONS = ['不涉及', '是，云上 Doris', '是，客户内部提供', '是，独立部署 Doris', '否，使用 DataEase 自带 Doris']
const IMPLEMENTATION_DATAEASE_DATA_SOURCE_OPTIONS = ['MySQL', 'PostgreSQL', 'Oracle', 'SQL Server', 'Excel', 'API', 'Doris', 'ClickHouse', 'StarRocks', 'Hive', 'Elasticsearch', '其他']
const IMPLEMENTATION_DATAEASE_DATA_SCALE_OPTIONS = ['1GB以下', '1GB-10GB', '10GB-100GB', '100GB以上', 'TB级']
const IMPLEMENTATION_DATAEASE_AUTH_OPTIONS = ['OIDC', 'CAS', 'LDAP', '企业微信', '钉钉', '国际飞书', '飞书']
const IMPLEMENTATION_DATAEASE_EMBEDDED_OPTIONS = ['不涉及', 'iframe 嵌入', 'SDK 嵌入', '单点登录集成', '门户菜单集成']
const IMPLEMENTATION_DATAEASE_CUSTOMER_JOINED_OPTIONS = ['已接入', '部分接入', '未接入', '待客户提供信息']
const IMPLEMENTATION_SQLBOT_BACKUP_OPTIONS = ['快照备份', '备份脚本本地备份', '备份脚本异地备份']
const IMPLEMENTATION_SQLBOT_POSTGRES_OPTIONS = ['是，云上数据库', '是，客户内部提供', '否，使用 SQLBot 自带数据库', '否，在应用服务器上部署数据库']
const IMPLEMENTATION_SQLBOT_ARCHITECTURE_OPTIONS = ['单节点', '冷备模式', '热备模式', '集群模式', 'K8S']
const IMPLEMENTATION_SQLBOT_DATA_SOURCE_OPTIONS = ['MySQL', 'PostgreSQL', 'Oracle', 'SQL Server', 'ClickHouse', 'Doris', 'StarRocks', 'Hive', 'API', '其他']
const IMPLEMENTATION_SQLBOT_AI_MODEL_OPTIONS = ['OpenAI 兼容模型', 'MaxKB', '私有大模型', '公有云大模型', '其他']
const IMPLEMENTATION_SQLBOT_PLATFORM_OPTIONS = ['无', 'OIDC', 'CAS', 'LDAP', 'OAuth2.0']
const IMPLEMENTATION_SQLBOT_EMBEDDED_OPTIONS = ['无', '是，小助手嵌入', '是，页面嵌入']

const implementationProductOptions = computed(() => Array.isArray(implementationContext.value?.productOptions)
  ? implementationContext.value.productOptions
  : [])
const isImplementationDraftMode = computed(() => Boolean(implementationContext.value?.draftMode))
const shouldShowImplementationBasicInfo = computed(() => !isImplementationDraftMode.value && Boolean(implementationContext.value?.subscriptionId))
const implementationBasicInfoRows = computed(() => {
  const context = implementationContext.value || {}
  return [
    { label: '客户全称', value: context.clientName },
    { label: '产品名称', value: context.productName },
    { label: '合同编号', value: context.contractNumber },
    { label: '服务类型', value: context.serviceTypeName },
    { label: '销售', value: context.salesName },
    { label: '区域', value: context.regionName },
    { label: '订阅开始时间', value: context.subscriptionStartDate },
    { label: '维保结束时间', value: context.supportEndDate }
  ]
})
const selectedImplementationProductOption = computed(() => {
  const selectedProductId = String(addImplementationForm.value.selectedProductId || '')
  if (!selectedProductId) {
    return null
  }
  return implementationProductOptions.value.find(option => String(option?.productId || '') === selectedProductId) || null
})
const implementationProductAlias = computed(() => {
  return addImplementationForm.value.productAlias || selectedImplementationProductOption.value?.productAlias || implementationContext.value?.productAlias || ''
})
const implementationFormType = computed(() => {
  return addImplementationForm.value.formType || selectedImplementationProductOption.value?.formType || implementationContext.value?.formType || implementationProductAlias.value || ''
})
const isJumpServerImplementation = computed(() => implementationFormType.value === 'JS')
const isMaxKbImplementation = computed(() => implementationFormType.value === 'MK')
const isDataEaseImplementation = computed(() => implementationFormType.value === 'DE')
const isSqlBotImplementation = computed(() => implementationFormType.value === 'SQLBOT')

const getDefaultImplementationProductFields = (formType = '') => {
  const defaults = {
    assetTypes: [],
    assetCount: '',
    virtualizationType: '',
    applicationServer: '',
    databaseSync: '',
    databaseExternal: '',
    redisExternal: '',
    sharedNfs: '',
    authMethods: [],
    businessDirections: [],
    backupMethod: '',
    dataEaseDatabase: '',
    dorisUsage: '',
    dataSourceType: '',
    aiModelType: '',
    dataScale: '',
    embeddedMode: '',
    customerJoined: '',
    analysisDirection: '',
    customerFocus: '',
    deploymentArchitecture: '',
    deploymentRecord: ''
  }

  if (formType === 'JS') {
    return {
      ...defaults,
      virtualizationType: '无',
      applicationServer: '是，单台',
      databaseSync: '否，不涉及',
      databaseExternal: '否，在应用服务器上部署数据库',
      redisExternal: '否，使用JumpServer自带的Redis',
      sharedNfs: '否，不使用NFS'
    }
  }

  return defaults
}

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

const applyLocalDebugLogin = async () => {
  const debugUserId = (DEBUG_EDITOR_USER_ID || '').trim()
  if (!debugUserId) {
    throw new Error('未配置 DEBUG_EDITOR_USER_ID')
  }

  const response = await jsapiApi.debugLogin(debugUserId)
  if (!(response.success || response.code === 0)) {
    throw new Error(response.message || response.msg || '调试登录失败')
  }

  const debugUserInfo = response.data || {
    userid: debugUserId,
    UserId: debugUserId,
    user_id: debugUserId,
    name: debugUserId,
    avatar: ''
  }

  userStore.$patch({
    isLoggedIn: true,
    userInfo: debugUserInfo,
    isAdmin: !!debugUserInfo.isAdmin
  })
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

const getAcceptanceBadgeClass = (statusCode) => {
  if (statusCode === 'accepted') return 'badge-success'
  if (statusCode === 'pending') return 'badge-warning'
  return 'badge-neutral'
}

const isValidEmail = (value) => {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value || '')
}

const splitEmailParts = (value) => {
  return String(value || '')
    .replace(/；/g, ';')
    .replace(/，/g, ';')
    .replace(/,/g, ';')
    .split(';')
    .map(item => item.trim())
    .filter(Boolean)
}

const normalizeEmailList = (emails) => {
  const deduped = []
  const seen = new Set()
  ;(emails || []).forEach((item) => {
    const email = String(item || '').trim().toLowerCase()
    if (!email || seen.has(email)) return
    seen.add(email)
    deduped.push(email)
  })
  return deduped
}

const formatCcEmailList = (emails) => {
  return normalizeEmailList(emails).join(';')
}

const parseEmailInput = (value, fieldLabel, allowEmpty = true) => {
  const parts = splitEmailParts(value)
  if (parts.length === 0) {
    if (allowEmpty) return []
    throw new Error(`请输入${fieldLabel}`)
  }
  const invalid = parts.find(item => !isValidEmail(item))
  if (invalid) {
    throw new Error(`${fieldLabel}格式不正确：${invalid}`)
  }
  return normalizeEmailList(parts)
}

const parseCcEmailsInput = (value, allowEmpty = true) => {
  return parseEmailInput(value, '抄送邮箱', allowEmpty)
}

const loadToolMailDefaultCc = async (extChatId) => {
  if (!extChatId) {
    toolCcEmails.value = DEFAULT_TOOL_MAIL_CC
    return
  }
  try {
    const result = await docApi.getMailDefaultCc(extChatId)
    if (result?.success) {
      const data = result?.data || {}
      const list = Array.isArray(data.defaultCcList) ? data.defaultCcList : []
      const formatted = formatCcEmailList(list)
      toolCcEmails.value = formatted || DEFAULT_TOOL_MAIL_CC
      return
    }
  } catch (error) {
    console.warn('loadToolMailDefaultCc failed:', error)
  }
  toolCcEmails.value = DEFAULT_TOOL_MAIL_CC
}

const doAdjustTextarea = (textarea) => {
  if (!textarea) return
  textarea.style.height = 'auto'
  const borderHeight = textarea.offsetHeight - textarea.clientHeight
  const minHeight = Number.parseFloat(window.getComputedStyle(textarea).minHeight) || 56
  const maxHeight = Number.parseFloat(window.getComputedStyle(textarea).maxHeight) || Infinity
  const nextHeight = Math.max(textarea.scrollHeight + borderHeight, minHeight)
  textarea.style.height = `${Math.min(nextHeight, maxHeight)}px`
  textarea.style.overflowY = nextHeight > maxHeight ? 'auto' : 'hidden'
}

const doAdjustToolEmailTextarea = () => {
  doAdjustTextarea(toolEmailTextareaRef.value)
}

const doAdjustToolCcTextarea = () => {
  doAdjustTextarea(toolCcTextareaRef.value)
}

const adjustToolEmailTextarea = async () => {
  await nextTick()
  doAdjustToolEmailTextarea()
  requestAnimationFrame(() => {
    doAdjustToolEmailTextarea()
  })
}

const adjustToolCcTextarea = async () => {
  await nextTick()
  doAdjustToolCcTextarea()
  requestAnimationFrame(() => {
    doAdjustToolCcTextarea()
  })
}

const doAdjustImplementationCustomerFocusTextarea = () => {
  doAdjustTextarea(implementationCustomerFocusTextareaRef.value)
}

const adjustImplementationCustomerFocusTextarea = async () => {
  await nextTick()
  doAdjustImplementationCustomerFocusTextarea()
  requestAnimationFrame(() => {
    doAdjustImplementationCustomerFocusTextarea()
  })
}

const doAdjustImplementationDeploymentRecordTextarea = () => {
  doAdjustTextarea(implementationDeploymentRecordTextareaRef.value)
}

const adjustImplementationDeploymentRecordTextarea = async () => {
  await nextTick()
  doAdjustImplementationDeploymentRecordTextarea()
  requestAnimationFrame(() => {
    doAdjustImplementationDeploymentRecordTextarea()
  })
}

const doAdjustImplementationRemainingIssuesTextarea = () => {
  doAdjustTextarea(implementationRemainingIssuesTextareaRef.value)
}

const adjustImplementationRemainingIssuesTextarea = async () => {
  await nextTick()
  doAdjustImplementationRemainingIssuesTextarea()
  requestAnimationFrame(() => {
    doAdjustImplementationRemainingIssuesTextarea()
  })
}

const doAdjustImplementationRemarkTextarea = () => {
  doAdjustTextarea(implementationRemarkTextareaRef.value)
}

const adjustImplementationRemarkTextarea = async () => {
  await nextTick()
  doAdjustImplementationRemarkTextarea()
  requestAnimationFrame(() => {
    doAdjustImplementationRemarkTextarea()
  })
}

const doAdjustImplementationTextareas = () => {
  doAdjustImplementationCustomerFocusTextarea()
  doAdjustImplementationDeploymentRecordTextarea()
  doAdjustImplementationRemainingIssuesTextarea()
  doAdjustImplementationRemarkTextarea()
}

const pad2 = (value) => String(value).padStart(2, '0')

const sanitizeFileNamePart = (value) => {
  const text = String(value || '').trim()
  if (!text) {
    return ''
  }
  return text
    .replace(/[\\/:*?"<>|\r\n]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

const getAcceptanceReportFormData = (result) => {
  const payload = result?.data?.requestPayload
  if (payload && typeof payload === 'object') {
    if (payload.form_data && typeof payload.form_data === 'object') {
      return payload.form_data
    }
    return payload
  }
  return {}
}

const buildReportDownloadName = (result, extChatId) => {
  const formData = getAcceptanceReportFormData(result)
  const finalCustomer = sanitizeFileNamePart(formData?.finalcustomer)
  const productName = sanitizeFileNamePart(formData?.productname)
  const partya = sanitizeFileNamePart(formData?.partya)
  if (finalCustomer && productName) {
    return `${partya}-${finalCustomer}${productName}验收报告.docx`
  }
  const now = new Date()
  const yyyy = now.getFullYear()
  const mm = pad2(now.getMonth() + 1)
  const dd = pad2(now.getDate())
  const hh = pad2(now.getHours())
  const mi = pad2(now.getMinutes())
  const ss = pad2(now.getSeconds())
  return `acceptance-report-${extChatId}-${yyyy}${mm}${dd}${hh}${mi}${ss}.docx`
}

const normalizeBase64 = (value) => {
  if (typeof value !== 'string') {
    return ''
  }
  let text = value.trim()
  if (!text) {
    return ''
  }
  const dataUriPrefixMatch = text.match(/^data:[^;]+;base64,/i)
  if (dataUriPrefixMatch) {
    text = text.slice(dataUriPrefixMatch[0].length)
  }
  if ((text.startsWith('"') && text.endsWith('"')) || (text.startsWith("'") && text.endsWith("'"))) {
    text = text.slice(1, -1)
  }
  text = text.replace(/\s+/g, '').replace(/-/g, '+').replace(/_/g, '/')
  if (!/^[A-Za-z0-9+/=]+$/.test(text)) {
    return ''
  }
  const mod = text.length % 4
  if (mod) {
    text += '='.repeat(4 - mod)
  }
  return text.length >= 100 ? text : ''
}

const concatUint8Arrays = (chunks) => {
  if (!Array.isArray(chunks) || chunks.length === 0) {
    return new Uint8Array(0)
  }
  const total = chunks.reduce((sum, item) => sum + (item?.length || 0), 0)
  const merged = new Uint8Array(total)
  let offset = 0
  for (const item of chunks) {
    if (!(item instanceof Uint8Array) || item.length === 0) {
      continue
    }
    merged.set(item, offset)
    offset += item.length
  }
  return merged
}

const zipMagic = [
  [0x50, 0x4b, 0x03, 0x04],
  [0x50, 0x4b, 0x05, 0x06],
  [0x50, 0x4b, 0x07, 0x08]
]

const findZipHeaderOffset = (bytes) => {
  if (!(bytes instanceof Uint8Array) || bytes.length < 4) {
    return -1
  }
  for (let i = 0; i <= bytes.length - 4; i++) {
    for (const magic of zipMagic) {
      if (bytes[i] === magic[0] && bytes[i + 1] === magic[1] && bytes[i + 2] === magic[2] && bytes[i + 3] === magic[3]) {
        return i
      }
    }
  }
  return -1
}

const normalizeZipBytes = (bytes) => {
  if (!(bytes instanceof Uint8Array) || bytes.length < 4) {
    return new Uint8Array(0)
  }
  const offset = findZipHeaderOffset(bytes)
  if (offset < 0) {
    return new Uint8Array(0)
  }
  return offset === 0 ? bytes : bytes.subarray(offset)
}

const parseEscapedBytesText = (value) => {
  if (typeof value !== 'string') {
    return null
  }
  let text = value.trim()
  if (!text) {
    return null
  }
  if ((text.startsWith('"') && text.endsWith('"')) || (text.startsWith("'") && text.endsWith("'"))) {
    text = text.slice(1, -1)
  }
  const bytes = []
  let escapeCount = 0
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (ch !== '\\') {
      bytes.push(ch.charCodeAt(0) & 0xff)
      continue
    }
    if (i + 1 >= text.length) {
      bytes.push(92)
      continue
    }
    const next = text[++i]
    if (next === 'x' && i + 2 < text.length) {
      const hex = text.slice(i + 1, i + 3)
      if (/^[0-9a-fA-F]{2}$/.test(hex)) {
        bytes.push(parseInt(hex, 16))
        i += 2
        escapeCount++
        continue
      }
      bytes.push('x'.charCodeAt(0))
      continue
    }
    if (next >= '0' && next <= '7') {
      let oct = next
      let count = 1
      while (count < 3 && i + 1 < text.length && /[0-7]/.test(text[i + 1])) {
        oct += text[++i]
        count++
      }
      bytes.push(parseInt(oct, 8))
      escapeCount++
      continue
    }
    const escapedMap = {
      '\\': 92,
      "'": 39,
      '"': 34,
      n: 10,
      r: 13,
      t: 9,
      a: 7,
      b: 8,
      f: 12,
      v: 11
    }
    if (Object.prototype.hasOwnProperty.call(escapedMap, next)) {
      bytes.push(escapedMap[next])
      escapeCount++
      continue
    }
    bytes.push(next.charCodeAt(0) & 0xff)
  }
  if (escapeCount === 0) {
    return null
  }
  return new Uint8Array(bytes)
}

const parsePythonBytesLiteral = (value) => {
  if (typeof value !== 'string') {
    return null
  }
  let text = value.trim()
  if (!text) {
    return null
  }
  if ((text.startsWith('"') && text.endsWith('"')) || (text.startsWith("'") && text.endsWith("'"))) {
    text = text.slice(1, -1).trim()
  }
  if (!(text.startsWith("b'") || text.startsWith('b"'))) {
    return null
  }
  if (text.length < 3) {
    return null
  }
  const quote = text[1]
  if (text[text.length - 1] !== quote) {
    return null
  }
  const body = text.slice(2, -1)
  const bytes = []
  for (let i = 0; i < body.length; i++) {
    const ch = body[i]
    if (ch !== '\\') {
      bytes.push(ch.charCodeAt(0) & 0xff)
      continue
    }
    if (i + 1 >= body.length) {
      bytes.push(92)
      continue
    }
    const next = body[++i]
    if (next === 'x' && i + 2 < body.length) {
      const hex = body.slice(i + 1, i + 3)
      if (/^[0-9a-fA-F]{2}$/.test(hex)) {
        bytes.push(parseInt(hex, 16))
        i += 2
        continue
      }
      bytes.push('x'.charCodeAt(0))
      continue
    }
    if (next >= '0' && next <= '7') {
      let oct = next
      let count = 1
      while (count < 3 && i + 1 < body.length && /[0-7]/.test(body[i + 1])) {
        oct += body[++i]
        count++
      }
      bytes.push(parseInt(oct, 8))
      continue
    }
    const escapedMap = {
      '\\': 92,
      "'": 39,
      '"': 34,
      n: 10,
      r: 13,
      t: 9,
      a: 7,
      b: 8,
      f: 12,
      v: 11
    }
    if (Object.prototype.hasOwnProperty.call(escapedMap, next)) {
      bytes.push(escapedMap[next])
      continue
    }
    bytes.push(next.charCodeAt(0) & 0xff)
  }
  return new Uint8Array(bytes)
}

const deescapeForPythonBytesParsing = (value) => {
  if (typeof value !== 'string') {
    return ''
  }
  let text = value
  // 常见转义：b\'...\' / b\"...\"
  text = text.replace(/b\\'/g, "b'").replace(/b\\"/g, 'b"')
  // 常见双反斜杠转义：\\xNN -> \xNN
  text = text.replace(/\\\\x([0-9a-fA-F]{2})/g, '\\x$1')
  // 统一收敛剩余双反斜杠
  text = text.replace(/\\\\/g, '\\')
  // 去掉引号转义
  text = text.replace(/\\'/g, "'").replace(/\\"/g, '"')
  return text
}

const extractPythonBytesFromLooseText = (text) => {
  if (typeof text !== 'string') {
    return new Uint8Array(0)
  }
  const source = text.trim()
  if (!source) {
    return new Uint8Array(0)
  }
  const candidates = [source, deescapeForPythonBytesParsing(source)]

  for (const candidateText of candidates) {
    const candidate = (candidateText || '').trim()
    if (!candidate) {
      continue
    }
    const chunks = []
    let i = 0
    while (i < candidate.length - 2) {
      if (candidate[i] !== 'b' || (candidate[i + 1] !== "'" && candidate[i + 1] !== '"')) {
        i++
        continue
      }
      const quote = candidate[i + 1]
      let j = i + 2
      let foundEnd = false
      while (j < candidate.length) {
        const ch = candidate[j]
        if (ch === '\\') {
          j += 2
          continue
        }
        if (ch === quote) {
          const literal = candidate.slice(i, j + 1)
          const parsed = parsePythonBytesLiteral(literal)
          if (parsed && parsed.length > 0) {
            chunks.push(parsed)
          }
          i = j + 1
          foundEnd = true
          break
        }
        j++
      }
      if (!foundEnd) {
        break
      }
    }
    if (chunks.length > 0) {
      return concatUint8Arrays(chunks)
    }
  }
  const escaped = parseEscapedBytesText(source)
  if (escaped && escaped.length > 0) {
    return escaped
  }
  const escapedDeescaped = parseEscapedBytesText(deescapeForPythonBytesParsing(source))
  return escapedDeescaped || new Uint8Array(0)
}

const extractPythonBytesFromNode = (node, depth = 0) => {
  if (depth > 8 || node === null || node === undefined) {
    return new Uint8Array(0)
  }
  if (typeof node === 'string') {
    const parsed = parsePythonBytesLiteral(node)
    if (parsed && parsed.length > 0) {
      return parsed
    }
    const text = node.trim()
    if (text && (text.startsWith('[') || text.startsWith('{'))) {
      try {
        const parsedJson = JSON.parse(text)
        return extractPythonBytesFromNode(parsedJson, depth + 1)
      } catch (error) {
        // ignore invalid JSON
      }
    }
    const fromLoose = extractPythonBytesFromLooseText(text)
    if (fromLoose.length > 0) {
      return fromLoose
    }
    return new Uint8Array(0)
  }
  if (Array.isArray(node)) {
    const literalChunks = []
    let allLiteral = node.length > 0
    for (const item of node) {
      if (typeof item !== 'string') {
        allLiteral = false
        break
      }
      const chunk = parsePythonBytesLiteral(item)
      if (!chunk) {
        allLiteral = false
        break
      }
      literalChunks.push(chunk)
    }
    if (allLiteral && literalChunks.length > 0) {
      return concatUint8Arrays(literalChunks)
    }
    for (const item of node) {
      const found = extractPythonBytesFromNode(item, depth + 1)
      if (found.length > 0) {
        return found
      }
    }
    return new Uint8Array(0)
  }
  if (typeof node !== 'object') {
    return new Uint8Array(0)
  }
  for (const value of Object.values(node)) {
    const found = extractPythonBytesFromNode(value, depth + 1)
    if (found.length > 0) {
      return found
    }
  }
  return new Uint8Array(0)
}

const extractBase64FromObject = (node, depth = 0) => {
  if (depth > 6 || node === null || node === undefined) {
    return ''
  }
  if (typeof node === 'string') {
    return normalizeBase64(node)
  }
  if (Array.isArray(node)) {
    for (const item of node) {
      const found = extractBase64FromObject(item, depth + 1)
      if (found) {
        return found
      }
    }
    return ''
  }
  if (typeof node !== 'object') {
    return ''
  }

  const priorityKeys = ['reportBase64', 'docxBase64', 'base64', 'fileBase64', 'docBase64', 'wordBase64', 'fileStream', 'file_stream', 'data', 'result', 'content']
  for (const key of priorityKeys) {
    if (Object.prototype.hasOwnProperty.call(node, key)) {
      const found = extractBase64FromObject(node[key], depth + 1)
      if (found) {
        return found
      }
    }
  }

  for (const value of Object.values(node)) {
    const found = extractBase64FromObject(value, depth + 1)
    if (found) {
      return found
    }
  }
  return ''
}

const extractAcceptanceReportBase64 = (data) => {
  const fromReportField = normalizeBase64(data?.reportBase64 || data?.docxBase64 || '')
  if (fromReportField) {
    return fromReportField
  }

  const raw = typeof data?.upstreamRaw === 'string' ? data.upstreamRaw.trim() : ''
  const rawBase64 = normalizeBase64(raw)
  if (rawBase64) {
    return rawBase64
  }

  if (raw && (raw.startsWith('{') || raw.startsWith('['))) {
    try {
      const parsedRaw = JSON.parse(raw)
      const fromRawJson = extractBase64FromObject(parsedRaw)
      if (fromRawJson) {
        return fromRawJson
      }
    } catch (error) {
      // ignore invalid JSON
    }
  }

  const fromUpstreamJson = extractBase64FromObject(data?.upstreamJson)
  if (fromUpstreamJson) {
    return fromUpstreamJson
  }

  if (typeof data?.upstreamJson === 'string') {
    const upstreamJsonStr = data.upstreamJson.trim()
    const directBase64 = normalizeBase64(upstreamJsonStr)
    if (directBase64) {
      return directBase64
    }
    if (upstreamJsonStr.startsWith('{') || upstreamJsonStr.startsWith('[')) {
      try {
        const parsed = JSON.parse(upstreamJsonStr)
        return extractBase64FromObject(parsed)
      } catch (error) {
        // ignore invalid JSON
      }
    }
  }
  return ''
}

const base64ToBytes = (base64) => {
  const binary = window.atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

const bytesToWordBlob = (bytes) => {
  const normalized = normalizeZipBytes(bytes)
  if (!(normalized instanceof Uint8Array) || normalized.length === 0) {
    return null
  }
  return new Blob([normalized], {
    type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  })
}

const base64ToWordBlob = (base64) => {
  try {
    const bytes = base64ToBytes(base64)
    const strictBlob = bytesToWordBlob(bytes)
    if (strictBlob) {
      return strictBlob
    }
    if (bytes instanceof Uint8Array && bytes.length > 0) {
      // Fallback: if header detection fails but base64 is decodable, still download for inspection.
      return new Blob([bytes], {
        type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      })
    }
    return null
  } catch (error) {
    return null
  }
}

const parseContentFieldToBytes = (content) => {
  if (typeof content !== 'string') {
    return new Uint8Array(0)
  }
  const trimmed = content.trim()
  if (!trimmed) {
    return new Uint8Array(0)
  }
  const py = parsePythonBytesLiteral(trimmed)
  if (py && py.length > 0) {
    return py
  }
  const pyDeescaped = parsePythonBytesLiteral(deescapeForPythonBytesParsing(trimmed))
  if (pyDeescaped && pyDeescaped.length > 0) {
    return pyDeescaped
  }
  const fromLoose = extractPythonBytesFromLooseText(trimmed)
  if (fromLoose.length > 0) {
    return fromLoose
  }
  const escaped = parseEscapedBytesText(trimmed)
  if (escaped && escaped.length > 0) {
    return escaped
  }
  const escapedDeescaped = parseEscapedBytesText(deescapeForPythonBytesParsing(trimmed))
  if (escapedDeescaped && escapedDeescaped.length > 0) {
    return escapedDeescaped
  }
  const b64 = normalizeBase64(trimmed)
  if (b64) {
    try {
      return base64ToBytes(b64)
    } catch (error) {
      // ignore
    }
  }
  return new Uint8Array(0)
}

const extractBlobFromSseEvents = (upstreamJson) => {
  if (!upstreamJson || upstreamJson.isSse !== true || !Array.isArray(upstreamJson.events)) {
    return null
  }
  const chunks = []
  for (const event of upstreamJson.events) {
    const candidates = []
    if (typeof event === 'string') {
      const text = event.trim()
      if (text) {
        candidates.push(text)
        if (text.startsWith('data:')) {
          candidates.push(text.slice(5).trim())
        }
      }
    } else if (event && typeof event === 'object') {
      candidates.push(event.content)
      try {
        candidates.push(JSON.stringify(event))
      } catch (error) {
        // ignore stringify error
      }
    }
    for (const candidate of candidates) {
      const bytes = parseContentFieldToBytes(candidate)
      if (bytes.length > 0) {
        chunks.push(bytes)
        break
      }
    }
  }
  if (chunks.length === 0) {
    return null
  }
  return bytesToWordBlob(concatUint8Arrays(chunks))
}

const extractAcceptanceReportBlob = (data) => {
  const fromSse = extractBlobFromSseEvents(data?.upstreamJson)
  if (fromSse) {
    return fromSse
  }

  const pythonRawBytes = extractPythonBytesFromNode(data?.upstreamRaw)
  if (pythonRawBytes.length > 0) {
    return bytesToWordBlob(pythonRawBytes)
  }

  const pythonJsonBytes = extractPythonBytesFromNode(data?.upstreamJson)
  if (pythonJsonBytes.length > 0) {
    return bytesToWordBlob(pythonJsonBytes)
  }

  const reportBase64 = extractAcceptanceReportBase64(data)
  if (!reportBase64) {
    return null
  }
  return base64ToWordBlob(reportBase64)
}

const resolvePrimaryReportBlob = (result) => {
  const data = result?.data || {}
  const primaryBase64 = normalizeBase64(data?.reportBase64 || data?.docxBase64 || result?.reportBase64 || result?.docxBase64 || '')
  if (!primaryBase64) {
    return { blob: null, reason: 'no_file_content' }
  }
  const blob = base64ToWordBlob(primaryBase64)
  if (!blob) {
    return { blob: null, reason: 'invalid_base64' }
  }
  return { blob, reason: '' }
}

const trimLogText = (value, max = 3000) => {
  if (typeof value !== 'string') {
    return value
  }
  const text = value.trim()
  if (text.length <= max) {
    return text
  }
  return `${text.slice(0, max)}...(truncated, total=${text.length})`
}

const logAcceptanceReportDebug = (stage, data) => {
  try {
    const raw = typeof data?.upstreamRaw === 'string' ? data.upstreamRaw : ''
    const sseLines = raw
      ? raw.split(/\r?\n/).map(line => line.trim()).filter(line => line.startsWith('data:'))
      : []
    console.groupCollapsed(`[acceptance-report] ${stage}`)
    console.info('upstreamRaw.length:', raw.length)
    console.info('upstreamRaw.preview:', trimLogText(raw, 8000))
    console.info('upstreamJson:', data?.upstreamJson)
    const sseEventCount = data?.upstreamJson?.isSse === true && Array.isArray(data?.upstreamJson?.events)
      ? data.upstreamJson.events.length
      : 0
    console.info('sseEventCount:', sseEventCount)
    if (sseLines.length > 0) {
      console.info('sseDataLines:', sseLines.map(line => trimLogText(line, 2000)))
    }
    console.groupEnd()
  } catch (error) {
    console.warn('[acceptance-report] debug log failed:', error)
  }
}

const triggerReportDownload = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const buildToolMailErrorMessage = (rawMessage) => {
  const message = (rawMessage || '').toString().trim()
  const lower = message.toLowerCase()
  if (!message) {
    return '邮件发送失败：请稍后重试'
  }
  if (message.includes('tool.mail.from-address') || message.includes('tool.mail.auth-code') || message.includes('占位值')) {
    return '邮件发送失败：邮件配置缺失，请联系管理员补全发件配置'
  }
  if (lower.includes('stage=auth') || lower.includes('authentication failed') || lower.includes('535')) {
    return '邮件发送失败：邮箱认证失败，请检查客户端专用密码'
  }
  const invalidAddressMatch = message.match(/invalidAddresses=([^；]+)/)
  if (invalidAddressMatch?.[1]) {
    return `邮件发送失败：以下邮箱地址无效或不存在，请检查后重试：${invalidAddressMatch[1]}`
  }
  if (lower.includes('invalid addresses') || lower.includes('mailbox not found or access denied') || lower.includes('550 mailbox')) {
    return '邮件发送失败：存在无效邮箱地址，请检查收件人或抄送邮箱是否填写正确'
  }
  if (lower.includes('stage=connect')
    || lower.includes('timed out')
    || lower.includes('timeout of')
    || lower.includes('connection refused')
    || lower.includes('unknownhost')
    || lower.includes('nodename nor servname')) {
    return '邮件发送失败：SMTP 连接异常，请稍后重试或联系管理员检查网络'
  }
  if (message.startsWith('邮件发送失败')) {
    return message
  }
  return `邮件发送失败：${message}`
}

const handleSendToolMail = async () => {
  if (!chatId.value) {
    showToast('未获取到当前群聊ID，无法发送邮件', false)
    return
  }

  let toList = []
  try {
    toList = parseEmailInput(toolEmail.value, '收件邮箱', false)
  } catch (error) {
    showToast(error?.message || '收件邮箱格式不正确', false)
    return
  }

  let ccList = []
  try {
    ccList = parseCcEmailsInput(toolCcEmails.value, true)
  } catch (error) {
    showToast(error?.message || '抄送邮箱格式不正确', false)
    return
  }

  toolMailSubmitting.value = true
  try {
    const payload = {
      toEmail: formatCcEmailList(toList),
      ccEmails: formatCcEmailList(ccList),
      customerName: customerData.value?.name || '',
      latestVersion: latestVersion.value || '',
      extChatId: chatId.value
    }
    const result = await docApi.sendToolMail(payload)
    if (result?.success) {
      const data = result?.data || {}
      const attached = Array.isArray(data.attachedAttachments) ? data.attachedAttachments : []
      const linked = Array.isArray(data.linkedAttachments) ? data.linkedAttachments : []
      const skipped = Array.isArray(data.skippedAttachments) ? data.skippedAttachments : []
      const warning = data.warningMessage || ''
      const resolvedProduct = data.resolvedProduct || ''
      const appliedTo = Array.isArray(data.toEmails) ? data.toEmails : toList
      const appliedCc = Array.isArray(data.ccEmails) ? data.ccEmails : ccList

      let message = '邮件发送成功'
      if (attached.length > 0) {
        message = `邮件发送成功，已附带 ${attached.join('、')}`
        if (linked.length > 0) {
          message += `；已写入下载按钮 ${linked.join('、')}`
        }
      } else if (linked.length > 0) {
        message = `邮件发送成功，已写入下载按钮 ${linked.join('、')}`
      } else if (resolvedProduct === 'DataEase') {
        message = '邮件发送成功（当前产品无需附件）'
      } else if (warning) {
        message = `邮件发送成功（${warning}）`
      }
      if (skipped.length > 0) {
        message += `；缺失已跳过：${skipped.join('、')}`
      }
      if (appliedTo.length > 1) {
        message += `；收件 ${appliedTo.length} 个邮箱`
      }
      if (appliedCc.length > 0) {
        message += `；抄送 ${appliedCc.length} 个邮箱`
      }
      showToast(message, true)
      return
    }
    showToast(buildToolMailErrorMessage(result?.message || result?.errmsg), false)
  } catch (error) {
    showToast(buildToolMailErrorMessage(error?.message || error), false)
  } finally {
    toolMailSubmitting.value = false
  }
}

const handleGetAcceptanceReport = async () => {
  if (!chatId.value) {
    showToast('未获取到当前群聊ID，无法获取验收报告', false)
    return
  }

  toolReportSubmitting.value = true
  try {
    const payload = {
      extChatId: chatId.value
    }
    const result = await docApi.getAcceptanceReport(payload)
    if (result?.success) {
      const responseData = result?.data || {}
      const primary = resolvePrimaryReportBlob(result)
      let wordBlob = primary.blob
      if (!wordBlob) {
        wordBlob = extractAcceptanceReportBlob(responseData)
      }
      if (!wordBlob && result && result !== responseData) {
        wordBlob = extractAcceptanceReportBlob(result)
      }
      if (!wordBlob) {
        logAcceptanceReportDebug('parse-failed', {
          ...responseData,
          __rawResult: result
        })
        if (primary.reason === 'no_file_content') {
          showToast('获取验收报告失败: 无文件内容', false)
        } else if (primary.reason === 'invalid_base64') {
          showToast('获取验收报告失败: Base64 文件流无效', false)
        } else {
          showToast('获取验收报告失败: 文件写入失败', false)
        }
        return
      }
      const filename = buildReportDownloadName(result, chatId.value)
      triggerReportDownload(wordBlob, filename)
      logAcceptanceReportDebug('download-success', responseData)
      showToast('验收报告获取成功，已下载 Word', true)
      return
    }
    showToast(result?.message || result?.errmsg || '获取验收报告失败', false)
  } catch (error) {
    showToast('获取验收报告失败: ' + (error?.message || error), false)
  } finally {
    toolReportSubmitting.value = false
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
  customerDataLoading.value = true
  try {
    const res = await docApi.getCustomerData(extChatId)
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    if (res.success) {
      customerData.value = {
        ...(customerData.value || {}),
        ...(res.data || {})
      }
    } else {
      showToast('获取客户数据失败：' + res.message, false)
      customerData.value = {}
    }
  } catch (err) {
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    showToast('获取客户数据异常：' + (err.message || err), false)
    customerData.value = {}
  } finally {
    if (isCurrentChatTarget(extChatId)) {
      customerDataLoading.value = false
    }
  }
}

const clearAcceptanceStatusFields = () => {
  customerData.value = {
    ...(customerData.value || {}),
    isAccepted: '',
    acceptanceStatusCode: '',
    needAcceptanceReport: '',
    accepted: ''
  }
}

const clearAcceptanceStatusRefreshTimers = () => {
  acceptanceStatusRefreshTimers.forEach(timer => clearTimeout(timer))
  acceptanceStatusRefreshTimers = []
}

const hasAcceptanceStatusValue = (data) => {
  return ['isAccepted', 'acceptanceStatusCode', 'needAcceptanceReport', 'accepted']
    .some(key => String(data?.[key] || '').trim() !== '')
}

const isActiveAcceptanceStatusTarget = (extChatId, token) => {
  return acceptanceStatusRequestToken === token && chatId.value === extChatId
}

const fetchAcceptanceStatus = async (extChatId, options = {}) => {
  const {
    preserveCurrent = false,
    markLoading = false,
    token = acceptanceStatusRequestToken
  } = options

  if (markLoading) {
    acceptanceStatusLoading.value = true
  }
  if (!preserveCurrent) {
    clearAcceptanceStatusFields()
  }

  try {
    const res = await docApi.getAcceptanceStatus(extChatId)
    if (!isActiveAcceptanceStatusTarget(extChatId, token)) {
      return false
    }
    if (res.success && res.data) {
      customerData.value = {
        ...(customerData.value || {}),
        ...res.data
      }
      return hasAcceptanceStatusValue(res.data)
    }
  } catch (err) {
    // 验收状态异步加载，失败时静默降级，不阻塞首页
    return false
  } finally {
    if (markLoading && isActiveAcceptanceStatusTarget(extChatId, token)) {
      acceptanceStatusLoading.value = false
    }
  }

  return false
}

const scheduleAcceptanceStatusFollowUp = (extChatId, token, delayMs, remainingRetries = 0) => {
  const timer = setTimeout(async () => {
    acceptanceStatusRefreshTimers = acceptanceStatusRefreshTimers.filter(item => item !== timer)
    if (!isActiveAcceptanceStatusTarget(extChatId, token)) {
      return
    }
    const hasStatus = await fetchAcceptanceStatus(extChatId, {
      preserveCurrent: true,
      markLoading: false,
      token
    })
    if (!hasStatus && remainingRetries > 0 && isActiveAcceptanceStatusTarget(extChatId, token)) {
      scheduleAcceptanceStatusFollowUp(extChatId, token, 3200, remainingRetries - 1)
    }
  }, delayMs)
  acceptanceStatusRefreshTimers.push(timer)
}

const getAcceptanceStatus = async (extChatId) => {
  acceptanceStatusRequestToken += 1
  const token = acceptanceStatusRequestToken
  clearAcceptanceStatusRefreshTimers()
  const hasStatus = await fetchAcceptanceStatus(extChatId, {
    preserveCurrent: false,
    markLoading: true,
    token
  })
  if (!isActiveAcceptanceStatusTarget(extChatId, token)) {
    return
  }
  scheduleAcceptanceStatusFollowUp(extChatId, token, hasStatus ? 1500 : 1200, hasStatus ? 0 : 1)
}

const getMaintenanceRecords = async (extChatId) => {
  maintenanceLoading.value = true
  try {
    const res = await docApi.getMaintenanceRecords(extChatId)
    if (!isCurrentChatTarget(extChatId)) {
      return []
    }
    if (res.success) {
      const records = res.data || []
      maintenanceRecords.value = records
      return records
    } else {
      maintenanceRecords.value = []
      return []
    }
  } catch (err) {
    if (!isCurrentChatTarget(extChatId)) {
      return []
    }
    maintenanceRecords.value = []
    return []
  } finally {
    if (isCurrentChatTarget(extChatId)) {
      maintenanceLoading.value = false
    }
  }
}

const mergeCreatedImplementationRecord = (createdRecord) => {
  if (!createdRecord?.id) {
    return
  }
  const createdId = String(createdRecord.id)
  const exists = (maintenanceRecords.value || []).some(record => String(record?.id) === createdId)
  if (!exists) {
    maintenanceRecords.value = [createdRecord, ...(maintenanceRecords.value || [])]
  }
}

const getServiceRecords = async (extChatId) => {
  serviceLoading.value = true
  try {
    const res = await docApi.getServiceRecords(extChatId)
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    if (res.success) {
      serviceRecords.value = res.data || []
    } else {
      serviceRecords.value = []
    }
  } catch (err) {
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    serviceRecords.value = []
  } finally {
    if (isCurrentChatTarget(extChatId)) {
      serviceLoading.value = false
    }
  }
}

const loadContractSubscriptions = async (extChatId, { silent = false, force = false } = {}) => {
  if (!extChatId) {
    return []
  }
  if (!force && contractsLoadedChatId.value === extChatId) {
    return contractSubscriptions.value
  }

  contractsLoading.value = true
  try {
    const res = await docApi.getContractSubscriptions(extChatId)
    if (!isCurrentChatTarget(extChatId)) {
      return []
    }
    if (res.success || res.code === 0) {
      const items = Array.isArray(res.data) ? res.data : (res.data?.items || [])
      contractSubscriptions.value = items
      contractsLoadedChatId.value = extChatId
      return items
    }
    contractSubscriptions.value = []
    contractsLoadedChatId.value = ''
    if (!silent) {
      showToast(res.message || res.msg || '获取合同信息失败', false)
    }
    return []
  } catch (err) {
    if (!isCurrentChatTarget(extChatId)) {
      return []
    }
    contractSubscriptions.value = []
    contractsLoadedChatId.value = ''
    if (!silent) {
      showToast('获取合同信息失败: ' + (err.message || err), false)
    }
    return []
  } finally {
    if (isCurrentChatTarget(extChatId)) {
      contractsLoading.value = false
    }
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

const isCurrentChatTarget = (targetChatId) => {
  return !targetChatId || chatId.value === targetChatId
}

const getTickets = async (extChatId) => {
  ticketsLoading.value = true
  try {
    const res = await docApi.getTickets(extChatId)
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    if (res.success) {
      tickets.value = res.data || []
      syncIssueAndBugTickets()
    } else {
      tickets.value = []
      syncIssueAndBugTickets()
    }
  } catch (err) {
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    tickets.value = []
    syncIssueAndBugTickets()
  } finally {
    if (isCurrentChatTarget(extChatId)) {
      ticketsLoading.value = false
    }
  }
}

const getIssueTickets = async (extChatId) => {
  issueTicketsLoading.value = true
  try {
    const res = await docApi.getIssueTickets(extChatId)
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    if (res.success) {
      const list = Array.isArray(res.data) ? res.data : []
      issueTickets.value = list.length > 0
        ? list
        : tickets.value.filter(t => /需求/.test(t?.issueCategory || ''))
    } else {
      issueTickets.value = tickets.value.filter(t => /需求/.test(t?.issueCategory || ''))
    }
  } catch (err) {
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    issueTickets.value = tickets.value.filter(t => /需求/.test(t?.issueCategory || ''))
  } finally {
    if (isCurrentChatTarget(extChatId)) {
      issueTicketsLoading.value = false
    }
  }
}

const getBugTickets = async (extChatId) => {
  bugTicketsLoading.value = true
  try {
    const res = await docApi.getBugTickets(extChatId)
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    if (res.success) {
      const list = Array.isArray(res.data) ? res.data : []
      bugTickets.value = list.length > 0
        ? list
        : tickets.value.filter(t => (t?.issueCategory || '') === '产品缺陷')
    } else {
      bugTickets.value = tickets.value.filter(t => (t?.issueCategory || '') === '产品缺陷')
    }
  } catch (err) {
    if (!isCurrentChatTarget(extChatId)) {
      return
    }
    bugTickets.value = tickets.value.filter(t => (t?.issueCategory || '') === '产品缺陷')
  } finally {
    if (isCurrentChatTarget(extChatId)) {
      bugTicketsLoading.value = false
    }
  }
}

const resetTabLoadState = () => {
  tabLoadState.value = createTabLoadState()
}

const loadTicketTabData = async (targetChatId, { force = false } = {}) => {
  if (!targetChatId) return
  if (!isCurrentChatTarget(targetChatId)) return
  if (tabLoadState.value.ticket && !force) return
  await getTickets(targetChatId)
  if (!isCurrentChatTarget(targetChatId)) return
  tabLoadState.value.ticket = true
}

const loadRequirementTabData = async (targetChatId, { force = false } = {}) => {
  if (!targetChatId) return
  if (!isCurrentChatTarget(targetChatId)) return
  await loadTicketTabData(targetChatId, { force })
  if (!isCurrentChatTarget(targetChatId)) return
  if (tabLoadState.value.requirement && !force) return
  await getIssueTickets(targetChatId)
  if (!isCurrentChatTarget(targetChatId)) return
  tabLoadState.value.requirement = true
}

const loadDefectTabData = async (targetChatId, { force = false } = {}) => {
  if (!targetChatId) return
  if (!isCurrentChatTarget(targetChatId)) return
  await loadTicketTabData(targetChatId, { force })
  if (!isCurrentChatTarget(targetChatId)) return
  if (tabLoadState.value.defect && !force) return
  await getBugTickets(targetChatId)
  if (!isCurrentChatTarget(targetChatId)) return
  tabLoadState.value.defect = true
}

const loadImplementationTabData = async (targetChatId, { force = false } = {}) => {
  if (!targetChatId) return
  if (!isCurrentChatTarget(targetChatId)) return
  if (tabLoadState.value.implementation && !force) return
  await getMaintenanceRecords(targetChatId)
  if (!isCurrentChatTarget(targetChatId)) return
  tabLoadState.value.implementation = true
}

const loadMaintenanceTabData = async (targetChatId, { force = false } = {}) => {
  if (!targetChatId) return
  if (!isCurrentChatTarget(targetChatId)) return
  if (tabLoadState.value.maintenance && !force) return
  await getServiceRecords(targetChatId)
  if (!isCurrentChatTarget(targetChatId)) return
  tabLoadState.value.maintenance = true
}

const prefetchContractSubscriptions = (targetChatId = chatId.value) => {
  if (!targetChatId || !isCurrentChatTarget(targetChatId)) {
    return Promise.resolve([])
  }
  if (contractsLoadedChatId.value === targetChatId) {
    return Promise.resolve(contractSubscriptions.value)
  }
  if (contractsPreloadPromise.value && contractsPreloadChatId.value === targetChatId) {
    return contractsPreloadPromise.value
  }

  contractsPreloadChatId.value = targetChatId
  const preloadPromise = loadContractSubscriptions(targetChatId, { silent: true }).finally(() => {
    if (contractsPreloadPromise.value === preloadPromise) {
      contractsPreloadPromise.value = null
      contractsPreloadChatId.value = ''
    }
  })
  contractsPreloadPromise.value = preloadPromise
  return contractsPreloadPromise.value
}

const loadContractTabData = async (targetChatId, { force = false } = {}) => {
  if (!targetChatId) return
  if (!isCurrentChatTarget(targetChatId)) return
  if (tabLoadState.value.contract && !force) return
  await prefetchContractSubscriptions(targetChatId)
  if (!isCurrentChatTarget(targetChatId)) return
  tabLoadState.value.contract = contractsLoadedChatId.value === targetChatId
}

const loadToolsTabData = async (targetChatId, { force = false } = {}) => {
  if (!targetChatId) {
    toolCcEmails.value = DEFAULT_TOOL_MAIL_CC
    tabLoadState.value.tools = true
    return
  }
  if (!isCurrentChatTarget(targetChatId)) return
  if (tabLoadState.value.tools && !force) return
  await loadToolMailDefaultCc(targetChatId)
  if (!isCurrentChatTarget(targetChatId)) return
  tabLoadState.value.tools = true
}

const ensureActiveTabData = async (targetChatId, options = {}) => {
  if (!targetChatId) return
  if (activeTab.value === 'implementation') {
    await loadImplementationTabData(targetChatId, options)
    return
  }
  if (activeTab.value === 'maintenance') {
    await loadMaintenanceTabData(targetChatId, options)
    return
  }
  if (activeTab.value === 'contract') {
    await loadContractTabData(targetChatId, options)
    return
  }
  if (activeTab.value === 'tools') {
    await loadToolsTabData(targetChatId, options)
    return
  }
  if (activeTab.value === 'ticket') {
    await loadTicketTabData(targetChatId, options)
    return
  }
  if (activeTab.value === 'requirement') {
    await loadRequirementTabData(targetChatId, options)
    return
  }
  if (activeTab.value === 'defect') {
    await loadDefectTabData(targetChatId, options)
  }
}

const preloadVersionSources = async (targetChatId, options = {}) => {
  if (!targetChatId) return
  await Promise.all([
    loadImplementationTabData(targetChatId, options),
    loadMaintenanceTabData(targetChatId, options)
  ])
}

const getTicketLogs = async (ticketId) => {
  if (!ticketId) {
    return
  }
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

const getTrackingLinkList = (ticket) => {
  const raw = ticket?.trackingLinks
  if (raw == null) {
    return []
  }
  if (Array.isArray(raw)) {
    return raw.map(item => String(item || '').trim()).filter(Boolean)
  }
  if (typeof raw === 'string') {
    const text = raw.trim()
    if (!text) {
      return []
    }
    if (text.startsWith('[') && text.endsWith(']')) {
      try {
        const parsed = JSON.parse(text)
        if (Array.isArray(parsed)) {
          return parsed.map(item => String(item || '').trim()).filter(Boolean)
        }
      } catch (e) {
        // ignore parse error and fallback to newline split
      }
    }
    return text.split(/\r?\n/).map(item => item.trim()).filter(Boolean)
  }
  return [String(raw).trim()].filter(Boolean)
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

const getStatusCode = (ticket) => {
  const status = ticket?.status
  if (typeof status === 'number') return status
  if (typeof status === 'string') {
    const s = status.trim()
    if (/^\d+$/.test(s)) return Number(s)
    if (s.includes('跨团队')) return 3
    if (s.includes('跟进')) return 2
    if (s.includes('已处理') || s.includes('已解决')) return 4
    if (s.includes('待确认')) return 1
  }
  return null
}

const isCriticalTicket = (ticket) => getStatusCode(ticket) === 3
const isIssueCategory = (ticket) => (ticket?.issueCategory || '') === '功能需求'
const isBugCategory = (ticket) => (ticket?.issueCategory || '') === '产品缺陷'
const getTicketBaseList = () => tickets.value.filter(t => !isIssueCategory(t) && !isBugCategory(t))
const isUnresolvedNonCriticalTicket = (ticket) => !isResolvedTicket(ticket) && !isCriticalTicket(ticket)

const getResolvedCount = (list) => (Array.isArray(list) ? list.filter(isResolvedTicket).length : 0)
const getUnresolvedCount = (list) => (Array.isArray(list) ? list.filter(isUnresolvedNonCriticalTicket).length : 0)
const getCriticalCount = (list) => (Array.isArray(list) ? list.filter(isCriticalTicket).length : 0)

const getFilteredTickets = () => {
  const ticketBaseList = getTicketBaseList()
  let filtered = ticketBaseList

  if (ticketFilter.value === 'all') {
    filtered = ticketBaseList
  } else if (ticketFilter.value === 'resolved') {
    filtered = ticketBaseList.filter(isResolvedTicket)
  } else if (ticketFilter.value === 'unresolved') {
    filtered = ticketBaseList.filter(isUnresolvedNonCriticalTicket)
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
    filtered = issueTickets.value.filter(isUnresolvedNonCriticalTicket)
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
    filtered = bugTickets.value.filter(isUnresolvedNonCriticalTicket)
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
    'draft': '草稿',
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

const parseRecordTimestamp = (value) => {
  if (value === null || value === undefined || value === '') {
    return Number.NaN
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) {
      return Number.NaN
    }
    // 兼容秒级/毫秒级时间戳
    return value < 1e12 ? value * 1000 : value
  }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) {
      return Number.NaN
    }
    if (/^\d+$/.test(text)) {
      const numeric = Number(text)
      if (!Number.isFinite(numeric)) {
        return Number.NaN
      }
      return numeric < 1e12 ? numeric * 1000 : numeric
    }
    const parsed = Date.parse(text)
    return Number.isNaN(parsed) ? Number.NaN : parsed
  }
  return Number.NaN
}

const getLatestTimestampFromRecord = (record, fields = []) => {
  for (const field of fields) {
    const timestamp = parseRecordTimestamp(record?.[field])
    if (Number.isFinite(timestamp)) {
      return timestamp
    }
  }
  return 0
}

const compareVersionCore = (leftVersion, rightVersion) => {
  const left = (leftVersion || '').match(/v?(\d+)\.(\d+)\.(\d+)/i)
  const right = (rightVersion || '').match(/v?(\d+)\.(\d+)\.(\d+)/i)

  if (!left && !right) return 0
  if (left && !right) return 1
  if (!left && right) return -1

  const leftParts = [Number(left[1]), Number(left[2]), Number(left[3])]
  const rightParts = [Number(right[1]), Number(right[2]), Number(right[3])]
  for (let i = 0; i < leftParts.length; i += 1) {
    if (leftParts[i] > rightParts[i]) return 1
    if (leftParts[i] < rightParts[i]) return -1
  }
  return 0
}

const formatBadgeVersion = (version) => {
  const text = version || ''
  if (!text) return ''

  // 提取纯版本号 (如 v3.5.7 或 3.5.7)
  const versionMatch = text.match(/v?(\d+\.\d+\.\d+)/i)
  const versionNum = versionMatch ? `v${versionMatch[1]}` : text

  // 判断架构类型
  const lowerVersion = text.toLowerCase()
  if (lowerVersion.includes('arm')) {
    return `${versionNum}-arm64`
  } else if (lowerVersion.includes('x86') || lowerVersion.includes('amd')) {
    return `${versionNum}-x86`
  }
  // 纯版本号，不加架构后缀
  return versionNum
}

const latestVersion = computed(() => {
  const candidates = []

  maintenanceRecords.value.forEach((record, index) => {
    if (!record?.version) return
    candidates.push({
      version: record.version,
      timestamp: getLatestTimestampFromRecord(record, ['deploymentTime', 'createTime']),
      order: index
    })
  })

  const baseOrder = candidates.length
  serviceRecords.value.forEach((record, index) => {
    if (!record?.maintenanceVersion) return
    candidates.push({
      version: record.maintenanceVersion,
      timestamp: getLatestTimestampFromRecord(record, ['maintenanceTime', 'createTime']),
      order: baseOrder + index
    })
  })

  if (candidates.length === 0) return ''

  const latest = candidates.reduce((best, current) => {
    if (!best) return current

    const versionCompare = compareVersionCore(current.version, best.version)
    if (versionCompare > 0) return current
    if (versionCompare < 0) return best

    if (current.timestamp > best.timestamp) return current
    if (current.timestamp < best.timestamp) return best

    return current.order < best.order ? current : best
  }, null)

  return formatBadgeVersion(latest?.version || '')
})

const versionBadgeText = computed(() => {
  if (latestVersion.value) {
    return latestVersion.value
  }
  if (maintenanceLoading.value || serviceLoading.value) {
    return ''
  }
  return '请补充实施'
})

const customerDisplayName = computed(() => {
  if (customerDataLoading.value) {
    return '-'
  }
  return customerData.value?.name || '客户信息待补全'
})

const isSubscriptionDateExpired = (value) => {
  if (!value) return false
  const date = new Date(`${value}T00:00:00+08:00`)
  if (Number.isNaN(date.getTime())) return false
  const today = new Date()
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  return date.getTime() < todayStart
}

const latestContractSubscriptionEndDate = computed(() => {
  const dates = contractSubscriptions.value
    .map(contract => normalizeContractDate(contract?.supportEndDate || contract?.endDate))
    .filter(Boolean)
    .sort()
  return dates.at(-1) || ''
})

const customerSubscriptionEndDateText = computed(() => {
  return latestContractSubscriptionEndDate.value || customerData.value?.subscriptionEndDate || ''
})

const hasActiveContractSubscription = computed(() => {
  const latestEndDate = latestContractSubscriptionEndDate.value
  return latestEndDate ? !isSubscriptionDateExpired(latestEndDate) : false
})

const customerServiceStatusText = computed(() => {
  if (hasActiveContractSubscription.value) {
    const status = customerData.value?.serviceStatus || ''
    return status && status !== '已到期' ? status : '服务中'
  }
  const supportExpired = customerData.value?.supportExpired
  if (supportExpired === true || isSubscriptionDateExpired(customerSubscriptionEndDateText.value)) {
    return '已到期'
  }
  return customerData.value?.serviceStatus || ''
})

const getContractServiceShortName = (contract) => {
  const serviceType = String(contract?.serviceTypeName || '').trim()
  const amountUnit = String(contract?.amountUnit || '').trim()
  const text = `${serviceType} ${amountUnit}`
  if (text.includes('订阅')) return '订阅'
  if (text.includes('授权')) return '授权'
  return serviceType || amountUnit || '许可'
}

const isJumpServerContract = (contract) => {
  const productId = Number(contract?.productId)
  if (productId === 2001) {
    return true
  }
  const category = String(contract?.category || '').trim().toUpperCase()
  if (category === 'JS' || category === 'JUMPSERVER') {
    return true
  }
  return String(contract?.productServiceName || '').toUpperCase().includes('JUMPSERVER')
}

const isMaxKbContract = (contract) => {
  const productId = Number(contract?.productId)
  if ([2009, 2013].includes(productId)) {
    return true
  }
  const category = String(contract?.category || '').trim().toUpperCase()
  if (category === 'MK' || category === 'MAXKB') {
    return true
  }
  return String(contract?.productServiceName || '').toUpperCase().includes('MAXKB')
}

const isDataEaseContract = (contract) => {
  const productId = Number(contract?.productId)
  if ([2003, 2008].includes(productId)) {
    return true
  }
  const category = String(contract?.category || '').trim().toUpperCase()
  if (category === 'DE' || category === 'DATAEASE') {
    return true
  }
  return String(contract?.productServiceName || '').toUpperCase().includes('DATAEASE')
}

const formatContractServiceTag = (contract) => {
  return getContractServiceShortName(contract)
}

const formatContractAmountValue = (amount) => {
  if (amount === null || amount === undefined || amount === '') {
    return '-'
  }
  return String(amount)
}

const formatContractLicenseAmount = (contract) => {
  const productServiceName = String(contract?.productServiceName || '').trim()
  if (isMaxKbContract(contract)) {
    return '一套'
  }
  if (isDataEaseContract(contract)) {
    if (productServiceName.includes('专业版') || productServiceName.includes('嵌入式版')) {
      return '一套'
    }
    if (productServiceName.includes('企业版')) {
      return formatContractAmountValue(contract?.amount)
    }
    return Number(contract?.amount) > 1 ? formatContractAmountValue(contract?.amount) : '一套'
  }
  if (isJumpServerContract(contract)) {
    return formatContractAmountValue(contract?.amount)
  }
  return Number(contract?.amount) > 1 ? formatContractAmountValue(contract?.amount) : '一套'
}

const normalizeContractDate = (value) => {
  if (value === null || value === undefined || value === '') {
    return ''
  }
  const text = String(value).trim()
  if (!text) {
    return ''
  }
  if (/^\d+$/.test(text)) {
    const date = new Date(Number(text))
    return Number.isNaN(date.getTime()) ? '' : formatDateInputValue(date)
  }
  return text
}

const formatContractPeriod = (contract) => {
  const startDate = normalizeContractDate(contract?.startDate)
  const endDate = normalizeContractDate(contract?.supportEndDate || contract?.endDate)
  if (!startDate && !endDate) {
    return '-'
  }
  return `${startDate || '-'} 至 ${endDate || '-'}`
}

const getCustomerServiceStatusBadgeClass = (status) => {
  if (status === '已到期') return 'badge-danger'
  if (status === '服务中') return 'badge-success'
  if (status === '交付中') return 'badge-warning'
  return 'badge-neutral'
}

const showCustomerDataCompletionGuide = computed(() => {
  return chatId.value && !customerDataLoading.value && !customerData.value?.name
})

let realtimeAnalysisAbortController = null
let realtimeAnalysisTypingTimer = null
let realtimeAnalysisPendingContent = ''
let realtimeAnalysisStreamFinished = false
const realtimeAnalysisRangeOptions = [
  { label: '1h', value: '1' },
  { label: '3h', value: '3' },
  { label: '6h', value: '6' },
  { label: '1d', value: '24' },
  { label: '3d', value: '72' },
  { label: '7d', value: '168' }
]
const realtimeAnalysisRange = ref('24')
const realtimeAnalysisStatus = ref('idle')
const realtimeAnalysisRawContent = ref('')
const realtimeAnalysisParsed = ref(null)
const realtimeAnalysisMeta = ref({
  extChatId: '',
  groupName: '',
  timeRange: '24'
})
const realtimeAnalysisError = ref('')

const REALTIME_ANALYSIS_PRODUCT_LABELS = {
  JS: 'JumpServer',
  MK: 'MaxKB',
  DE: 'DataEase',
  SQLBOT: 'SQLBot'
}

const REALTIME_ANALYSIS_PRODUCT_ID_ALIAS_MAP = {
  2001: 'JS',
  2003: 'DE',
  2008: 'DE',
  2009: 'MK',
  2013: 'MK'
}

const escapeRealtimeRegex = (value) => String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

const decodeRealtimeJsonString = (value) => {
  const text = String(value || '').replace(/\\$/, '')
  try {
    return JSON.parse(`"${text}"`)
  } catch (error) {
    return text
      .replace(/\\"/g, '"')
      .replace(/\\n/g, '\n')
      .replace(/\\r/g, '\r')
      .replace(/\\t/g, '\t')
      .replace(/\\\\/g, '\\')
  }
}

const extractRealtimePartialString = (text, keys) => {
  const keyList = Array.isArray(keys) ? keys : [keys]
  for (const key of keyList) {
    const pattern = new RegExp(`"${escapeRealtimeRegex(key)}"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)`, 's')
    const match = pattern.exec(text)
    if (match?.[1]) {
      return decodeRealtimeJsonString(match[1]).trim()
    }
  }
  return ''
}

const extractRealtimeArrayBody = (text, key) => {
  const pattern = new RegExp(`"${escapeRealtimeRegex(key)}"\\s*:\\s*\\[`, 's')
  const match = pattern.exec(text)
  if (!match) return ''
  const start = match.index + match[0].length
  const end = text.indexOf(']', start)
  return text.slice(start, end === -1 ? undefined : end)
}

const extractRealtimePartialObjects = (text, key, fields) => {
  const body = extractRealtimeArrayBody(text, key)
  if (!body) return []
  const objects = []
  const objectPattern = /\{([\s\S]*?)(?=\}\s*,|\}\s*$|\}\s*\]|$)/g
  let match
  while ((match = objectPattern.exec(body)) !== null) {
    const source = match[1]
    const item = {}
    fields.forEach((field) => {
      const value = extractRealtimePartialString(source, field.keys || field.key)
      if (value) {
        item[field.key] = value
      }
    })
    if (Object.keys(item).length) {
      objects.push(item)
    }
  }
  return objects
}

const buildRealtimeAnalysisDraft = (value) => {
  const text = stripRealtimeJsonFence(value)
  if (!text) return null
  return {
    product: extractRealtimePartialString(text, 'product'),
    analysis: {
      problem_summary: extractRealtimePartialString(text, ['problem_summary', 'problemSummary']),
      problem_type: extractRealtimePartialString(text, ['problem_type', 'problemType']),
      urgency_level: extractRealtimePartialString(text, ['urgency_level', 'urgencyLevel']),
      customer_emotion: extractRealtimePartialString(text, ['customer_emotion', 'customerEmotion']),
      component: extractRealtimePartialString(text, 'component')
    },
    suggestions: {
      reply_options: extractRealtimePartialObjects(text, 'reply_options', [
        { key: 'approach', keys: ['approach', 'title'] },
        { key: 'content', keys: ['content', 'answer', 'text'] },
        { key: 'tone', keys: 'tone' }
      ])
    },
    knowledge_references: extractRealtimePartialObjects(text, 'knowledge_references', [
      { key: 'title', keys: ['title', 'label', 'name'] },
      { key: 'url', keys: ['url', 'link'] },
      { key: 'relevance', keys: ['relevance', 'description'] }
    ])
  }
}

const realtimeAnalysisDraft = computed(() => buildRealtimeAnalysisDraft(realtimeAnalysisRawContent.value))

const realtimeAnalysisDisplayData = computed(() => realtimeAnalysisParsed.value || realtimeAnalysisDraft.value || {})

const realtimeAnalysisSelectedRangeLabel = computed(() => {
  return realtimeAnalysisRangeOptions.find(option => option.value === realtimeAnalysisRange.value)?.label || '当前时间范围'
})

const realtimeAnalysisPlainContent = computed(() => {
  if (realtimeAnalysisParsed.value) return ''
  const text = stripRealtimeJsonFence(realtimeAnalysisRawContent.value)
  if (!text || text.startsWith('{') || text.startsWith('[')) return ''
  return text
})

const realtimeAnalysisNoConversation = computed(() => {
  return /无.*对话记录|暂无.*对话记录|没有.*对话记录/.test(realtimeAnalysisPlainContent.value)
})

const realtimeAnalysisProductAlias = computed(() => {
  const parsedProduct = String(realtimeAnalysisDisplayData.value?.product || '').trim().toUpperCase()
  if (parsedProduct) return parsedProduct
  if (implementationProductAlias.value) return implementationProductAlias.value
  const productId = Number(customerData.value?.productId || 0)
  if (productId && REALTIME_ANALYSIS_PRODUCT_ID_ALIAS_MAP[productId]) {
    return REALTIME_ANALYSIS_PRODUCT_ID_ALIAS_MAP[productId]
  }
  return ''
})

const realtimeAnalysisData = computed(() => {
  const data = realtimeAnalysisDisplayData.value?.analysis
  return data && typeof data === 'object' ? data : {}
})

const realtimeAnalysisSuggestions = computed(() => {
  const data = realtimeAnalysisDisplayData.value?.suggestions
  return data && typeof data === 'object' ? data : {}
})

const realtimeAnalysisProblemSummary = computed(() => {
  return realtimeAnalysisData.value.problem_summary
    || realtimeAnalysisData.value.problemSummary
    || (realtimeAnalysisNoConversation.value ? `${realtimeAnalysisSelectedRangeLabel.value}内暂无对话记录。` : realtimeAnalysisPlainContent.value)
    || (realtimeAnalysisStatus.value === 'streaming' ? '正在分析当前群聊内容...' : '点击“开始分析”获取当前群聊客户问题概览。')
})

const realtimeAnalysisOverviewTags = computed(() => {
  const productAlias = realtimeAnalysisProductAlias.value
  const productLabel = REALTIME_ANALYSIS_PRODUCT_LABELS[productAlias] || productAlias || '-'
  return [
    { label: '产品', value: productLabel },
    { label: '类型', value: realtimeAnalysisData.value.problem_type || realtimeAnalysisData.value.problemType || '-', type: 'type' },
    { label: '紧急度', value: realtimeAnalysisData.value.urgency_level || realtimeAnalysisData.value.urgencyLevel || '-', type: 'urgency' },
    { label: '情绪', value: realtimeAnalysisData.value.customer_emotion || realtimeAnalysisData.value.customerEmotion || '-', type: 'emotion' },
    { label: '组件', value: realtimeAnalysisData.value.component || '-', type: 'component' }
  ].filter(tag => tag.value && tag.value !== '-')
})

const normalizeRealtimeArray = (value) => {
  if (Array.isArray(value)) return value
  if (value === null || value === undefined || value === '') return []
  return [value]
}

const normalizeRealtimeTags = (value) => {
  return normalizeRealtimeArray(value)
    .flatMap(item => String(item || '').split(/[、,，/｜|]+/))
    .map(item => item.trim())
    .filter(Boolean)
}

const realtimeAnalysisReplyOptions = computed(() => {
  return normalizeRealtimeArray(realtimeAnalysisSuggestions.value.reply_options || realtimeAnalysisSuggestions.value.replyOptions)
    .map((item) => {
      if (item && typeof item === 'object') {
        return {
          approach: item.approach || item.title || '',
          toneTags: normalizeRealtimeTags(item.tone || item.tags || item.tag),
          content: item.content || item.answer || item.text || ''
        }
      }
      return {
        approach: '',
        toneTags: [],
        content: String(item || '')
      }
    })
    .filter(item => item.content)
})

const realtimeAnalysisReplyEmptyText = computed(() => {
  if (realtimeAnalysisPlainContent.value) {
    return realtimeAnalysisNoConversation.value ? '当前时间范围内暂无可生成的回复建议' : '未生成可直接复制的回复建议'
  }
  return realtimeAnalysisStatus.value === 'streaming' ? '正在生成回复建议...' : '暂无回复建议'
})

const realtimeAnalysisReferences = computed(() => {
  return normalizeRealtimeArray(realtimeAnalysisDisplayData.value?.knowledge_references || realtimeAnalysisDisplayData.value?.knowledgeReferences)
    .map((item) => {
      if (item && typeof item === 'object') {
        return {
          title: item.title || item.label || item.name || '',
          url: item.url || item.link || '',
          relevance: item.relevance || item.description || ''
        }
      }
      return {
        title: String(item || ''),
        url: '',
        relevance: ''
      }
    })
    .filter(item => item.title || item.url)
})

const realtimeAnalysisOtherFields = computed(() => {
  if (!realtimeAnalysisParsed.value || typeof realtimeAnalysisParsed.value !== 'object') return []
  const known = new Set(['product', 'analysis', 'suggestions', 'knowledge_references', 'knowledgeReferences'])
  return Object.entries(realtimeAnalysisParsed.value)
    .filter(([key]) => !known.has(key))
    .map(([key, value]) => ({
      key,
      value: typeof value === 'string' ? value : JSON.stringify(value, null, 2)
    }))
})

const stripRealtimeJsonFence = (value) => {
  let text = String(value || '').trim()
  text = text.replace(/^```(?:json)?\s*/i, '')
  text = text.replace(/\s*```$/i, '')
  return text.trim()
}

const parseRealtimeAnalysisContent = () => {
  const text = stripRealtimeJsonFence(realtimeAnalysisRawContent.value)
  if (!text) return
  try {
    realtimeAnalysisParsed.value = JSON.parse(text)
  } catch (error) {
    if (realtimeAnalysisStatus.value !== 'streaming') {
      console.warn('parse realtime analysis failed:', error)
    }
  }
}

const stopRealtimeAnalysisTyping = () => {
  if (realtimeAnalysisTypingTimer) {
    clearInterval(realtimeAnalysisTypingTimer)
    realtimeAnalysisTypingTimer = null
  }
  realtimeAnalysisPendingContent = ''
  realtimeAnalysisStreamFinished = false
}

const finishRealtimeAnalysisTypingIfReady = () => {
  if (realtimeAnalysisPendingContent || realtimeAnalysisTypingTimer) return
  if (realtimeAnalysisStreamFinished && realtimeAnalysisStatus.value === 'streaming') {
    realtimeAnalysisStatus.value = 'done'
    realtimeAnalysisStreamFinished = false
    parseRealtimeAnalysisContent()
  }
}

const flushRealtimeAnalysisTyping = () => {
  if (!realtimeAnalysisPendingContent) {
    if (realtimeAnalysisTypingTimer) {
      clearInterval(realtimeAnalysisTypingTimer)
      realtimeAnalysisTypingTimer = null
    }
    finishRealtimeAnalysisTypingIfReady()
    return
  }
  const batchSize = realtimeAnalysisPendingContent.length > 400 ? 4 : 2
  realtimeAnalysisRawContent.value += realtimeAnalysisPendingContent.slice(0, batchSize)
  realtimeAnalysisPendingContent = realtimeAnalysisPendingContent.slice(batchSize)
  parseRealtimeAnalysisContent()
}

const queueRealtimeAnalysisContent = (content) => {
  const text = String(content || '')
  if (!text) return
  realtimeAnalysisPendingContent += text
  if (!realtimeAnalysisTypingTimer) {
    realtimeAnalysisTypingTimer = setInterval(flushRealtimeAnalysisTyping, 14)
  }
}

const markRealtimeAnalysisStreamFinished = () => {
  realtimeAnalysisStreamFinished = true
  finishRealtimeAnalysisTypingIfReady()
}

const parseRealtimeSseBlock = (block) => {
  const event = {
    type: 'message',
    data: ''
  }
  let hasDataLine = false
  String(block || '').split(/\r?\n/).forEach((line) => {
    if (line.startsWith('event:')) {
      event.type = line.slice(6).trim() || 'message'
    } else if (line.startsWith('data:')) {
      hasDataLine = true
      event.data += line.slice(5).trim() + '\n'
    }
  })
  event.data = hasDataLine ? event.data.trim() : String(block || '').trim()
  return event
}

const parseRealtimeSsePayload = (data) => {
  if (!data) return null
  try {
    return JSON.parse(data)
  } catch (error) {
    return {
      code: 0,
      message: 'ok',
      data: {
        content: data
      }
    }
  }
}

const handleRealtimeSseEvent = (event) => {
  const payload = parseRealtimeSsePayload(event.data)
  if (!payload) return
  if (payload.error) {
    realtimeAnalysisStatus.value = 'error'
    realtimeAnalysisError.value = payload.error
    return
  }
  if (payload.code && payload.code !== 0) {
    realtimeAnalysisStatus.value = 'error'
    realtimeAnalysisError.value = payload.message || payload.error || '实时分析失败'
    return
  }
  const data = payload.data || {}
  if (event.type === 'message' && data.content) {
    queueRealtimeAnalysisContent(data.content)
    return
  }
  if (event.type === 'start') {
    realtimeAnalysisMeta.value = {
      extChatId: data.ext_chat_id || data.extChatId || realtimeAnalysisMeta.value.extChatId || '',
      groupName: data.group_name || data.groupName || realtimeAnalysisMeta.value.groupName || '',
      timeRange: data.time_range || data.timeRange || realtimeAnalysisRange.value
    }
    return
  }
  if (event.type === 'delta') {
    queueRealtimeAnalysisContent(data.content || '')
    return
  }
  if (event.type === 'done') {
    realtimeAnalysisMeta.value = {
      extChatId: data.ext_chat_id || data.extChatId || realtimeAnalysisMeta.value.extChatId || '',
      groupName: data.group_name || data.groupName || realtimeAnalysisMeta.value.groupName || '',
      timeRange: data.time_range || data.timeRange || realtimeAnalysisMeta.value.timeRange || realtimeAnalysisRange.value
    }
    markRealtimeAnalysisStreamFinished()
    return
  }
  if (event.type === 'error') {
    realtimeAnalysisStatus.value = 'error'
    realtimeAnalysisError.value = payload.message || data.message || '实时分析失败'
  }
}

const resetRealtimeAnalysisState = () => {
  stopRealtimeAnalysisTyping()
  realtimeAnalysisStatus.value = 'idle'
  realtimeAnalysisRawContent.value = ''
  realtimeAnalysisParsed.value = null
  realtimeAnalysisMeta.value = {
    extChatId: chatId.value || '',
    groupName: '',
    timeRange: realtimeAnalysisRange.value
  }
  realtimeAnalysisError.value = ''
}

const selectRealtimeAnalysisRange = (value) => {
  if (realtimeAnalysisStatus.value === 'streaming' || realtimeAnalysisRange.value === value) return
  realtimeAnalysisRange.value = value
  resetRealtimeAnalysisState()
}

const startRealtimeAnalysisStream = async () => {
  const targetChatId = chatId.value
  if (!targetChatId) {
    showToast('未获取到当前群聊 ID', false)
    return
  }
  if (realtimeAnalysisAbortController) {
    realtimeAnalysisAbortController.abort()
  }
  realtimeAnalysisAbortController = new AbortController()
  resetRealtimeAnalysisState()
  realtimeAnalysisStatus.value = 'streaming'

  try {
    const query = new URLSearchParams({
      extChatId: targetChatId,
      timeRange: realtimeAnalysisRange.value
    })
    const response = await fetch('/api/chat-group/realtime-analysis/stream?' + query.toString(), {
      headers: {
        Accept: 'text/event-stream'
      },
      signal: realtimeAnalysisAbortController.signal
    })
    if (!response.ok || !response.body) {
      throw new Error('实时分析请求失败: HTTP ' + response.status)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\n\n/)
      buffer = blocks.pop() || ''
      blocks.forEach((block) => {
        if (block.trim()) {
          handleRealtimeSseEvent(parseRealtimeSseBlock(block))
        }
      })
    }
    buffer += decoder.decode()
    if (buffer.trim()) {
      handleRealtimeSseEvent(parseRealtimeSseBlock(buffer))
    }
    if (realtimeAnalysisStatus.value === 'streaming') {
      markRealtimeAnalysisStreamFinished()
    }
  } catch (error) {
    if (error.name === 'AbortError') return
    realtimeAnalysisStatus.value = 'error'
    realtimeAnalysisError.value = error.message || '实时分析失败'
  } finally {
    if (realtimeAnalysisAbortController?.signal.aborted === false) {
      realtimeAnalysisAbortController = null
    }
  }
}

const legacyCopyText = (text) => {
  if (!text || typeof document === 'undefined') {
    return false
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', 'readonly')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '-9999px'
  textarea.style.opacity = '0'
  textarea.style.fontSize = '16px'
  const activeElement = document.activeElement
  const selection = window.getSelection ? window.getSelection() : null
  const selectedRange = selection && selection.rangeCount > 0 ? selection.getRangeAt(0) : null
  try {
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()
    textarea.setSelectionRange(0, textarea.value.length)
    return document.execCommand && document.execCommand('copy')
  } catch (error) {
    console.warn('legacy copy failed:', error)
    return false
  } finally {
    if (textarea.parentNode) {
      textarea.parentNode.removeChild(textarea)
    }
    if (selectedRange && selection) {
      selection.removeAllRanges()
      selection.addRange(selectedRange)
    }
    if (activeElement && typeof activeElement.focus === 'function') {
      activeElement.focus()
    }
  }
}

const copyTextToClipboard = async (text) => {
  const normalized = typeof text === 'string' ? text : String(text || '')
  if (!normalized) {
    return false
  }
  if (legacyCopyText(normalized)) {
    return true
  }
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(normalized)
      return true
    } catch (error) {
      console.warn('navigator clipboard copy failed:', error)
    }
  }
  return false
}

const copyRealtimeAnalysisText = async (text, successMessage = '内容已复制') => {
  const normalized = String(text || '').trim()
  if (!normalized) {
    showToast('当前没有可复制的内容', false)
    return
  }
  const copied = await copyTextToClipboard(normalized)
  showToast(copied ? successMessage : '复制失败，请稍后重试', copied)
}

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

const loadChatData = async (targetChatId) => {
  resetTicketViewState()
  resetTabLoadState()
  clearAcceptanceStatusFields()
  customerData.value = {}
  maintenanceRecords.value = []
  serviceRecords.value = []
  contractSubscriptions.value = []
  contractsLoading.value = false
  contractsLoadedChatId.value = ''
  contractsPreloadPromise.value = null
  contractsPreloadChatId.value = ''
  tickets.value = []
  issueTickets.value = []
  bugTickets.value = []
  implementationContext.value = null
  implementationContextLoadedChatId.value = ''
  implementationContextPreloadChatId.value = ''
  maintenanceCreateContext.value = null
  maintenanceContextLoadedChatId.value = ''
  maintenanceContextPreloadChatId.value = ''
  versionsLoading.value = false
  productVersions.value = []
  versionsLoadedProductId.value = null
  versionsLoadedChatId.value = ''
  versionsLoadedKey.value = ''
  versionPreloadPromise.value = null
  versionPreloadKey.value = ''
  downloadVersion.value = ''
  downloadVersionTouched.value = false
  downloadUrl.value = ''
  downloadUrlLoading.value = false
  implementationContextPreloadPromise.value = null
  maintenanceContextPreloadPromise.value = null
  toolCcEmails.value = DEFAULT_TOOL_MAIL_CC

  const runInBackground = (task) => {
    Promise.resolve(task).catch(() => {
      // 后台任务的错误各自处理，这里仅避免未捕获 Promise。
    })
  }

  const customerDataPromise = getCustomerData(targetChatId)
  const acceptanceStatusPromise = getAcceptanceStatus(targetChatId)
  const versionSourcesPromise = preloadVersionSources(targetChatId)
  const productVersionsPromise = customerDataPromise.then(() => prefetchProductVersions(targetChatId))
  const downloadVersionSyncPromise = Promise.allSettled([
    productVersionsPromise,
    versionSourcesPromise
  ]).then(() => syncDefaultDownloadVersion())
  const implementationContextPromise = prefetchImplementationCreateContext(targetChatId)
  const maintenanceContextPromise = prefetchMaintenanceCreateContext(targetChatId)

  runInBackground(implementationContextPromise.then(() => prefetchProductVersions(targetChatId)))
  runInBackground(maintenanceContextPromise)
  runInBackground(prefetchContractSubscriptions(targetChatId))
  runInBackground(loadStaffList({ silent: true }))
  const activeTabPromise = activeTab.value !== 'implementation' && activeTab.value !== 'maintenance'
    ? ensureActiveTabData(targetChatId)
    : Promise.resolve()

  await Promise.allSettled([
    customerDataPromise,
    acceptanceStatusPromise,
    versionSourcesPromise,
    productVersionsPromise,
    downloadVersionSyncPromise,
    activeTabPromise
  ])

  if (activeTab.value !== 'ticket') {
    runInBackground(loadTicketTabData(targetChatId))
  }
}

const loadDebugChatFallback = async (messagePrefix = '企业微信 chatID 获取失败') => {
  const debugChatId = (DEBUG_CHAT_ID || '').trim()
  if (!debugChatId) {
    throw new Error(messagePrefix + '，且未配置 DEBUG_CHAT_ID')
  }
  chatId.value = debugChatId
  showToast(`${messagePrefix}，已回退到调试 chatID`, false)
  await loadChatData(chatId.value)
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
          chatId.value = (result.chatId || '').trim()
          if (!chatId.value) {
            await loadDebugChatFallback('企业微信返回的 chatID 为空')
          } else {
            await loadChatData(chatId.value)
          }
        } catch (err) {
          showToast('异常：' + (err.message || err), false)
          console.error('异常:', err)
        } finally {
          loading.value = false
        }
      },
      fail(result) {
        // 失败回调，通过 result.errMsg 查看失败详情
        ;(async () => {
          try {
            await loadDebugChatFallback('获取群聊 chatID 失败！' + result.errMsg)
          } catch (err) {
            showToast('获取群聊 chatID 失败！' + result.errMsg, false)
            console.error('调用失败:', result)
          } finally {
            loading.value = false
          }
        })()
      }
    })
  } catch (err) {
    ;(async () => {
      try {
        await loadDebugChatFallback('获取群聊 chatID 异常')
      } catch (fallbackErr) {
        showToast('异常：' + (err.message || err), false)
        console.error('异常:', err)
      } finally {
        loading.value = false
      }
    })()
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

const handleAcceptanceStatusClick = () => {
  activeTab.value = 'tools'
}

const handleVersionClick = () => {
  activeTab.value = 'implementation'
}

const handleCustomerServiceStatusClick = () => {
  activeTab.value = 'contract'
}

const handleDownloadVersionChange = () => {
  downloadVersionTouched.value = true
  downloadUrl.value = ''
}

const handleGenerateDownloadUrl = async () => {
  if (!chatId.value) {
    showToast('未获取到当前群聊ID，无法获取下载链接', false)
    return
  }
  if (!downloadVersion.value) {
    showToast('请先选择版本', false)
    return
  }

  downloadUrlLoading.value = true
  try {
    const result = await docApi.getProductDownloadUrl(chatId.value, downloadVersion.value)
    if (result.success && result.data?.url) {
      downloadUrl.value = result.data.url
      showToast('下载链接已生成', true)
      return
    }
    showToast(result.message || '获取下载链接失败', false)
  } catch (error) {
    showToast('获取下载链接失败: ' + (error.message || error), false)
  } finally {
    downloadUrlLoading.value = false
  }
}

const copyDownloadUrl = async () => {
  if (!downloadUrl.value) {
    showToast('当前没有可复制的下载链接', false)
    return
  }
  const copied = await copyTextToClipboard(downloadUrl.value)
  showToast(copied ? '下载链接已复制' : '复制失败，请长按链接手动复制', copied)
}

const formatDateInputValue = (date = new Date()) => {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

const parseDateInputValue = (value) => {
  if (!value) return null
  const [year, month, day] = value.split('-').map(Number)
  if (!year || !month || !day) return null
  return new Date(year, month - 1, day)
}

const startOfCalendarMonth = (date = new Date()) => new Date(date.getFullYear(), date.getMonth(), 1)

const addCalendarMonths = (date, offset) => new Date(date.getFullYear(), date.getMonth() + offset, 1)

const isSameCalendarDay = (left, right) => (
  left &&
  right &&
  left.getFullYear() === right.getFullYear() &&
  left.getMonth() === right.getMonth() &&
  left.getDate() === right.getDate()
)

const maintenanceCalendarWeekdays = MAINTENANCE_CALENDAR_WEEKDAYS

const maintenanceCalendarTitle = computed(() => {
  const date = maintenanceCalendarCursor.value || new Date()
  return `${date.getFullYear()}年 ${date.getMonth() + 1}月`
})

const maintenanceCalendarDays = computed(() => {
  const cursor = startOfCalendarMonth(maintenanceCalendarCursor.value || new Date())
  const firstDay = cursor.getDay()
  const gridStart = new Date(cursor)
  gridStart.setDate(cursor.getDate() - firstDay)
  const selectedDate = parseDateInputValue(addMaintenanceForm.value.maintenanceTime)
  const today = new Date()
  return Array.from({ length: 42 }, (_, index) => {
    const current = new Date(gridStart)
    current.setDate(gridStart.getDate() + index)
    return {
      key: `${current.getFullYear()}-${current.getMonth()}-${current.getDate()}`,
      label: current.getDate(),
      value: formatDateInputValue(current),
      inCurrentMonth: current.getMonth() === cursor.getMonth(),
      isToday: isSameCalendarDay(current, today),
      isSelected: isSameCalendarDay(current, selectedDate)
    }
  })
})

const implementationCalendarTitle = computed(() => {
  const date = implementationCalendarCursor.value || new Date()
  return `${date.getFullYear()}年 ${date.getMonth() + 1}月`
})

const implementationCalendarDays = computed(() => {
  const cursor = startOfCalendarMonth(implementationCalendarCursor.value || new Date())
  const firstDay = cursor.getDay()
  const gridStart = new Date(cursor)
  gridStart.setDate(cursor.getDate() - firstDay)
  const selectedDate = parseDateInputValue(addImplementationForm.value.deploymentDate)
  const today = new Date()
  return Array.from({ length: 42 }, (_, index) => {
    const current = new Date(gridStart)
    current.setDate(gridStart.getDate() + index)
    return {
      key: `${current.getFullYear()}-${current.getMonth()}-${current.getDate()}`,
      label: current.getDate(),
      value: formatDateInputValue(current),
      inCurrentMonth: current.getMonth() === cursor.getMonth(),
      isToday: isSameCalendarDay(current, today),
      isSelected: isSameCalendarDay(current, selectedDate)
    }
  })
})

const syncMaintenanceCalendarCursor = (value = addMaintenanceForm.value.maintenanceTime) => {
  maintenanceCalendarCursor.value = startOfCalendarMonth(parseDateInputValue(value) || new Date())
}

const openMaintenanceCalendar = () => {
  syncMaintenanceCalendarCursor()
  showMaintenanceCalendar.value = true
}

const closeMaintenanceCalendar = () => {
  showMaintenanceCalendar.value = false
}

const toggleMaintenanceCalendar = () => {
  if (showMaintenanceCalendar.value) {
    closeMaintenanceCalendar()
  } else {
    openMaintenanceCalendar()
  }
}

const changeMaintenanceCalendarMonth = (offset) => {
  maintenanceCalendarCursor.value = startOfCalendarMonth(addCalendarMonths(maintenanceCalendarCursor.value || new Date(), offset))
}

const selectMaintenanceCalendarDate = (value) => {
  addMaintenanceForm.value.maintenanceTime = value
  closeMaintenanceCalendar()
}

const selectMaintenanceCalendarToday = () => {
  selectMaintenanceCalendarDate(formatDateInputValue(new Date()))
}

const syncImplementationCalendarCursor = (value = addImplementationForm.value.deploymentDate) => {
  implementationCalendarCursor.value = startOfCalendarMonth(parseDateInputValue(value) || new Date())
}

const openImplementationCalendar = () => {
  syncImplementationCalendarCursor()
  showImplementationCalendar.value = true
}

const closeImplementationCalendar = () => {
  showImplementationCalendar.value = false
}

const toggleImplementationCalendar = () => {
  if (showImplementationCalendar.value) {
    closeImplementationCalendar()
  } else {
    openImplementationCalendar()
  }
}

const changeImplementationCalendarMonth = (offset) => {
  implementationCalendarCursor.value = startOfCalendarMonth(addCalendarMonths(implementationCalendarCursor.value || new Date(), offset))
}

const selectImplementationCalendarDate = (value) => {
  addImplementationForm.value.deploymentDate = value
  closeImplementationCalendar()
}

const selectImplementationCalendarToday = () => {
  selectImplementationCalendarDate(formatDateInputValue(new Date()))
}

const getCurrentProductId = () => {
  if (showAddImplementationModal.value && isImplementationDraftMode.value && addImplementationForm.value.selectedProductId) {
    return addImplementationForm.value.selectedProductId
  }
  return customerData.value?.productId || null
}

const isSameProductId = (left, right) => {
  if (!left || !right) {
    return false
  }
  return String(left) === String(right)
}

const getProductVersionsCacheKey = (targetChatId = chatId.value, productId = getCurrentProductId()) => {
  if (!targetChatId || !productId) {
    return ''
  }
  return `${targetChatId}::${productId}`
}

const getEditorUserId = () => {
  if (LOCAL_DEBUG_CHAT) {
    return DEBUG_EDITOR_USER_ID
  }
  return userStore.userInfo?.userid || userStore.userInfo?.UserId || userStore.userInfo?.user_id || ''
}

const getEditorDisplayName = () => {
  const editorUserId = getEditorUserId()
  const candidates = [
    userStore.userInfo?.name,
    userStore.userInfo?.username,
    userStore.userInfo?.nickname
  ]
  for (const candidate of candidates) {
    const normalized = typeof candidate === 'string' ? candidate.trim() : ''
    if (normalized && normalized !== editorUserId) {
      return normalized
    }
  }
  return ''
}

const resolveDefaultTicketOwnerName = (ticket = null) => {
  const editorName = getEditorDisplayName()
  if (editorName) {
    return editorName
  }
  return typeof ticket?.ownerName === 'string' ? ticket.ownerName.trim() : ''
}

const loadProductVersions = async ({ silent = false, force = false, targetChatId = chatId.value } = {}) => {
  if (!isCurrentChatTarget(targetChatId)) {
    return
  }
  const productId = getCurrentProductId()
  if (!productId) {
    productVersions.value = []
    versionsLoadedProductId.value = null
    versionsLoadedChatId.value = ''
    versionsLoadedKey.value = ''
    if (!silent) {
      showToast('未识别到当前客户产品，无法加载版本列表', false)
    }
    return
  }
  const cacheKey = getProductVersionsCacheKey(targetChatId, productId)
  if (!force && versionsLoadedKey.value === cacheKey) {
    return
  }

  versionsLoading.value = true
  try {
    const result = await Promise.race([
      docApi.getProductVersions(productId, targetChatId),
      new Promise((_, reject) => setTimeout(() => reject(new Error('获取版本列表超时')), 12000))
    ])
    if (!isCurrentChatTarget(targetChatId)) {
      return
    }
    if (result.success) {
      const rawVersions = Array.isArray(result.data) ? result.data : (result.data?.items || [])
      const resolvedProductId = result.data?.productId
      if (!isImplementationDraftMode.value && !customerData.value?.productId && resolvedProductId) {
        customerData.value = {
          ...(customerData.value || {}),
          productId: resolvedProductId
        }
      }
      const loadedProductId = resolvedProductId || productId
      if (!isSameProductId(getCurrentProductId(), loadedProductId)) {
        productVersions.value = []
        versionsLoadedProductId.value = null
        versionsLoadedChatId.value = ''
        versionsLoadedKey.value = ''
        return
      }
      productVersions.value = rawVersions.map(item => {
        if (typeof item === 'string') return item
        return item?.version || item?.name || ''
      }).filter(Boolean)
      versionsLoadedProductId.value = loadedProductId
      versionsLoadedChatId.value = targetChatId
      versionsLoadedKey.value = getProductVersionsCacheKey(targetChatId, loadedProductId)
      syncDefaultDownloadVersion()
      return
    }
    productVersions.value = []
    versionsLoadedProductId.value = null
    versionsLoadedChatId.value = ''
    versionsLoadedKey.value = ''
    if (!silent) {
      showToast(result.message || '获取版本列表失败', false)
    }
  } catch (error) {
    if (!isCurrentChatTarget(targetChatId)) {
      return
    }
    productVersions.value = []
    versionsLoadedProductId.value = null
    versionsLoadedChatId.value = ''
    versionsLoadedKey.value = ''
    if (!silent) {
      showToast('获取版本列表失败: ' + (error.message || error), false)
    }
  } finally {
    if (isCurrentChatTarget(targetChatId)) {
      versionsLoading.value = false
    }
  }
}

const getDefaultDownloadVersion = (versions) => {
  if (!Array.isArray(versions) || versions.length === 0) {
    return ''
  }
  const semanticVersions = versions.filter(isSemanticProductVersion)
  const scopedVersions = semanticVersions.filter(version => matchesDownloadProductMajor(version))
  const majorPreferredVersions = scopedVersions.length > 0 ? scopedVersions : semanticVersions
  const architecture = inferDefaultDownloadArchitecture()
  const architectureMatched = majorPreferredVersions.filter(version => matchesDownloadArchitecture(version, architecture))
  const candidates = architectureMatched.length > 0 ? architectureMatched : majorPreferredVersions
  return candidates[0] || semanticVersions[0] || versions[0]
}

const syncDefaultDownloadVersion = () => {
  if (!isCurrentChatTarget(versionsLoadedChatId.value)) {
    return
  }
  if (downloadVersionTouched.value || productVersions.value.length === 0) {
    return
  }
  downloadVersion.value = getDefaultDownloadVersion(productVersions.value)
}

const isSemanticProductVersion = (version) => /v?\d+\.\d+\.\d+/i.test(String(version || '').trim())

const getDownloadProductAlias = () => {
  const productId = Number(customerData.value?.productId || 0)
  if (productId && REALTIME_ANALYSIS_PRODUCT_ID_ALIAS_MAP[productId]) {
    return REALTIME_ANALYSIS_PRODUCT_ID_ALIAS_MAP[productId]
  }
  return realtimeAnalysisProductAlias.value || ''
}

const matchesDownloadProductMajor = (version) => {
  const alias = getDownloadProductAlias()
  const major = getProductVersionMajor(version)
  if (alias === 'MK') {
    return major === 2
  }
  if (alias === 'JS') {
    return major === 4
  }
  return true
}

const getProductVersionMajor = (version) => {
  const match = String(version || '').match(/v?(\d+)\.\d+\.\d+/i)
  return match ? Number(match[1]) : Number.NaN
}

const inferDefaultDownloadArchitecture = () => {
  const text = String(versionBadgeText.value || '').trim().toLowerCase()
  if (!text || text === '请补充实施') {
    return 'x86'
  }
  if (text.includes('arm')) {
    return 'arm'
  }
  return 'x86'
}

const matchesDownloadArchitecture = (version, architecture) => {
  const text = String(version || '').trim().toLowerCase()
  const isArm = text.includes('arm') || text.includes('aarch64')
  const isX86 = text.includes('x86') || text.includes('amd64')
  if (architecture === 'arm') {
    return isArm
  }
  return isX86 || !isArm
}

const prefetchProductVersions = (targetChatId = chatId.value) => {
  if (!isCurrentChatTarget(targetChatId)) {
    return Promise.resolve()
  }
  const productId = getCurrentProductId()
  const cacheKey = getProductVersionsCacheKey(targetChatId, productId)
  if (!cacheKey) {
    return Promise.resolve()
  }
  if (versionsLoadedKey.value === cacheKey) {
    return Promise.resolve()
  }
  if (versionPreloadPromise.value && versionPreloadKey.value === cacheKey) {
    return versionPreloadPromise.value
  }
  versionPreloadKey.value = cacheKey
  const preloadPromise = (async () => {
    await loadProductVersions({ silent: true, force: false, targetChatId })
    if (versionsLoadedKey.value !== cacheKey && isCurrentChatTarget(targetChatId) && getCurrentProductId()) {
      await new Promise(resolve => setTimeout(resolve, 1200))
      await loadProductVersions({ silent: true, force: true, targetChatId })
    }
  })().finally(() => {
    if (versionPreloadPromise.value === preloadPromise) {
      versionPreloadPromise.value = null
      versionPreloadKey.value = ''
    }
  })
  versionPreloadPromise.value = preloadPromise
  return versionPreloadPromise.value
}

const prefetchMaintenanceCreateContext = (targetChatId = chatId.value) => {
  if (!targetChatId) {
    return Promise.resolve(null)
  }
  if (maintenanceContextLoadedChatId.value === targetChatId && maintenanceCreateContext.value) {
    return Promise.resolve(maintenanceCreateContext.value)
  }
  if (maintenanceContextPreloadPromise.value && maintenanceContextPreloadChatId.value === targetChatId) {
    return maintenanceContextPreloadPromise.value
  }
  maintenanceContextPreloadChatId.value = targetChatId
  const preloadPromise = (async () => {
    const result = await docApi.getMaintenanceCreateContext(targetChatId)
    if (!(result.success || result.code === 0)) {
      throw new Error(result.message || result.msg || '获取新增维护上下文失败')
    }
    if (!isCurrentChatTarget(targetChatId)) {
      return null
    }
    const context = result.data || {}
    maintenanceCreateContext.value = context
    maintenanceContextLoadedChatId.value = targetChatId
    return context
  })().catch((error) => {
    if (isCurrentChatTarget(targetChatId)) {
      maintenanceCreateContext.value = null
      maintenanceContextLoadedChatId.value = ''
    }
    throw error
  }).finally(() => {
    if (maintenanceContextPreloadPromise.value === preloadPromise) {
      maintenanceContextPreloadPromise.value = null
      maintenanceContextPreloadChatId.value = ''
    }
  })
  maintenanceContextPreloadPromise.value = preloadPromise
  return maintenanceContextPreloadPromise.value
}

const prefetchImplementationCreateContext = (targetChatId = chatId.value) => {
  if (!targetChatId) {
    return Promise.resolve(null)
  }
  if (implementationContextLoadedChatId.value === targetChatId && implementationContext.value) {
    return Promise.resolve(implementationContext.value)
  }
  if (implementationContextPreloadPromise.value && implementationContextPreloadChatId.value === targetChatId) {
    return implementationContextPreloadPromise.value
  }
  implementationContextPreloadChatId.value = targetChatId
  const preloadPromise = (async () => {
    const result = await docApi.getImplementationCreateContext(targetChatId)
    if (!(result.success || result.code === 0)) {
      throw new Error(result.message || result.msg || '获取新增实施上下文失败')
    }
    if (!isCurrentChatTarget(targetChatId)) {
      return null
    }
    const context = result.data || {}
    implementationContext.value = context
    if (!customerData.value?.productId && context.productId) {
      customerData.value = {
        ...(customerData.value || {}),
        productId: context.productId
      }
      prefetchProductVersions(targetChatId).catch(() => {})
    }
    implementationContextLoadedChatId.value = targetChatId
    return context
  })().catch((error) => {
    if (isCurrentChatTarget(targetChatId)) {
      implementationContext.value = null
      implementationContextLoadedChatId.value = ''
    }
    throw error
  }).finally(() => {
    if (implementationContextPreloadPromise.value === preloadPromise) {
      implementationContextPreloadPromise.value = null
      implementationContextPreloadChatId.value = ''
    }
  })
  implementationContextPreloadPromise.value = preloadPromise
  return implementationContextPreloadPromise.value
}

const resetAddMaintenanceForm = (context = null) => {
  const defaultSubmitterName = (context?.defaultSubmitterName || '').trim()
  const submitterName = defaultSubmitterName && implementationStaffList.value.includes(defaultSubmitterName) ? defaultSubmitterName : ''
  addMaintenanceForm.value = {
    maintenanceTime: formatDateInputValue(),
    maintenanceTypes: '',
    submitterName,
    maintenanceTitle: '',
    maintenanceVersion: '',
    maintenanceContext: ''
  }
  syncMaintenanceCalendarCursor(addMaintenanceForm.value.maintenanceTime)
}

const closeAddMaintenanceModal = () => {
  showAddMaintenanceModal.value = false
  addMaintenanceSubmitting.value = false
  showMaintenanceSubmitterDropdown.value = false
  closeMaintenanceCalendar()
}

const resetAddImplementationForm = (context = null) => {
  const submitterUserId = context?.defaultSubmitterUserId || getEditorUserId()
  const defaultSubmitterName = (context?.defaultSubmitterName || '').trim()
  const submitterName = defaultSubmitterName && implementationStaffList.value.includes(defaultSubmitterName) ? defaultSubmitterName : ''
  const productDefaults = getDefaultImplementationProductFields(context?.formType || '')
  addImplementationForm.value = {
    selectedProductId: '',
    template: context?.template || '',
    formType: context?.formType || '',
    productAlias: context?.productAlias || '',
    deploymentDate: formatDateInputValue(),
    deploymentMethod: '远程部署',
    version: '',
    ...productDefaults,
    remainingIssues: '',
    remark: '',
    submitterUserId,
    submitterName
  }
  syncImplementationCalendarCursor(addImplementationForm.value.deploymentDate)
  nextTick(() => {
    doAdjustImplementationTextareas()
  })
}

const closeAddImplementationModal = () => {
  showAddImplementationModal.value = false
  implementationContextLoading.value = false
  addImplementationSubmitting.value = false
  showImplementationSubmitterDropdown.value = false
  closeImplementationCalendar()
}

const toggleImplementationAssetType = (option) => {
  const current = new Set(addImplementationForm.value.assetTypes || [])
  if (current.has(option)) {
    current.delete(option)
  } else {
    current.add(option)
  }
  addImplementationForm.value.assetTypes = Array.from(current)
}

const toggleImplementationAuthMethod = (option) => {
  const current = new Set(addImplementationForm.value.authMethods || [])
  if (option === '无') {
    addImplementationForm.value.authMethods = current.has(option) ? [] : [option]
    return
  }
  current.delete('无')
  if (current.has(option)) {
    current.delete(option)
  } else {
    current.add(option)
  }
  addImplementationForm.value.authMethods = Array.from(current)
}

const toggleImplementationBusinessDirection = (option) => {
  const current = new Set(addImplementationForm.value.businessDirections || [])
  if (current.has(option)) {
    current.delete(option)
  } else {
    current.add(option)
  }
  addImplementationForm.value.businessDirections = Array.from(current)
}

const applySelectedImplementationProduct = () => {
  const option = selectedImplementationProductOption.value
  addImplementationForm.value.template = option?.template || ''
  addImplementationForm.value.formType = option?.formType || ''
  addImplementationForm.value.productAlias = option?.productAlias || ''
  Object.assign(addImplementationForm.value, getDefaultImplementationProductFields(option?.formType || ''))
  nextTick(() => {
    doAdjustImplementationTextareas()
  })
}

const resetImplementationVersionState = () => {
  productVersions.value = []
  versionsLoadedProductId.value = null
  versionsLoadedChatId.value = ''
  versionsLoadedKey.value = ''
  versionPreloadPromise.value = null
  versionPreloadKey.value = ''
  addImplementationForm.value.version = ''
}

const handleImplementationProductChange = async () => {
  applySelectedImplementationProduct()
  resetImplementationVersionState()
  if (!addImplementationForm.value.selectedProductId) {
    return
  }
  await loadProductVersions({ silent: false, force: true, targetChatId: chatId.value })
}

const handleAddImplementation = async () => {
  if (!chatId.value) {
    showToast('未获取到当前群聊ID，无法新增实施记录', false)
    return
  }
  implementationContextLoading.value = true
  showAddImplementationModal.value = true
  resetAddImplementationForm()
  try {
    const targetChatId = chatId.value
    const [context] = await Promise.all([
      prefetchImplementationCreateContext(targetChatId),
      prefetchProductVersions(targetChatId),
      loadImplementationStaffList()
    ])
    await prefetchProductVersions(targetChatId)
    resetAddImplementationForm(context || {})
    nextTick(() => {
      doAdjustImplementationTextareas()
    })
  } catch (error) {
    closeAddImplementationModal()
    showToast('获取新增实施上下文失败: ' + (error.message || error), false)
  } finally {
    implementationContextLoading.value = false
  }
}

const handleAddMaintenance = async () => {
  if (!chatId.value) {
    showToast('未获取到当前群聊ID，无法新增维护记录', false)
    return
  }
  resetAddMaintenanceForm()
  showAddMaintenanceModal.value = true
  try {
    const targetChatId = chatId.value
    const [, context] = await Promise.all([
      prefetchProductVersions(targetChatId),
      prefetchMaintenanceCreateContext(targetChatId),
      loadImplementationStaffList()
    ])
    resetAddMaintenanceForm(context || {})
  } catch (error) {
    showToast('获取新增维护上下文失败: ' + (error.message || error), false)
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
  const submitterName = (addMaintenanceForm.value.submitterName || '').trim()
  if (!submitterName || !implementationStaffList.value.includes(submitterName)) {
    showToast('请选择提交人', false)
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
      submitterName,
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
        creatorName: payload.submitterName || '-',
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

const resolveImplementationSubmitErrorMessage = (errorLike) => {
  const message = String(
    errorLike?.message ||
    errorLike?.errmsg ||
    errorLike?.msg ||
    errorLike ||
    ''
  )
  const normalized = message.toLowerCase()
  if (
    normalized.includes('already exists') ||
    normalized.includes('status":409') ||
    normalized.includes('conflict') ||
    normalized.includes('maintenance of subscription id')
  ) {
    return '实施已存在'
  }
  return message || '新增实施记录失败'
}

const submitAddImplementation = async () => {
  const draftMode = isImplementationDraftMode.value
  const selectedProductOption = selectedImplementationProductOption.value
  if (!draftMode && !implementationContext.value?.subscriptionId) {
    showToast('缺少订阅信息，无法提交实施记录', false)
    return
  }
  if (!draftMode && !implementationContext.value?.clientId) {
    showToast('缺少客户信息，无法提交实施记录', false)
    return
  }
  const selectedProductId = selectedProductOption?.productId || implementationContext.value?.productId
  if (!draftMode && !selectedProductId) {
    showToast('缺少产品信息，无法提交实施记录', false)
    return
  }
  const productAlias = implementationFormType.value || ''
  const missingFields = []
  if (draftMode && !selectedProductOption) missingFields.push('产品')
  const submitterName = (addImplementationForm.value.submitterName || '').trim()
  if (!submitterName || !implementationStaffList.value.includes(submitterName)) missingFields.push('实施人')
  if (!addImplementationForm.value.deploymentDate) missingFields.push('部署日期')
  if (!addImplementationForm.value.deploymentMethod) missingFields.push('部署方式')
  if (!addImplementationForm.value.version) missingFields.push('软件版本')
  if (productAlias === 'JS') {
    if (!(addImplementationForm.value.assetTypes || []).length) missingFields.push('纳管资产类型')
    if (!addImplementationForm.value.assetCount) missingFields.push('管理资产数')
    if (!addImplementationForm.value.virtualizationType) missingFields.push('虚拟化类型')
    if (!addImplementationForm.value.applicationServer) missingFields.push('应用发布服务器')
    if (!addImplementationForm.value.databaseSync) missingFields.push('是否涉及到数据同步')
    if (!addImplementationForm.value.databaseExternal) missingFields.push('数据库是否外置')
    if (!addImplementationForm.value.redisExternal) missingFields.push('Redis是否外置部署')
    if (!addImplementationForm.value.sharedNfs) missingFields.push('共享存储NFS')
    if (!addImplementationForm.value.deploymentArchitecture) missingFields.push('部署架构')
    if (!addImplementationForm.value.deploymentRecord) missingFields.push('记录内容')
  } else if (productAlias === 'MK') {
    if (!addImplementationForm.value.deploymentArchitecture) missingFields.push('部署架构')
    if (!(addImplementationForm.value.authMethods || []).length) missingFields.push('认证方式')
    if (!(addImplementationForm.value.businessDirections || []).length) missingFields.push('业务方向')
  } else if (productAlias === 'DE') {
    if (!addImplementationForm.value.backupMethod) missingFields.push('备份方式')
    if (!addImplementationForm.value.dataEaseDatabase) missingFields.push('数据库配置')
    if (!addImplementationForm.value.dorisUsage) missingFields.push('Doris配置')
    if (!addImplementationForm.value.deploymentArchitecture) missingFields.push('部署架构')
    if (!addImplementationForm.value.dataSourceType) missingFields.push('数据源类型')
    if (!addImplementationForm.value.dataScale) missingFields.push('数据量规模')
    if (!(addImplementationForm.value.authMethods || []).length) missingFields.push('认证方式')
    if (!addImplementationForm.value.embeddedMode) missingFields.push('嵌入方式')
    if (!addImplementationForm.value.customerJoined) missingFields.push('客户接入状态')
    if (!addImplementationForm.value.analysisDirection) missingFields.push('分析及展示方向')
    if (!addImplementationForm.value.customerFocus) missingFields.push('客户核心关注点')
  } else if (productAlias === 'SQLBOT') {
    if (!addImplementationForm.value.backupMethod) missingFields.push('备份方式')
    if (!addImplementationForm.value.databaseExternal) missingFields.push('PostgreSQL 是否外置')
    if (!addImplementationForm.value.deploymentArchitecture) missingFields.push('部署架构')
    if (!addImplementationForm.value.dataSourceType) missingFields.push('数据源类型')
    if (!addImplementationForm.value.aiModelType) missingFields.push('AI模型类型')
    if (!(addImplementationForm.value.authMethods || []).length) missingFields.push('第三方平台对接')
    if (!addImplementationForm.value.embeddedMode) missingFields.push('是否嵌入集成')
  }

  if (missingFields.length > 0) {
    showToast(`请完整填写必填项：${missingFields.join('、')}`, false)
    return
  }

  addImplementationSubmitting.value = true
  try {
    const resolvedEditorUserId = addImplementationForm.value.submitterUserId || getEditorUserId()
    if (!submitterName && !resolvedEditorUserId) {
      showToast('缺少提交人ID，请先登录或开启本地调试提交人兜底', false)
      return
    }
    const payload = {
      extChatId: chatId.value,
      subscriptionId: draftMode ? 0 : implementationContext.value.subscriptionId,
      clientId: draftMode ? null : implementationContext.value.clientId,
      productId: selectedProductId,
      selectedProductId: selectedProductOption?.productId || null,
      template: selectedProductOption?.template || addImplementationForm.value.template || implementationContext.value?.template || '',
      formType: selectedProductOption?.formType || addImplementationForm.value.formType || implementationContext.value?.formType || productAlias,
      regionId: implementationContext.value?.regionId || null,
      editorUserId: resolvedEditorUserId,
      submitterName,
      deploymentDate: addImplementationForm.value.deploymentDate,
      deploymentMethod: addImplementationForm.value.deploymentMethod,
      version: addImplementationForm.value.version,
      assetTypes: addImplementationForm.value.assetTypes,
      assetCount: addImplementationForm.value.assetCount,
      virtualizationType: addImplementationForm.value.virtualizationType,
      applicationServer: addImplementationForm.value.applicationServer,
      databaseSync: addImplementationForm.value.databaseSync,
      databaseExternal: addImplementationForm.value.databaseExternal,
      redisExternal: addImplementationForm.value.redisExternal,
      sharedNfs: addImplementationForm.value.sharedNfs,
      authMethods: addImplementationForm.value.authMethods,
      businessDirections: addImplementationForm.value.businessDirections,
      backupMethod: addImplementationForm.value.backupMethod.trim(),
      dataEaseDatabase: addImplementationForm.value.dataEaseDatabase.trim(),
      dorisUsage: addImplementationForm.value.dorisUsage.trim(),
      dataSourceType: addImplementationForm.value.dataSourceType.trim(),
      aiModelType: addImplementationForm.value.aiModelType.trim(),
      dataScale: addImplementationForm.value.dataScale.trim(),
      embeddedMode: addImplementationForm.value.embeddedMode.trim(),
      customerJoined: addImplementationForm.value.customerJoined.trim(),
      analysisDirection: addImplementationForm.value.analysisDirection.trim(),
      customerFocus: addImplementationForm.value.customerFocus.trim(),
      deploymentArchitecture: addImplementationForm.value.deploymentArchitecture,
      deploymentRecord: addImplementationForm.value.deploymentRecord.trim(),
      remainingIssues: addImplementationForm.value.remainingIssues.trim(),
      remark: addImplementationForm.value.remark.trim()
    }
    const result = await docApi.createImplementationRecord(payload)
    if (result.success || result.code === 0) {
      const createdRecord = result.data?.record
      closeAddImplementationModal()
      if (chatId.value) {
        const refreshedRecords = await getMaintenanceRecords(chatId.value)
        if (createdRecord && !refreshedRecords.some(record => String(record?.id) === String(createdRecord.id))) {
          mergeCreatedImplementationRecord(createdRecord)
        }
      }
      showToast('新增实施记录成功', true)
      return
    }
    showToast(resolveImplementationSubmitErrorMessage(result), false)
  } catch (error) {
    showToast(resolveImplementationSubmitErrorMessage(error), false)
  } finally {
    addImplementationSubmitting.value = false
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
  updateForm.value.ownerName = resolveDefaultTicketOwnerName(ticket)
  showUpdateModal.value = true
}

const loadStaffList = async ({ silent = false } = {}) => {
  if (staffList.value.length > 0) {
    return true
  }
  if (staffListPreloadPromise.value) {
    const loaded = await staffListPreloadPromise.value
    if (!loaded && !silent) {
      showToast('加载员工列表失败', false)
    }
    return loaded
  }

  const preloadPromise = (async () => {
    try {
      const result = await docApi.getStaffList()
      // console.log('员工列表响应:', result)
      if (result.success) {
        staffList.value = result.data || []
        // console.log('员工列表加载成功，共', staffList.value.length, '人')
        return true
      }
      console.error('加载员工列表失败:', result.message)
      if (!silent) {
        showToast('加载员工列表失败: ' + result.message, false)
      }
      return false
    } catch (error) {
      console.error('加载员工列表失败:', error)
      if (!silent) {
        showToast('加载员工列表失败', false)
      }
      return false
    }
  })().finally(() => {
    if (staffListPreloadPromise.value === preloadPromise) {
      staffListPreloadPromise.value = null
    }
  })

  staffListPreloadPromise.value = preloadPromise
  return staffListPreloadPromise.value
}

const loadImplementationStaffList = async ({ silent = false } = {}) => {
  if (implementationStaffListLoaded.value) {
    return true
  }
  if (implementationStaffListPreloadPromise.value) {
    const loaded = await implementationStaffListPreloadPromise.value
    if (!loaded && !silent) {
      showToast('加载实施人列表失败', false)
    }
    return loaded
  }

  const preloadPromise = (async () => {
    try {
      const result = await docApi.getImplementationStaffList()
      if (result.success) {
        implementationStaffList.value = result.data || []
        implementationStaffListLoaded.value = true
        return true
      }
      console.error('加载实施人列表失败:', result.message)
      if (!silent) {
        showToast('加载实施人列表失败: ' + result.message, false)
      }
      return false
    } catch (error) {
      console.error('加载实施人列表失败:', error)
      if (!silent) {
        showToast('加载实施人列表失败', false)
      }
      return false
    }
  })().finally(() => {
    if (implementationStaffListPreloadPromise.value === preloadPromise) {
      implementationStaffListPreloadPromise.value = null
    }
  })

  implementationStaffListPreloadPromise.value = preloadPromise
  return implementationStaffListPreloadPromise.value
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

const filteredMaintenanceSubmitterList = computed(() => {
  if (!addMaintenanceForm.value.submitterName) {
    return implementationStaffList.value
  }
  return implementationStaffList.value.filter(staff =>
    staff.toLowerCase().includes(addMaintenanceForm.value.submitterName.toLowerCase())
  )
})

const filteredImplementationSubmitterList = computed(() => {
  if (!addImplementationForm.value.submitterName) {
    return implementationStaffList.value
  }
  return implementationStaffList.value.filter(staff =>
    staff.toLowerCase().includes(addImplementationForm.value.submitterName.toLowerCase())
  )
})

// 处理输入事件
const handleOwnerInput = () => {
  showStaffDropdown.value = true
}

const handleMaintenanceSubmitterInput = () => {
  showMaintenanceSubmitterDropdown.value = true
}

const handleImplementationSubmitterInput = () => {
  showImplementationSubmitterDropdown.value = true
}

// 处理失焦事件
const handleOwnerBlur = () => {
  setTimeout(() => {
    showStaffDropdown.value = false
  }, 200)
}

const handleMaintenanceSubmitterBlur = () => {
  setTimeout(() => {
    showMaintenanceSubmitterDropdown.value = false
  }, 200)
}

const handleImplementationSubmitterBlur = () => {
  setTimeout(() => {
    showImplementationSubmitterDropdown.value = false
  }, 200)
}

// 选择员工
const selectStaff = (staff) => {
  updateForm.value.ownerName = staff
  showStaffDropdown.value = false
}

const selectMaintenanceSubmitter = (staff) => {
  addMaintenanceForm.value.submitterName = staff
  showMaintenanceSubmitterDropdown.value = false
}

const selectImplementationSubmitter = (staff) => {
  addImplementationForm.value.submitterName = staff
  showImplementationSubmitterDropdown.value = false
}

// 切换下拉框显示
const toggleStaffDropdown = () => {
  showStaffDropdown.value = !showStaffDropdown.value
}

const toggleMaintenanceSubmitterDropdown = () => {
  showMaintenanceSubmitterDropdown.value = !showMaintenanceSubmitterDropdown.value
}

const toggleImplementationSubmitterDropdown = () => {
  showImplementationSubmitterDropdown.value = !showImplementationSubmitterDropdown.value
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
      ...updateForm.value,
      reminderCycle: 2
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

    const ticketIdToUpdate = currentTicketId.value
    let result
    if (activeTab.value === 'requirement') {
      result = await docApi.updateIssueTicket(ticketIdToUpdate, requestData)
    } else if (activeTab.value === 'defect') {
      result = await docApi.updateBugTicket(ticketIdToUpdate, requestData)
    } else {
      result = await docApi.updateTicket(ticketIdToUpdate, requestData)
    }

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
      await getTicketLogs(ticketIdToUpdate)
    } else {
      showToast(result.message || '工单更新失败', false)
    }
  } catch (error) {
    console.error('更新工单失败:', error)
    showToast('工单更新失败: ' + error.message, false)
  }
}

onMounted(async () => {
  if (LOCAL_DEBUG_CHAT) {
    try {
      await applyLocalDebugLogin()
    } catch (error) {
      showToast('本地调试登录失败: ' + (error.message || error), false)
      console.error('本地调试登录失败:', error)
    }
  } else {
    await userStore.checkLogin()
  }

  registerWxWork()
  window.addEventListener('resize', adjustToolEmailTextarea)
  window.addEventListener('resize', adjustToolCcTextarea)
  adjustToolEmailTextarea()
  adjustToolCcTextarea()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', adjustToolEmailTextarea)
  window.removeEventListener('resize', adjustToolCcTextarea)
  clearAcceptanceStatusRefreshTimers()
  if (realtimeAnalysisAbortController) {
    realtimeAnalysisAbortController.abort()
  }
  stopRealtimeAnalysisTyping()
})

watch(toolEmail, () => {
  adjustToolEmailTextarea()
})

watch(toolCcEmails, () => {
  adjustToolCcTextarea()
})

watch(activeTab, (newValue, oldValue) => {
  if (!chatId.value || !newValue || newValue === oldValue) return
  ensureActiveTabData(chatId.value).catch((error) => {
    console.warn('load active tab data failed:', error)
  })
})

watch(chatId, (newValue, oldValue) => {
  if (!newValue || newValue === oldValue) return
  resetRealtimeAnalysisState()
})
</script>
