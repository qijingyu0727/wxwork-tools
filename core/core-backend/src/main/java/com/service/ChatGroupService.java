package com.service;

import org.springframework.stereotype.Service;
import com.model.AcceptanceStatusData;
import com.model.CustomerData;
import com.model.ImplementationCreateContext;
import com.model.MaintenanceRecord;
import com.model.ProductVersionSnapshot;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import com.model.request.UpdateTicketRequest;
import com.model.request.CreateMaintenanceRecordRequest;
import com.model.request.CreateImplementationRecordRequest;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import com.util.HttpClientUtil;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.UnknownHostException;
import jakarta.annotation.Resource;

@Service
public class ChatGroupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGroupService.class);
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Value("${cscrm.base.url:http://democenter.fit2cloud.cn:24916}")
    private String cscrmBaseUrl;

    @Value("${cscrm.api.path:/api/v1/staff-admin}")
    private String cscrmApiPath;

    @Value("${cscrm.api.key:}")
    private String cscrmApiKey;

    // 区域部门ID映射
    private static final List<Long> EAST_REGION_DEPT_IDS = List.of(129L, 131L, 130L, 61L, 123L);
    private static final List<Long> NORTH_REGION_DEPT_IDS = List.of(127L, 145L, 146L, 60L);
    private static final List<Long> SOUTH_REGION_DEPT_IDS = List.of(125L, 126L, 62L);

    // 线上部门ID
    private static final List<Long> ONLINE_DEPT_IDS = List.of(123L, 127L, 125L);

    // 虚拟账号部门ID
    private static final List<Long> VIRTUAL_DEPT_IDS = List.of(61L, 60L, 62L);

    // 线下部门ID
    private static final List<Long> OFFLINE_DEPT_IDS = List.of(129L, 131L, 130L, 145L, 146L, 126L);

    // 版本聚合产品ID映射
    private static final List<Long> MAXKB_PRODUCT_IDS = Arrays.asList(2009L, 2013L);
    private static final List<Long> DATAEASE_PRODUCT_IDS = Arrays.asList(2003L, 2008L);

    @Resource
    private FinanceLedgerService financeLedgerService;

    private static final String ACCEPTANCE_REPORT_REQUIRED_ID = "ID01lceprWXBrp";
    private static final String IMPLEMENTATION_DEFAULT_VALIDATION_VALUE = "正常，满足客户使用";

    public CustomerData getCustomerData(String extChatId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            long totalStartNs = System.nanoTime();
            String sql = "with ticket_count as ( " +
                    "     select room_id, " +
                    "            sum(if(resolved, 0, 1)) not_resolved_ticket_count, " +
                    "            count(1) all_ticket_count, " +
                    "            sum(if(status = 3, 1, 0)) critical_ticket_count " +
                    "     from chat_analysis_tickets " +
                    "     where deleted_at IS NULL " +
                    "     group by room_id " +
                    " ), " +
                    " issue_count as ( " +
                    "     select room_id, " +
                    "            sum(if(resolved, 0, 1)) not_resolved_issue_count, " +
                    "            count(1) all_issue_count " +
                    "     from chat_analysis_tickets " +
                    "     where issue_category = '功能需求' AND deleted_at IS NULL " +
                    "     group by room_id " +
                    " ), " +
                    " bug_count as ( " +
                    "     select room_id, " +
                    "            sum(if(resolved, 0, 1)) not_resolved_bug_count, " +
                    "            count(1) all_bug_count " +
                    "     from chat_analysis_tickets " +
                    "     where issue_category = '产品缺陷' AND deleted_at IS NULL " +
                    "     group by room_id " +
                    " ), " +
                    " subscription_info as ( " +
                    "     select client_id, " +
                    "            group_chat_name, " +
                    "            from_unixtime(max(support_subscription.support_end_date)/1000,'%Y-%m-%d') subscription_end_date " +
                    "     from support_subscription " +
                    "     group by client_id, " +
                    "              group_chat_name " +
                    " ) " +
                    " " +
                    " select support_client.name, " +
                    "        group_chat.name as chat_name, " +
                    "        subscription_info.client_id, " +
                    "        subscription_info.subscription_end_date, " +
                    "        ticket_count.not_resolved_ticket_count, " +
                    "        ticket_count.all_ticket_count, " +
                    "        ticket_count.critical_ticket_count, " +
                    "        issue_count.all_issue_count, " +
                    "        issue_count.not_resolved_issue_count, " +
                    "        bug_count.all_bug_count, " +
                    "        bug_count.not_resolved_bug_count " +
                    " from group_chat " +
                    " left join subscription_info on group_chat.name = subscription_info.group_chat_name " +
                    " left join support_client on subscription_info.client_id = support_client.id " +
                    " left join ticket_count on ticket_count.room_id = group_chat.ext_chat_id " +
                    " left join issue_count on issue_count.room_id = group_chat.ext_chat_id " +
                    " left join bug_count on bug_count.room_id = group_chat.ext_chat_id " +
                    " where ext_chat_id = ?";

            long baseQueryStartNs = System.nanoTime();
            var result = com.util.JdbcUtils.query(sql, extChatId);
            long baseQueryCostMs = (System.nanoTime() - baseQueryStartNs) / 1_000_000;
            CustomerData data = new CustomerData();

            if (!result.isEmpty()) {
                Object[] row = result.get(0);
                String clientName = row[0] != null ? row[0].toString() : null;
                data.setName(resolveCustomerName(clientName, extChatId));
                data.setClientId(row[2] != null ? Long.parseLong(row[2].toString()) : null);
                data.setSubscriptionEndDate(row[3] != null ? row[3].toString() : null);
                data.setNotResolvedTicketCount(row[4] != null ? Integer.parseInt(row[4].toString()) : 0);
                data.setAllTicketCount(row[5] != null ? Integer.parseInt(row[5].toString()) : 0);
                data.setCriticalTicketCount(row[6] != null ? Integer.parseInt(row[6].toString()) : 0);
                data.setAllIssueCount(row[7] != null ? Integer.parseInt(row[7].toString()) : 0);
                data.setNotResolvedIssueCount(row[8] != null ? Integer.parseInt(row[8].toString()) : 0);
                data.setAllBugCount(row[9] != null ? Integer.parseInt(row[9].toString()) : 0);
                data.setNotResolvedBugCount(row[10] != null ? Integer.parseInt(row[10].toString()) : 0);
                long productMetaStartNs = System.nanoTime();
                fillCustomerProductMeta(extChatId, data);
                long productMetaCostMs = (System.nanoTime() - productMetaStartNs) / 1_000_000;

                LOGGER.info("getCustomerData timing extChatId={}, baseQueryMs={}, productMetaMs={}, acceptanceMs=0, totalMs={}",
                        extChatId,
                        baseQueryCostMs,
                        productMetaCostMs,
                        (System.nanoTime() - totalStartNs) / 1_000_000);
            } else {
                data.setName("未知客户");
                LOGGER.info("getCustomerData timing extChatId={}, baseQueryMs={}, productMetaMs=0, acceptanceMs=0, totalMs={}",
                        extChatId,
                        baseQueryCostMs,
                        (System.nanoTime() - totalStartNs) / 1_000_000);
            }

            return data;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private String resolveCustomerName(String clientName, String extChatId) {
        String resolvedClientName = sanitizeCustomerName(clientName);
        if (resolvedClientName != null) {
            return resolvedClientName;
        }
        LOGGER.warn("getCustomerData customer name missing, extChatId={}", extChatId);
        return null;
    }

    private String sanitizeCustomerName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String value = rawName.trim();
        if (value.isEmpty() || "-".equals(value) || "--".equals(value) || "—".equals(value)) {
            return null;
        }
        return value;
    }

    private void fillAcceptanceStatus(String extChatId, CustomerData data) {
        if (data == null) {
            return;
        }
        long startNs = System.nanoTime();
        try {
            FinanceLedgerService.LedgerRecord ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatId(extChatId);
            if (ledgerRecord == null) {
                LOGGER.warn("fillAcceptanceStatus ledger not found extChatId={}, clientId={}", extChatId, data.getClientId());
                return;
            }
            AcceptanceStatusData acceptanceStatusData = buildAcceptanceStatusData(ledgerRecord);
            data.setNeedAcceptanceReport(acceptanceStatusData.getNeedAcceptanceReport());
            data.setAccepted(acceptanceStatusData.getAccepted());
            data.setAcceptanceStatusCode(acceptanceStatusData.getAcceptanceStatusCode());
            data.setIsAccepted(acceptanceStatusData.getIsAccepted());

            LOGGER.info("fillAcceptanceStatus success extChatId={}, clientId={}, contractCode={}, needAcceptanceReport={}, accepted={}, acceptanceStatusCode={}",
                    extChatId,
                    data.getClientId(),
                    ledgerRecord.getCode(),
                    acceptanceStatusData.getNeedAcceptanceReport(),
                    acceptanceStatusData.getAccepted(),
                    acceptanceStatusData.getAcceptanceStatusCode());
        } catch (Exception e) {
            LOGGER.warn("fillAcceptanceStatus failed extChatId={}, clientId={}, err={}",
                    extChatId, data.getClientId(), e.getMessage());
        } finally {
            LOGGER.info("fillAcceptanceStatus timing extChatId={}, costMs={}",
                    extChatId, (System.nanoTime() - startNs) / 1_000_000);
        }
    }

    public AcceptanceStatusData getAcceptanceStatus(String extChatId) {
        long startNs = System.nanoTime();
        try {
            String resolveMode = "fast";
            FinanceLedgerService.LedgerRecord ledgerRecord;
            try {
                ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatIdFast(extChatId);
                if (ledgerRecord == null) {
                    resolveMode = "full_after_fast_miss";
                    ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatId(extChatId);
                }
            } catch (Exception fastLookupError) {
                resolveMode = "full_after_fast_error";
                LOGGER.warn("getAcceptanceStatus fast lookup failed extChatId={}, err={}",
                        extChatId,
                        fastLookupError.getMessage());
                ledgerRecord = financeLedgerService.resolveLedgerRecordByExtChatId(extChatId);
            }
            AcceptanceStatusData data = buildAcceptanceStatusData(ledgerRecord);
            LOGGER.info("getAcceptanceStatus timing extChatId={}, found={}, resolveMode={}, costMs={}",
                    extChatId,
                    ledgerRecord != null,
                    resolveMode,
                    (System.nanoTime() - startNs) / 1_000_000);
            return data;
        } catch (Exception e) {
            LOGGER.warn("getAcceptanceStatus failed extChatId={}, costMs={}, err={}",
                    extChatId,
                    (System.nanoTime() - startNs) / 1_000_000,
                    e.getMessage());
            return new AcceptanceStatusData();
        }
    }

    private AcceptanceStatusData buildAcceptanceStatusData(FinanceLedgerService.LedgerRecord ledgerRecord) {
        AcceptanceStatusData data = new AcceptanceStatusData();
        if (ledgerRecord == null) {
            return data;
        }
        String needAcceptanceReport = ACCEPTANCE_REPORT_REQUIRED_ID.equals(trim(ledgerRecord.getCheckAcceptReport())) ? "是" : "否";
        String accepted = "是".equals(trim(ledgerRecord.getCheckAccept())) ? "是" : "否";
        String acceptanceStatusCode;
        String acceptanceLabel;

        if ("否".equals(needAcceptanceReport)) {
            acceptanceStatusCode = "not_required";
            acceptanceLabel = "无需验收";
        } else if ("是".equals(accepted)) {
            acceptanceStatusCode = "accepted";
            acceptanceLabel = "已验收";
        } else {
            acceptanceStatusCode = "pending";
            acceptanceLabel = "待验收";
        }

        data.setNeedAcceptanceReport(needAcceptanceReport);
        data.setAccepted(accepted);
        data.setAcceptanceStatusCode(acceptanceStatusCode);
        data.setIsAccepted(acceptanceLabel);
        return data;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    // 补齐客户产品/区域信息（优先订阅数据，次选群聊产品映射，再次选维护记录）
    private void fillCustomerProductMeta(String extChatId, CustomerData data) {
        if (data == null) {
            return;
        }
        long startNs = System.nanoTime();
        LOGGER.info("fillCustomerProductMeta start extChatId={}, clientId={}", extChatId, data.getClientId());

        // 1) 如果 productId 为空，按群聊名称映射产品再去 support_product_service 取 product_id
        if (data.getProductId() == null) {
            long stepStartNs = System.nanoTime();
            try {
                String productSql = "WITH chat_product AS ( " +
                        "    SELECT CASE " +
                        "        WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                        "        WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                        "        WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                        "        WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBot' " +
                        "        ELSE NULL END AS product " +
                        "    FROM group_chat gc WHERE gc.ext_chat_id = ? " +
                        ") " +
                        "SELECT sps.product_id " +
                        "FROM support_product_service sps " +
                        "CROSS JOIN chat_product cp " +
                        "WHERE cp.product IS NOT NULL " +
                        "AND (UPPER(sps.name) LIKE CONCAT('%', UPPER(cp.product), '%') " +
                        "OR UPPER(cp.product) LIKE CONCAT('%', UPPER(sps.name), '%')) " +
                        "ORDER BY sps.product_id LIMIT 1";
                var productResult = com.util.JdbcUtils.query(productSql, extChatId);
                if (!productResult.isEmpty() && productResult.get(0)[0] != null) {
                    data.setProductId(Long.parseLong(productResult.get(0)[0].toString()));
                    LOGGER.info("fillCustomerProductMeta by chatName success extChatId={}, productId={}", extChatId, data.getProductId());
                }
            } catch (Exception ignored) {
                // 忽略并继续兜底
            } finally {
                LOGGER.info("fillCustomerProductMeta timing extChatId={}, step=chatNameProduct, costMs={}",
                        extChatId, (System.nanoTime() - stepStartNs) / 1_000_000);
            }
        }

        // 2) 如果 productId 仍为空，从该群实时工单记录里的 product 名称反查 product_id
        if (data.getProductId() == null) {
            long stepStartNs = System.nanoTime();
            try {
                String ticketProductSql = "SELECT cat.product " +
                        "FROM chat_analysis_tickets cat " +
                        "WHERE cat.room_id = ? " +
                        "AND cat.product IS NOT NULL AND cat.product != '' " +
                        "ORDER BY cat.updated_at DESC LIMIT 1";
                var ticketProductResult = com.util.JdbcUtils.query(ticketProductSql, extChatId);
                if (!ticketProductResult.isEmpty() && ticketProductResult.get(0)[0] != null) {
                    String productName = ticketProductResult.get(0)[0].toString().trim();
                    if (!productName.isEmpty()) {
                        String findProductIdSql = "SELECT sps.product_id " +
                                "FROM support_product_service sps " +
                                "WHERE UPPER(sps.name) = UPPER(?) " +
                                "OR UPPER(sps.name) LIKE CONCAT('%', UPPER(?), '%') " +
                                "OR UPPER(?) LIKE CONCAT('%', UPPER(sps.name), '%') " +
                                "ORDER BY sps.product_id LIMIT 1";
                        var pidResult = com.util.JdbcUtils.query(findProductIdSql, productName, productName, productName);
                        if (!pidResult.isEmpty() && pidResult.get(0)[0] != null) {
                            data.setProductId(Long.parseLong(pidResult.get(0)[0].toString()));
                            LOGGER.info("fillCustomerProductMeta by ticketProduct success extChatId={}, ticketProduct={}, productId={}",
                                    extChatId, productName, data.getProductId());
                        }
                    }
                }
            } catch (Exception ignored) {
                // 忽略并继续兜底
            } finally {
                LOGGER.info("fillCustomerProductMeta timing extChatId={}, step=ticketProduct, costMs={}",
                        extChatId, (System.nanoTime() - stepStartNs) / 1_000_000);
            }
        }

        // 3) regionId 兜底取最近维护记录
        if ((data.getRegionId() == null || data.getRegionId().isEmpty()) && data.getClientId() != null) {
            long stepStartNs = System.nanoTime();
            try {
                String regionSql = "SELECT smr.region_id " +
                        "FROM support_maintenance_record smr " +
                        "WHERE smr.client_id = ? AND smr.region_id IS NOT NULL AND smr.region_id != '' " +
                        "ORDER BY smr.create_time DESC LIMIT 1";
                var regionResult = com.util.JdbcUtils.query(regionSql, data.getClientId());
                if (!regionResult.isEmpty() && regionResult.get(0)[0] != null) {
                    data.setRegionId(regionResult.get(0)[0].toString());
                    LOGGER.info("fillCustomerProductMeta region fallback success extChatId={}, regionId={}", extChatId, data.getRegionId());
                }
            } catch (Exception ignored) {
                // 忽略兜底失败
            } finally {
                LOGGER.info("fillCustomerProductMeta timing extChatId={}, step=regionFallback, costMs={}",
                        extChatId, (System.nanoTime() - stepStartNs) / 1_000_000);
            }
        }
        LOGGER.info("fillCustomerProductMeta done extChatId={}, productId={}, regionId={}, totalMs={}",
                extChatId, data.getProductId(), data.getRegionId(), (System.nanoTime() - startNs) / 1_000_000);
    }

    // 通用的获取工单方法
    private List<Ticket> getTicketsByCategory(String extChatId, String issueCategory) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            StringBuilder sql = new StringBuilder("SELECT cat.id, " +
                    "       cat.title, " +
                    "       cat.description, " +
                    "       cat.product, " +
                    "       cat.status, " +
                    "       cat.urgent, " +
                    "       cat.resolved, " +
                    "       cat.owner_name, " +
                    "       cat.issue_category, " +
                    "       cat.customer_sentiment, " +
                    "       cat.tracking_links, " +
                    "       DATE_FORMAT(cat.created_at, '%Y-%m-%d %H:%i:%s') as created_at, " +
                    "       DATE_FORMAT(cat.updated_at, '%Y-%m-%d %H:%i:%s') as updated_at " +
                    "FROM chat_analysis_tickets cat " +
                    "WHERE cat.room_id = ? AND cat.deleted_at IS NULL ");

            List<Object> params = new ArrayList<>();
            params.add(extChatId);

            if (issueCategory != null) {
                sql.append("AND cat.issue_category = ? ");
                params.add(issueCategory);
            }

            sql.append("ORDER BY cat.created_at DESC");

            var result = com.util.JdbcUtils.query(sql.toString(), params.toArray());
            List<Ticket> tickets = new ArrayList<>();

            for (Object[] row : result) {
                Ticket ticket = new Ticket();
                ticket.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                ticket.setTitle(row[1] != null ? row[1].toString() : null);
                ticket.setDescription(row[2] != null ? row[2].toString() : null);
                ticket.setProduct(row[3] != null ? row[3].toString() : null);
                ticket.setStatus(row[4] != null ? Long.parseLong(row[4].toString()) : null);
                ticket.setUrgent(row[5] != null ? (Boolean) row[5] : null);
                ticket.setResolved(row[6] != null ? (Boolean) row[6] : null);
                ticket.setOwnerName(row[7] != null ? row[7].toString() : null);
                ticket.setIssueCategory(row[8] != null ? row[8].toString() : null);
                ticket.setCustomerSentiment(row[9] != null ? row[9].toString() : null);
                ticket.setTrackingLinks(row[10] != null ? row[10].toString() : null);
                ticket.setCreatedAt(row[11] != null ? row[11].toString() : null);
                ticket.setUpdatedAt(row[12] != null ? row[12].toString() : null);
                tickets.add(ticket);
            }

            return tickets;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public List<Ticket> getIssueTickets(String extChatId) {
        return getTicketsByCategory(extChatId, "功能需求");
    }

    public List<Ticket> getBugTickets(String extChatId) {
        return getTicketsByCategory(extChatId, "产品缺陷");
    }

    public List<MaintenanceRecord> getMaintenanceRecords(String extChatId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            SubscriptionContext subscription = resolvePrimarySubscriptionContext(extChatId);
            Long subscriptionId = subscription != null ? subscription.subscriptionId : null;
            if (subscriptionId == null) {
                return new ArrayList<>();
            }

            String productAlias = getProductAliasByExtChatId(extChatId);
            String sql = "SELECT sm.id, " +
                    "       sm.status, " +
                    "       FROM_UNIXTIME(sm.deployment_time/1000, '%Y-%m-%d') as deployment_time, " +
                    "       sm.deployment_method, " +
                    "       sm.template, " +
                    "       sm.creator_name, " +
                    "       sm.version, " +
                    "       sm.content, " +
                    "       FROM_UNIXTIME(sm.create_time/1000, '%Y-%m-%d') as create_time " +
                    "FROM support_maintenance sm " +
                    "WHERE sm.subscription_id = ? " +
                    "ORDER BY sm.id DESC";

            var result = com.util.JdbcUtils.query(sql, subscriptionId);
            List<MaintenanceRecord> records = new ArrayList<>();

            for (Object[] row : result) {
                ImplementationContentSnapshot snapshot = parseImplementationContentSnapshot(row[7] != null ? row[7].toString() : null);
                String resolvedTemplate = firstNonBlank(
                        row[4] != null ? row[4].toString() : null,
                        snapshot.template
                );
                if (productAlias != null && !matchesProductAlias(productAlias, resolvedTemplate)) {
                    continue;
                }

                MaintenanceRecord record = new MaintenanceRecord();
                record.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                record.setStatus(row[1] != null ? row[1].toString() : null);
                record.setDeploymentTime(firstNonBlank(
                        normalizeLegacyDeploymentTime(row[2] != null ? row[2].toString() : null),
                        snapshot.deploymentTime
                ));
                record.setDeploymentMethod(firstNonBlank(
                        row[3] != null ? row[3].toString() : null,
                        snapshot.deploymentMethod
                ));
                record.setTemplate(resolvedTemplate);
                record.setCreatorName(row[5] != null ? row[5].toString() : null);
                record.setVersion(firstNonBlank(
                        row[6] != null ? row[6].toString() : null,
                        snapshot.version
                ));
                record.setContent(buildImplementationDisplayContent(snapshot, row[7] != null ? row[7].toString() : null));
                record.setCreateTime(firstNonBlank(
                        normalizeLegacyDeploymentTime(row[8] != null ? row[8].toString() : null),
                        snapshot.deploymentTime
                ));
                records.add(record);
            }

            return records;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public List<ServiceRecord> getServiceRecords(String extChatId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "WITH chat_product AS ( " +
                    "    SELECT gc.name, " +
                    "           CASE " +
                    "               WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                    "               WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                    "               WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                    "               WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBot' " +
                    "               ELSE NULL " +
                    "           END AS product " +
                    "    FROM group_chat gc " +
                    "    WHERE gc.ext_chat_id = ? " +
                    ") " +
                    "SELECT DISTINCT smr.id, " +
                    "       smr.maintenance_types, " +
                    "       smr.maintenance_version, " +
                    "       smr.maintenance_title, " +
                    "       smr.maintenance_context, " +
                    "       smr.maintenance_time, " +
                    "       FROM_UNIXTIME(smr.maintenance_time/1000, '%Y-%m-%d') as maintenance_time_formatted, " +
                    "       smr.creator_name, " +
                    "       FROM_UNIXTIME(smr.create_time/1000, '%Y-%m-%d') as create_time " +
                    "FROM support_maintenance_record smr " +
                    "CROSS JOIN chat_product cp " +
                    "WHERE smr.client_id IN ( " +
                    "    SELECT DISTINCT ss.client_id " +
                    "    FROM group_chat gc " +
                    "    INNER JOIN support_subscription ss ON gc.name = ss.group_chat_name " +
                    "    WHERE gc.ext_chat_id = ? " +
                    ") " +
                    "AND ( " +
                    "    (cp.product = 'MaxKB' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%MAXKB%' " +
                    "    )) " +
                    "    OR " +
                    "    (cp.product = 'JumpServer' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%JUMPSERVER%' " +
                    "    )) " +
                    "    OR " +
                    "    (cp.product = 'DataEase' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%DATAEASE%' " +
                    "    )) " +
                    "    OR " +
                    "    (cp.product = 'SQLBot' AND smr.product_id IN ( " +
                    "        SELECT DISTINCT product_id " +
                    "        FROM support_product_service " +
                    "        WHERE UPPER(name) LIKE '%SQLBOT%' " +
                    "    )) " +
                    "    OR " +
                    "    smr.product_id IS NULL OR smr.product_id = 0 " +
                    ") " +
                    "ORDER BY smr.maintenance_time DESC";

            var result = com.util.JdbcUtils.query(sql, extChatId, extChatId);
            List<ServiceRecord> records = new ArrayList<>();

            for (Object[] row : result) {
                ServiceRecord record = new ServiceRecord();
                record.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                record.setMaintenanceTypes(row[1] != null ? row[1].toString() : null);
                record.setMaintenanceVersion(row[2] != null ? row[2].toString() : null);
                record.setMaintenanceTitle(row[3] != null ? row[3].toString() : null);
                record.setMaintenanceContext(row[4] != null ? row[4].toString() : null);
                record.setMaintenanceTime(row[6] != null ? row[6].toString() : null);
                record.setCreatorName(row[7] != null ? row[7].toString() : null);
                record.setCreateTime(row[8] != null ? row[8].toString() : null);
                records.add(record);
            }

            return records;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public List<Ticket> getTickets(String extChatId) {
        return getTicketsByCategory(extChatId, null);
    }

    public List<TicketLog> getTicketLogs(Long ticketId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT catl.id, " +
                    "       catl.ticket_id, " +
                    "       catl.action, " +
                    "       catl.value, " +
                    "       catl.event_type, " +
                    "       catl.modified_by_name, " +
                    "       catl.comment, " +
                    "       DATE_FORMAT(catl.created_at, '%Y-%m-%d %H:%i:%s') as created_at " +
                    "FROM chat_analysis_ticket_logs catl " +
                    "WHERE catl.ticket_id = ? AND catl.deleted_at IS NULL " +
                    "ORDER BY catl.created_at ASC";

            var result = com.util.JdbcUtils.query(sql, ticketId);
            List<TicketLog> logs = new ArrayList<>();

            for (Object[] row : result) {
                TicketLog log = new TicketLog();
                log.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                log.setTicketId(row[1] != null ? Long.parseLong(row[1].toString()) : null);
                log.setAction(row[2] != null ? row[2].toString() : null);
                log.setValue(row[3] != null ? row[3].toString() : null);
                log.setEventType(row[4] != null ? row[4].toString() : null);
                log.setModifiedByName(row[5] != null ? row[5].toString() : null);
                log.setComment(row[6] != null ? row[6].toString() : null);
                log.setCreatedAt(row[7] != null ? row[7].toString() : null);
                logs.add(log);
            }

            return logs;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public void updateTicket(UpdateTicketRequest request, String modifiedById, String modifiedByName) throws Exception {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            // 打印接收到的参数
            LOGGER.info("========== Service 层接收到的参数 ==========");
            LOGGER.info("modifiedById (传入参数): {}", modifiedById);
            LOGGER.info("modifiedByName (传入参数): {}", modifiedByName);
            LOGGER.info("ownerName (处理人姓名): {}", request.getOwnerName());
            LOGGER.info("==========================================");

            // 根据处理人姓名查询处理人ID
            String ownerSql = "SELECT s.ext_id FROM staff s WHERE s.name = ? LIMIT 1";
            var ownerResult = com.util.JdbcUtils.query(ownerSql, request.getOwnerName());

            String ownerId = null;
            if (!ownerResult.isEmpty() && ownerResult.get(0)[0] != null) {
                ownerId = ownerResult.get(0)[0].toString();
            } else {
                throw new Exception("未找到处理人: " + request.getOwnerName());
            }

            LOGGER.info("查询到的处理人ID (ownerId): {}", ownerId);

            // 构建请求体
            JSONObject payload = new JSONObject();
            payload.put("id", request.getTicketId());
            payload.put("urgent", request.getUrgent());
            payload.put("customer_sentiment", request.getCustomerSentiment());
            payload.put("owner_name", request.getOwnerName());
            payload.put("owner_id", ownerId);  // 处理人ID
            payload.put("modified_by_id", modifiedById);  // 当前登录用户ID
            payload.put("modified_by_name", modifiedByName);  // 当前登录用户姓名
            payload.put("comment", request.getComment());
            payload.put("status", request.getStatus());
            payload.put("resolved", request.getResolved());
            if (request.getTrackingLinks() != null && !request.getTrackingLinks().isEmpty()) {
                payload.put("tracking_links", request.getTrackingLinks());
            }

            // 打印日志
            LOGGER.info("========== 更新工单请求信息 ==========");
            LOGGER.info("请求URL: {}", cscrmBaseUrl + cscrmApiPath + "/smart-tickets/tickets/" + request.getTicketId());
            LOGGER.info("请求头 Authorization: Bearer {}", (cscrmApiKey != null ? cscrmApiKey.substring(0, Math.min(20, cscrmApiKey.length())) + "..." : "null"));
            LOGGER.info("请求头 X-API-Key: {}", (cscrmApiKey != null ? cscrmApiKey.substring(0, Math.min(20, cscrmApiKey.length())) + "..." : "null"));
            LOGGER.info("请求体 Payload: {}", payload.toJSONString());
            LOGGER.info("=====================================");

            // 调用 CSCRM API
            String url = cscrmBaseUrl + cscrmApiPath + "/smart-tickets/tickets/" + request.getTicketId();
            String response = HttpClientUtil.putJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);

            LOGGER.info("响应内容: {}", response);

            JSONObject responseJson = JSONObject.parseObject(response);
            if (responseJson.getInteger("code") != 0) {
                throw new Exception("更新工单失败: " + responseJson.getString("msg"));
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    // 更新需求/缺陷工单：按 api.md 中“更新需求工单/更新缺陷工单”字段组装
    public void updateIssueOrBugTicket(UpdateTicketRequest request, String loginUserId, String kind) throws Exception {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String ownerId = request.getOwnerId();
            if (ownerId == null || ownerId.isEmpty()) {
                String ownerName = request.getOwnerName();
                if (ownerName != null && !ownerName.isEmpty()) {
                    String ownerSql = "SELECT s.ext_id FROM staff s WHERE s.name = ? LIMIT 1";
                    var ownerResult = com.util.JdbcUtils.query(ownerSql, ownerName);
                    if (!ownerResult.isEmpty() && ownerResult.get(0)[0] != null) {
                        ownerId = ownerResult.get(0)[0].toString();
                    }
                }
            }
            if (ownerId == null || ownerId.isEmpty()) {
                ownerId = loginUserId;
            }
            if (ownerId == null || ownerId.isEmpty()) {
                throw new Exception("缺少负责人ID");
            }
            String ownerName = resolveStaffNameByExtId(ownerId);
            String modifiedByName = resolveStaffNameByExtId(loginUserId);

            JSONObject payload = new JSONObject();
            payload.put("id", request.getTicketId());
            payload.put("owner_id", ownerId);
            if (ownerName != null && !ownerName.isEmpty()) {
                payload.put("owner_name", ownerName);
            }
            if (loginUserId != null && !loginUserId.isEmpty()) {
                payload.put("modified_by_id", loginUserId);
            }
            if (modifiedByName != null && !modifiedByName.isEmpty()) {
                payload.put("modified_by_name", modifiedByName);
            }
            payload.put("comment", request.getComment());
            payload.put("reminder_cycle", request.getReminderCycle() != null ? request.getReminderCycle() : 2);
            payload.put("urgent", request.getUrgent());
            payload.put("customer_sentiment", request.getCustomerSentiment());
            payload.put("tracking_links", request.getTrackingLinks() == null ? "" : request.getTrackingLinks());
            payload.put("status", request.getStatus());
            if (request.getResolved() != null) {
                payload.put("resolved", request.getResolved());
            }

            String url = cscrmBaseUrl + cscrmApiPath + "/smart-tickets/tickets/" + request.getTicketId();
            LOGGER.info("update{}Ticket request url={}, payload={}",
                    "bug".equals(kind) ? "Bug" : "Issue", url, payload.toJSONString());

            String response = HttpClientUtil.putJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);
            LOGGER.info("update{}Ticket response={}", "bug".equals(kind) ? "Bug" : "Issue", response);

            JSONObject responseJson = JSONObject.parseObject(response);
            Integer code = responseJson.getInteger("code");
            if (code != null && code != 0) {
                String msg = responseJson.getString("msg");
                if (msg == null || msg.isEmpty()) {
                    msg = responseJson.getString("message");
                }
                throw new Exception(msg != null ? msg : "更新失败");
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private String resolveStaffNameByExtId(String extId) {
        if (extId == null || extId.isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT s.name FROM staff s WHERE s.ext_id = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, extId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return result.get(0)[0].toString();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    public ImplementationCreateContext getImplementationCreateContext(String extChatId, String loginUserId) throws Exception {
        if (extChatId == null || extChatId.isEmpty()) {
            throw new Exception("缺少群聊ID");
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            SubscriptionContext subscription = resolvePrimarySubscriptionContext(extChatId);
            if (subscription == null || subscription.subscriptionId == null) {
                throw new Exception("未找到当前群聊对应的有效订阅");
            }
            String implementationProductAlias = resolveImplementationProductAlias(subscription, extChatId);
            if (implementationProductAlias == null) {
                throw new Exception("当前群聊暂不支持新增实施记录");
            }

            ImplementationCreateContext context = new ImplementationCreateContext();
            context.setSubscriptionId(subscription.subscriptionId);
            context.setClientId(subscription.clientId);
            context.setClientName(subscription.clientName);
            context.setContractNumber(subscription.contractNumber);
            context.setProductId(subscription.productId);
            context.setProductName(subscription.productName);
            context.setServiceTypeName(subscription.serviceTypeName);
            context.setSalesName(resolveSalesName(subscription.clientId));
            context.setRegionId(subscription.regionId);
            context.setRegionName(resolveRegionName(subscription.regionId));
            context.setSubscriptionStartDate(subscription.subscriptionStartDate);
            context.setSupportEndDate(subscription.supportEndDate);
            context.setDefaultSubmitterUserId(loginUserId);
            context.setDefaultSubmitterName(resolveSubmitterName(loginUserId));
            context.setProductAlias(implementationProductAlias);
            context.setSubscriptionDisplayText(buildSubscriptionDisplayText(subscription));
            context.setAvailableVersions(getProductVersions(subscription.productId, extChatId));
            return context;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    public JSONObject createMaintenanceRecord(CreateMaintenanceRecordRequest request, String loginUserId) throws Exception {
        if (request.getClientId() == null) {
            throw new Exception("缺少客户ID");
        }
        if (request.getProductId() == null) {
            throw new Exception("缺少产品ID");
        }
        if (request.getMaintenanceTime() == null) {
            throw new Exception("缺少维护日期");
        }
        if (request.getMaintenanceTypes() == null || request.getMaintenanceTypes().isEmpty()) {
            throw new Exception("缺少维护类型");
        }
        if (request.getMaintenanceTitle() == null || request.getMaintenanceTitle().isEmpty()) {
            throw new Exception("缺少概述");
        }
        if (request.getMaintenanceVersion() == null || request.getMaintenanceVersion().isEmpty()) {
            throw new Exception("缺少版本");
        }
        if (request.getMaintenanceContext() == null || request.getMaintenanceContext().isEmpty()) {
            throw new Exception("缺少详细过程记录");
        }

        String regionId = request.getRegionId();
        if (regionId == null || regionId.isEmpty()) {
            regionId = resolveRegionId(request.getClientId(), request.getExtChatId());
        }
        if (regionId == null || regionId.isEmpty()) {
            throw new Exception("缺少区域ID，无法提交维护记录");
        }

        String originalUserId = request.getOwnerId();
        if (originalUserId == null || originalUserId.isEmpty()) {
            originalUserId = request.getEditorUserId();
        }
        if (originalUserId == null || originalUserId.isEmpty()) {
            originalUserId = loginUserId;
        }
        if (originalUserId == null || originalUserId.isEmpty()) {
            throw new Exception("缺少提交人ID");
        }
        SupportUserResolution resolution = resolveSupportUserIdDetails(originalUserId);
        String ownerId = resolution.resolvedUserId;
        LOGGER.info(
                "createMaintenanceRecord owner resolution originalUserId={}, resolvedOwnerId={}, uuidCandidate={}, " +
                        "staffHit={}, staffName={}, staffEmail={}, directSupportUserHit={}, directSupportUserId={}, " +
                        "supportUserByStaffHit={}, supportUserByStaffId={}, fallbackToOriginal={}",
                resolution.originalCandidate,
                resolution.resolvedUserId,
                resolution.uuidCandidate,
                resolution.staffHit,
                resolution.staffName,
                resolution.staffEmail,
                resolution.directSupportUserHit,
                resolution.directSupportUserId,
                resolution.supportUserByStaffHit,
                resolution.supportUserByStaffId,
                resolution.fallbackToOriginal
        );

        JSONObject payload = new JSONObject();
        payload.put("clientId", request.getClientId());
        payload.put("editorUserId", ownerId);
        payload.put("creatorId", ownerId);
        payload.put("updaterId", ownerId);
        payload.put("maintenanceTypes", request.getMaintenanceTypes());
        payload.put("maintenanceTitle", request.getMaintenanceTitle());
        payload.put("maintenanceTime", request.getMaintenanceTime());
        payload.put("regionId", regionId);
        payload.put("maintenanceVersion", request.getMaintenanceVersion());
        payload.put("maintenanceContext", request.getMaintenanceContext());
        payload.put("productId", request.getProductId());

        String url = cscrmBaseUrl + cscrmApiPath + "/support-info/maintenance-records";
        LOGGER.info("createMaintenanceRecord request url={}, payload={}", url, payload.toJSONString());
        String response = HttpClientUtil.postJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);
        LOGGER.info("createMaintenanceRecord response={}", response);

        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            throw new Exception(responseJson.getString("msg"));
        }
        return responseJson.getJSONObject("data");
    }

    public JSONObject createImplementationRecord(CreateImplementationRecordRequest request, String loginUserId) throws Exception {
        if (request.getSubscriptionId() == null) {
            throw new Exception("缺少订阅ID");
        }
        if (request.getClientId() == null) {
            throw new Exception("缺少客户ID");
        }
        if (request.getProductId() == null) {
            throw new Exception("缺少产品ID");
        }
        if (request.getDeploymentDate() == null || request.getDeploymentDate().isEmpty()) {
            throw new Exception("缺少部署日期");
        }
        if (request.getDeploymentMethod() == null || request.getDeploymentMethod().isEmpty()) {
            throw new Exception("缺少部署方式");
        }
        if (request.getVersion() == null || request.getVersion().isEmpty()) {
            throw new Exception("缺少软件版本");
        }
        String implementationProductAlias = resolveImplementationProductAlias(resolvePrimarySubscriptionContext(request.getExtChatId()), request.getExtChatId());
        if (implementationProductAlias == null) {
            throw new Exception("当前群聊暂不支持新增实施记录");
        }
        validateImplementationRequestByProduct(implementationProductAlias, request);

        String regionId = request.getRegionId();
        if (regionId == null || regionId.isEmpty()) {
            regionId = resolveRegionId(request.getClientId(), request.getExtChatId());
        }
        if (regionId == null || regionId.isEmpty()) {
            throw new Exception("缺少区域ID，无法提交实施记录");
        }

        String originalUserId = request.getEditorUserId();
        if (originalUserId == null || originalUserId.isEmpty()) {
            originalUserId = loginUserId;
        }
        if (originalUserId == null || originalUserId.isEmpty()) {
            throw new Exception("缺少提交人ID");
        }

        SupportUserResolution resolution = resolveSupportUserIdDetails(originalUserId);
        String editorUserId = resolution.resolvedUserId;
        LOGGER.info(
                "createImplementationRecord owner resolution originalUserId={}, resolvedOwnerId={}, uuidCandidate={}, " +
                        "staffHit={}, staffName={}, staffEmail={}, directSupportUserHit={}, directSupportUserId={}, " +
                        "supportUserByStaffHit={}, supportUserByStaffId={}, fallbackToOriginal={}",
                resolution.originalCandidate,
                resolution.resolvedUserId,
                resolution.uuidCandidate,
                resolution.staffHit,
                resolution.staffName,
                resolution.staffEmail,
                resolution.directSupportUserHit,
                resolution.directSupportUserId,
                resolution.supportUserByStaffHit,
                resolution.supportUserByStaffId,
                resolution.fallbackToOriginal
        );

        JSONObject payload = new JSONObject();
        payload.put("subscriptionId", request.getSubscriptionId());
        payload.put("status", "DEPLOYED");
        payload.put("editorUserId", editorUserId);
        payload.put("regionId", regionId);
        payload.put("content", buildImplementationContent(implementationProductAlias, request));

        String url = cscrmBaseUrl + cscrmApiPath + "/support-info/maintenances";
        LOGGER.info("createImplementationRecord request url={}, payload={}", url, payload.toJSONString());
        String response = HttpClientUtil.postJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);
        LOGGER.info("createImplementationRecord response={}", response);

        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            String message = responseJson.getString("msg");
            if (message == null || message.isEmpty()) {
                message = responseJson.getString("message");
            }
            throw new Exception(message != null ? message : "新增实施记录失败");
        }
        return responseJson.getJSONObject("data");
    }

    private void validateImplementationRequestByProduct(String implementationProductAlias, CreateImplementationRecordRequest request) throws Exception {
        switch (implementationProductAlias) {
            case "JS" -> {
                if (request.getAssetTypes() == null || request.getAssetTypes().isEmpty()) {
                    throw new Exception("缺少纳管资产类型");
                }
                if (isBlank(request.getAssetCount())) {
                    throw new Exception("缺少管理资产数");
                }
                if (isBlank(request.getVirtualizationType())) {
                    throw new Exception("缺少虚拟化类型");
                }
                if (isBlank(request.getApplicationServer())) {
                    throw new Exception("缺少应用发布服务器");
                }
                if (isBlank(request.getDatabaseSync())) {
                    throw new Exception("缺少数据同步配置");
                }
                if (isBlank(request.getDatabaseExternal())) {
                    throw new Exception("缺少数据库外置配置");
                }
                if (isBlank(request.getRedisExternal())) {
                    throw new Exception("缺少 Redis 外置配置");
                }
                if (isBlank(request.getSharedNfs())) {
                    throw new Exception("缺少共享存储配置");
                }
                if (isBlank(request.getDeploymentArchitecture())) {
                    throw new Exception("缺少部署架构");
                }
                if (isBlank(request.getDeploymentRecord())) {
                    throw new Exception("缺少记录内容");
                }
            }
            case "MK" -> {
                if (isBlank(request.getDeploymentArchitecture())) {
                    throw new Exception("缺少部署架构");
                }
                if (request.getAuthMethods() == null || request.getAuthMethods().isEmpty()) {
                    throw new Exception("缺少认证方式");
                }
                if (request.getBusinessDirections() == null || request.getBusinessDirections().isEmpty()) {
                    throw new Exception("缺少应用方向");
                }
            }
            case "DE" -> {
                if (isBlank(request.getBackupMethod())) {
                    throw new Exception("缺少备份方式");
                }
                if (isBlank(request.getDataEaseDatabase())) {
                    throw new Exception("缺少数据库配置");
                }
                if (isBlank(request.getDorisUsage())) {
                    throw new Exception("缺少 Doris 配置");
                }
                if (isBlank(request.getDeploymentArchitecture())) {
                    throw new Exception("缺少部署架构");
                }
                if (isBlank(request.getDataSourceType())) {
                    throw new Exception("缺少数据源类型");
                }
                if (isBlank(request.getDataScale())) {
                    throw new Exception("缺少数据量规模");
                }
                if (request.getAuthMethods() == null || request.getAuthMethods().isEmpty()) {
                    throw new Exception("缺少认证方式");
                }
                if (isBlank(request.getEmbeddedMode())) {
                    throw new Exception("缺少嵌入配置");
                }
                if (isBlank(request.getCustomerJoined())) {
                    throw new Exception("缺少客户接入状态");
                }
                if (isBlank(request.getAnalysisDirection())) {
                    throw new Exception("缺少分析及展示方向");
                }
                if (isBlank(request.getCustomerFocus())) {
                    throw new Exception("缺少客户核心关注点");
                }
            }
            default -> throw new Exception("当前群聊暂不支持新增实施记录");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private JSONObject buildImplementationContent(String implementationProductAlias, CreateImplementationRecordRequest request) throws Exception {
        return switch (implementationProductAlias) {
            case "JS" -> buildJumpServerImplementationContent(request);
            case "MK" -> buildMaxKbImplementationContent(request);
            case "DE" -> buildDataEaseImplementationContent(request);
            default -> throw new Exception("当前群聊暂不支持新增实施记录");
        };
    }

    // 维护记录接口需要 support_user.user_id（UUID）。当前登录态常见为企业微信 userid（如 QiJingYu），这里做自动映射。
    private SupportUserResolution resolveSupportUserIdDetails(String candidate) {
        SupportUserResolution resolution = new SupportUserResolution(candidate);
        if (candidate == null || candidate.isEmpty()) {
            resolution.resolvedUserId = candidate;
            resolution.fallbackToOriginal = true;
            return resolution;
        }
        if (isUuid(candidate)) {
            resolution.uuidCandidate = true;
            resolution.resolvedUserId = candidate;
            return resolution;
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            // 1) 直接按 support_user.username / name / email 兜底匹配
            try {
                String sql = "SELECT su.user_id FROM support_user su " +
                        "WHERE su.username = ? OR su.name = ? OR su.email = ? LIMIT 1";
                var result = com.util.JdbcUtils.query(sql, candidate, candidate, candidate);
                if (!result.isEmpty() && result.get(0)[0] != null) {
                    resolution.directSupportUserHit = true;
                    resolution.directSupportUserId = result.get(0)[0].toString();
                    resolution.resolvedUserId = resolution.directSupportUserId;
                    return resolution;
                }
            } catch (Exception ignored) {
                // ignore and fallback
            }

            // 2) 先从 staff 按 ext_id 找到姓名/邮箱，再映射 support_user.user_id
            try {
                String staffSql = "SELECT s.name, s.email FROM staff s WHERE s.ext_id = ? LIMIT 1";
                var staffResult = com.util.JdbcUtils.query(staffSql, candidate);
                if (!staffResult.isEmpty()) {
                    resolution.staffHit = true;
                    String name = staffResult.get(0)[0] != null ? staffResult.get(0)[0].toString() : null;
                    String email = staffResult.get(0)[1] != null ? staffResult.get(0)[1].toString() : null;
                    resolution.staffName = name;
                    resolution.staffEmail = email;

                    String supportSql = "SELECT su.user_id FROM support_user su " +
                            "WHERE (? IS NOT NULL AND su.email = ?) " +
                            "   OR (? IS NOT NULL AND su.name = ?) " +
                            "LIMIT 1";
                    var supportResult = com.util.JdbcUtils.query(supportSql, email, email, name, name);
                    if (!supportResult.isEmpty() && supportResult.get(0)[0] != null) {
                        resolution.supportUserByStaffHit = true;
                        resolution.supportUserByStaffId = supportResult.get(0)[0].toString();
                        resolution.resolvedUserId = resolution.supportUserByStaffId;
                        return resolution;
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
        resolution.resolvedUserId = candidate;
        resolution.fallbackToOriginal = true;
        return resolution;
    }

    private static final class SupportUserResolution {
        private final String originalCandidate;
        private String resolvedUserId;
        private boolean uuidCandidate;
        private boolean staffHit;
        private String staffName;
        private String staffEmail;
        private boolean directSupportUserHit;
        private String directSupportUserId;
        private boolean supportUserByStaffHit;
        private String supportUserByStaffId;
        private boolean fallbackToOriginal;

        private SupportUserResolution(String originalCandidate) {
            this.originalCandidate = originalCandidate;
        }
    }

    private SubscriptionContext resolvePrimarySubscriptionContext(String extChatId) {
        String productAlias = getProductAliasByExtChatId(extChatId);
        String directSql = "SELECT " +
                "  ss.id, " +
                "  ss.client_id, " +
                "  sc.name, " +
                "  ss.contract_number, " +
                "  sps.product_id, " +
                "  sps.name, " +
                "  ss.region_id, " +
                "  FROM_UNIXTIME(ss.start_date / 1000, '%Y-%m-%d') AS subscription_start_date, " +
                "  FROM_UNIXTIME(ss.support_end_date / 1000, '%Y-%m-%d') AS support_end_date " +
                "FROM group_chat gc " +
                "INNER JOIN support_subscription ss ON ss.group_chat_name = gc.name " +
                "LEFT JOIN support_client sc ON sc.id = ss.client_id " +
                "LEFT JOIN support_product_service sps ON sps.id = ss.product_service_id " +
                "WHERE gc.ext_chat_id = ? " +
                "  AND ss.client_id IS NOT NULL " +
                "ORDER BY COALESCE(ss.support_end_date, 0) DESC, ss.id DESC " +
                "LIMIT 1";
        var directResult = com.util.JdbcUtils.query(directSql, extChatId);
        if (directResult.isEmpty()) {
            Long clientId = resolvePrimaryClientId(extChatId);
            if (clientId == null) {
                return null;
            }
            String sql = "SELECT " +
                    "  ss.id, " +
                    "  ss.client_id, " +
                    "  sc.name, " +
                    "  ss.contract_number, " +
                    "  sps.product_id, " +
                    "  sps.name, " +
                    "  ss.region_id, " +
                    "  FROM_UNIXTIME(ss.start_date / 1000, '%Y-%m-%d') AS subscription_start_date, " +
                    "  FROM_UNIXTIME(ss.support_end_date / 1000, '%Y-%m-%d') AS support_end_date " +
                    "FROM support_subscription ss " +
                    "LEFT JOIN support_client sc ON sc.id = ss.client_id " +
                    "LEFT JOIN support_product_service sps ON sps.id = ss.product_service_id " +
                    "WHERE ss.client_id = ? " +
                    "ORDER BY COALESCE(ss.support_end_date, 0) DESC, ss.id DESC " +
                    "LIMIT 20";
            var result = com.util.JdbcUtils.query(sql, clientId);
            SubscriptionContext fallback = null;
            for (Object[] row : result) {
                SubscriptionContext clientContext = toSubscriptionContext(row);
                if (fallback == null) {
                    fallback = clientContext;
                }
                if (matchesProductAlias(productAlias, clientContext.productName)) {
                    LOGGER.info("resolvePrimarySubscriptionContext by client success extChatId={}, clientId={}, subscriptionId={}, productAlias={}, productName={}",
                            extChatId, clientId, clientContext.subscriptionId, productAlias, clientContext.productName);
                    return clientContext;
                }
            }
            if (fallback != null) {
                LOGGER.info("resolvePrimarySubscriptionContext by client fallback extChatId={}, clientId={}, subscriptionId={}, productAlias={}, productName={}",
                        extChatId, clientId, fallback.subscriptionId, productAlias, fallback.productName);
                return fallback;
            }
            return null;
        }
        SubscriptionContext context = toSubscriptionContext(directResult.get(0));
        LOGGER.info("resolvePrimarySubscriptionContext by direct group success extChatId={}, subscriptionId={}, productAlias={}, productName={}",
                extChatId, context.subscriptionId, productAlias, context.productName);
        return context;
    }

    private Long resolvePrimaryClientId(String extChatId) {
        String sql = "SELECT ss.client_id " +
                "FROM group_chat gc " +
                "INNER JOIN support_subscription ss ON ss.group_chat_name = gc.name " +
                "WHERE gc.ext_chat_id = ? " +
                "  AND ss.client_id IS NOT NULL " +
                "ORDER BY COALESCE(ss.support_end_date, 0) DESC, ss.id DESC " +
                "LIMIT 1";
        var result = com.util.JdbcUtils.query(sql, extChatId);
        if (result.isEmpty() || result.get(0)[0] == null) {
            return null;
        }
        return toLong(result.get(0)[0]);
    }

    private SubscriptionContext toSubscriptionContext(Object[] row) {
        SubscriptionContext context = new SubscriptionContext();
        context.subscriptionId = toLong(row[0]);
        context.clientId = toLong(row[1]);
        context.clientName = toStringValue(row[2]);
        context.contractNumber = toStringValue(row[3]);
        context.productId = toLong(row[4]);
        context.productName = toStringValue(row[5]);
        context.regionId = toStringValue(row[6]);
        context.subscriptionStartDate = toStringValue(row[7]);
        context.supportEndDate = toStringValue(row[8]);
        context.serviceTypeName = "授权服务";
        return context;
    }

    private boolean matchesProductAlias(String productAlias, String productName) {
        if (productAlias == null || productName == null) {
            return false;
        }
        String value = productName.toUpperCase(Locale.ROOT);
        return switch (productAlias) {
            case "JS" -> value.contains("JUMPSERVER") || value.contains("JS");
            case "MK" -> value.contains("MAXKB") || value.contains("MK");
            case "DE" -> value.contains("DATAEASE") || value.contains("DE");
            case "SQLBOT" -> value.contains("SQLBOT");
            default -> false;
        };
    }

    private boolean isJumpServerSubscription(SubscriptionContext subscription, String extChatId) {
        if (subscription == null) {
            return false;
        }
        if (matchesProductAlias("JS", subscription.productName)) {
            return true;
        }
        String groupAlias = getProductAliasByExtChatId(extChatId);
        return "JS".equals(groupAlias);
    }

    private String resolveImplementationProductAlias(SubscriptionContext subscription, String extChatId) {
        if (subscription != null) {
            if (matchesProductAlias("JS", subscription.productName)) {
                return "JS";
            }
            if (matchesProductAlias("MK", subscription.productName)) {
                return "MK";
            }
            if (matchesProductAlias("DE", subscription.productName)) {
                return "DE";
            }
        }
        String groupAlias = getProductAliasByExtChatId(extChatId);
        if ("JS".equals(groupAlias) || "MK".equals(groupAlias) || "DE".equals(groupAlias)) {
            return groupAlias;
        }
        return null;
    }

    private String resolveSalesName(Long clientId) {
        if (clientId == null) {
            return null;
        }
        try {
            String sql = "SELECT su.name " +
                    "FROM client_sales_users csu " +
                    "INNER JOIN sales_user su ON su.user_id = csu.sales_user_user_id " +
                    "WHERE csu.support_client_id = ? " +
                    "AND su.name IS NOT NULL AND su.name != '' " +
                    "ORDER BY su.name LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, clientId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return result.get(0)[0].toString();
            }
        } catch (Exception e) {
            LOGGER.warn("resolveSalesName failed clientId={}, err={}", clientId, e.getMessage());
        }
        return null;
    }

    private String resolveSubmitterName(String loginUserId) {
        String staffName = resolveStaffNameByExtId(loginUserId);
        if (staffName != null && !staffName.isEmpty()) {
            return staffName;
        }
        return loginUserId;
    }

    private String resolveRegionName(String regionId) {
        if (regionId == null || regionId.isEmpty()) {
            return null;
        }
        String[] candidates = {
                "SELECT name FROM region WHERE id = ? LIMIT 1",
                "SELECT name FROM support_region WHERE id = ? LIMIT 1",
                "SELECT name FROM work_region WHERE id = ? LIMIT 1"
        };
        for (String sql : candidates) {
            try {
                var result = com.util.JdbcUtils.query(sql, regionId);
                if (!result.isEmpty() && result.get(0)[0] != null) {
                    return result.get(0)[0].toString();
                }
            } catch (Exception ignored) {
                // ignore and continue fallback
            }
        }
        return null;
    }

    private String buildSubscriptionDisplayText(SubscriptionContext subscription) {
        List<String> parts = new ArrayList<>();
        if (subscription.clientName != null && !subscription.clientName.isEmpty()) {
            parts.add(subscription.clientName);
        }
        if (subscription.productName != null && !subscription.productName.isEmpty()) {
            parts.add(subscription.productName);
        }
        if (subscription.contractNumber != null && !subscription.contractNumber.isEmpty()) {
            parts.add(subscription.contractNumber);
        }
        return String.join(" - ", parts);
    }

    private JSONObject buildJumpServerImplementationContent(CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", "JumpServer");
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "form1prop4", request.getVirtualizationType());
        addImplementationElement(elements, "form1prop11", request.getDeploymentArchitecture());
        addImplementationElement(elements, "form1prop3", request.getAssetCount());
        addImplementationElement(elements, "form1prop8", request.getApplicationServer());
        addImplementationElement(elements, "form1prop5", request.getDatabaseExternal());
        addImplementationElement(elements, "form1prop6", request.getRedisExternal());
        addImplementationElement(elements, "form1prop9", request.getDatabaseSync());
        addImplementationElement(elements, "form1prop7", request.getSharedNfs());
        addImplementationMultiValueElement(elements, "form1prop1", request.getAssetTypes());
        addImplementationElement(elements, "form1prop10", nullToEmpty(request.getCustomerFocus()));
        addImplementationElement(elements, "deploymentRecord", nullToEmpty(trimToNull(request.getDeploymentRecord())));
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        for (int i = 1; i <= 9; i++) {
            addImplementationElement(elements, "form2prop" + i, IMPLEMENTATION_DEFAULT_VALIDATION_VALUE);
        }
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private JSONObject buildMaxKbImplementationContent(CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", "MaxKBV2_PRO");
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "form1prop1", request.getDeploymentArchitecture());
        addImplementationMultiValueElement(elements, "form4prop1", request.getAuthMethods());
        addImplementationMultiValueElement(elements, "form4prop3", request.getBusinessDirections());
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private JSONObject buildDataEaseImplementationContent(CreateImplementationRecordRequest request) throws Exception {
        JSONArray elements = new JSONArray();
        addImplementationElement(elements, "template", "DataEaseV2");
        addImplementationElement(elements, "deploymentTime", String.valueOf(parseDeploymentDateToEpochMillis(request.getDeploymentDate())));
        addImplementationElement(elements, "deploymentMethod", request.getDeploymentMethod());
        addImplementationElement(elements, "version", request.getVersion());
        addImplementationElement(elements, "backupMethod", request.getBackupMethod());
        addImplementationElement(elements, "form1prop1", request.getDataEaseDatabase());
        addImplementationElement(elements, "form1prop2", request.getDorisUsage());
        addImplementationElement(elements, "form1prop3", request.getDeploymentArchitecture());
        addImplementationElement(elements, "form4prop1", request.getDataSourceType());
        addImplementationElement(elements, "form4prop2", request.getDataScale());
        addImplementationMultiValueElement(elements, "form4prop5", request.getAuthMethods());
        addImplementationElement(elements, "form4prop6", request.getEmbeddedMode());
        addImplementationElement(elements, "form4prop7", request.getCustomerJoined());
        addImplementationElement(elements, "form4prop3", request.getAnalysisDirection());
        addImplementationElement(elements, "form4prop4", nullToEmpty(trimToNull(request.getCustomerFocus())));
        addImplementationElement(elements, "form3prop1", nullToEmpty(trimToNull(request.getRemainingIssues())));
        addImplementationElement(elements, "form3prop2", nullToEmpty(trimToNull(request.getRemark())));
        JSONObject content = new JSONObject();
        content.put("elements", elements);
        return content;
    }

    private void addImplementationElement(JSONArray elements, String title, String value) {
        JSONObject element = new JSONObject();
        element.put("title", title);
        JSONObject contentMap = new JSONObject();
        contentMap.put("value1", nullToEmpty(value));
        element.put("contentMap", contentMap);
        elements.add(element);
    }

    private void addImplementationMultiValueElement(JSONArray elements, String title, List<String> values) {
        JSONObject element = new JSONObject();
        element.put("title", title);
        JSONObject contentMap = new JSONObject();
        int index = 1;
        if (values != null) {
            for (String value : values) {
                String normalized = trimToNull(value);
                if (normalized == null) {
                    continue;
                }
                contentMap.put("value" + index, normalized);
                index++;
            }
        }
        if (contentMap.isEmpty()) {
            contentMap.put("value1", "");
        }
        element.put("contentMap", contentMap);
        elements.add(element);
    }

    private long parseDeploymentDateToEpochMillis(String deploymentDate) throws Exception {
        try {
            return LocalDate.parse(deploymentDate).atStartOfDay(ZONE_SHANGHAI).toInstant().toEpochMilli();
        } catch (Exception e) {
            throw new Exception("部署日期格式不正确");
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizeLegacyDeploymentTime(String value) {
        String normalized = trimToNull(value);
        if ("1970-01-01".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        String firstTrimmed = trimToNull(first);
        if (firstTrimmed != null) {
            return firstTrimmed;
        }
        return trimToNull(second);
    }

    private ImplementationContentSnapshot parseImplementationContentSnapshot(String rawContent) {
        ImplementationContentSnapshot snapshot = new ImplementationContentSnapshot();
        if (rawContent == null || rawContent.isBlank()) {
            return snapshot;
        }
        try {
            JSONObject contentJson = JSONObject.parseObject(rawContent);
            JSONArray elements = contentJson.getJSONArray("elements");
            if (elements == null) {
                return snapshot;
            }
            for (int i = 0; i < elements.size(); i++) {
                JSONObject element = elements.getJSONObject(i);
                if (element == null) {
                    continue;
                }
                String title = trimToNull(element.getString("title"));
                if (title == null) {
                    continue;
                }
                JSONObject contentMap = element.getJSONObject("contentMap");
                List<String> values = extractContentMapValues(contentMap);
                String value = values.isEmpty() ? null : values.get(0);
                if (value == null) {
                    continue;
                }
                switch (title) {
                    case "template" -> snapshot.template = value;
                    case "deploymentTime" -> snapshot.deploymentTime = formatEpochMillisDate(value);
                    case "deploymentMethod" -> snapshot.deploymentMethod = value;
                    case "version" -> snapshot.version = value;
                    case "backupMethod" -> snapshot.backupMethod = value;
                    case "virtualizationType", "form1prop4" -> snapshot.virtualizationType = value;
                    case "form1prop11" -> snapshot.deploymentArchitecture = value;
                    case "form1prop3" -> snapshot.assetCount = value;
                    case "form1prop8" -> snapshot.applicationServer = value;
                    case "form1prop5" -> snapshot.databaseExternal = value;
                    case "form1prop6" -> snapshot.redisExternal = value;
                    case "form1prop9" -> snapshot.databaseSync = value;
                    case "form1prop7" -> snapshot.sharedNfs = value;
                    case "form1prop1", "assetTypes" -> {
                        if (isTemplate(snapshot.template, "JumpServer")) {
                            snapshot.assetTypes = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "MaxKB")) {
                            snapshot.deploymentArchitecture = value;
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.dataEaseDatabase = value;
                        }
                    }
                    case "form1prop2" -> {
                        if (isTemplate(snapshot.template, "JumpServer")) {
                            snapshot.assetTypes = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.dorisUsage = value;
                        }
                    }
                    case "form4prop1" -> {
                        if (isTemplate(snapshot.template, "MaxKB")) {
                            snapshot.authMethods = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.dataSourceType = value;
                        }
                    }
                    case "form4prop2" -> snapshot.dataScale = value;
                    case "form4prop3" -> {
                        if (isTemplate(snapshot.template, "MaxKB")) {
                            snapshot.businessDirections = String.join("、", values);
                        } else if (isTemplate(snapshot.template, "DataEase")) {
                            snapshot.analysisDirection = value;
                        }
                    }
                    case "form4prop4" -> snapshot.customerFocus = value;
                    case "form4prop5" -> snapshot.authMethods = String.join("、", values);
                    case "form4prop6" -> snapshot.embeddedMode = value;
                    case "form4prop7" -> snapshot.customerJoined = value;
                    case "form1prop10", "customerFocus" -> snapshot.customerFocus = value;
                    case "deploymentRecord" -> snapshot.deploymentRecord = value;
                    case "remainingIssues", "form3prop1" -> snapshot.remainingIssues = value;
                    case "remark", "form3prop2" -> snapshot.remark = value;
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("parseImplementationContentSnapshot failed, err={}", e.getMessage());
        }
        splitLegacyDeploymentRecordSections(snapshot);
        return snapshot;
    }

    private void splitLegacyDeploymentRecordSections(ImplementationContentSnapshot snapshot) {
        String deploymentRecord = trimToNull(snapshot.deploymentRecord);
        if (deploymentRecord == null) {
            return;
        }

        int remainingIndex = deploymentRecord.indexOf("\n\n遗留问题：");
        int remarkIndex = deploymentRecord.indexOf("\n\n备注：");
        if (remainingIndex < 0 && remarkIndex < 0) {
            return;
        }

        int firstSectionIndex = remainingIndex >= 0 ? remainingIndex : remarkIndex;
        if (remainingIndex >= 0 && remarkIndex >= 0) {
            firstSectionIndex = Math.min(remainingIndex, remarkIndex);
        }
        snapshot.deploymentRecord = deploymentRecord.substring(0, firstSectionIndex).trim();

        if (remainingIndex >= 0) {
            int remainingValueStart = remainingIndex + "\n\n遗留问题：".length();
            int remainingValueEnd = remarkIndex > remainingIndex ? remarkIndex : deploymentRecord.length();
            if (snapshot.remainingIssues == null) {
                snapshot.remainingIssues = trimToNull(deploymentRecord.substring(remainingValueStart, remainingValueEnd));
            }
        }
        if (remarkIndex >= 0 && snapshot.remark == null) {
            int remarkValueStart = remarkIndex + "\n\n备注：".length();
            snapshot.remark = trimToNull(deploymentRecord.substring(remarkValueStart));
        }
    }

    private String formatEpochMillisDate(String value) {
        try {
            long epochMillis = Long.parseLong(value);
            if (epochMillis <= 0) {
                return null;
            }
            return Instant.ofEpochMilli(epochMillis).atZone(ZONE_SHANGHAI).toLocalDate().toString();
        } catch (Exception ignored) {
            return trimToNull(value);
        }
    }

    private List<String> extractContentMapValues(JSONObject contentMap) {
        List<String> values = new ArrayList<>();
        if (contentMap == null || contentMap.isEmpty()) {
            return values;
        }
        List<Map.Entry<String, Object>> entries = new ArrayList<>(contentMap.entrySet());
        entries.sort((left, right) -> extractContentMapOrder(left.getKey()) - extractContentMapOrder(right.getKey()));
        for (Map.Entry<String, Object> entry : entries) {
            String normalized = trimToNull(entry.getValue() == null ? null : entry.getValue().toString());
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return values;
    }

    private int extractContentMapOrder(String key) {
        if (key == null) {
            return Integer.MAX_VALUE;
        }
        if (key.startsWith("value")) {
            try {
                return Integer.parseInt(key.substring(5));
            } catch (Exception ignored) {
                return Integer.MAX_VALUE - 1;
            }
        }
        return Integer.MAX_VALUE;
    }

    private String buildImplementationDisplayContent(ImplementationContentSnapshot snapshot, String rawContent) {
        List<String> sections = new ArrayList<>();
        if (isTemplate(snapshot.template, "JumpServer")) {
            addImplementationDisplayLine(sections, "纳管资产类型", snapshot.assetTypes);
            addImplementationDisplayLine(sections, "管理资产数", snapshot.assetCount);
            addImplementationDisplayLine(sections, "虚拟化类型", snapshot.virtualizationType);
            addImplementationDisplayLine(sections, "应用发布服务器", snapshot.applicationServer);
            addImplementationDisplayLine(sections, "是否涉及到数据同步", snapshot.databaseSync);
            addImplementationDisplayLine(sections, "数据库是否外置", snapshot.databaseExternal);
            addImplementationDisplayLine(sections, "Redis是否外置部署", snapshot.redisExternal);
            addImplementationDisplayLine(sections, "共享存储NFS", snapshot.sharedNfs);
            addImplementationDisplayLine(sections, "客户核心关注点", snapshot.customerFocus);
            addImplementationDisplayLine(sections, "部署架构", snapshot.deploymentArchitecture);
            addImplementationDisplayBlock(sections, "记录内容", snapshot.deploymentRecord);
        } else if (isTemplate(snapshot.template, "MaxKB")) {
            addImplementationDisplayLine(sections, "部署架构", snapshot.deploymentArchitecture);
            addImplementationDisplayLine(sections, "认证方式", snapshot.authMethods);
            addImplementationDisplayLine(sections, "业务方向", snapshot.businessDirections);
        } else if (isTemplate(snapshot.template, "DataEase")) {
            addImplementationDisplayLine(sections, "备份方式", snapshot.backupMethod);
            addImplementationDisplayLine(sections, "数据库配置", snapshot.dataEaseDatabase);
            addImplementationDisplayLine(sections, "Doris配置", snapshot.dorisUsage);
            addImplementationDisplayLine(sections, "部署架构", snapshot.deploymentArchitecture);
            addImplementationDisplayLine(sections, "数据源类型", snapshot.dataSourceType);
            addImplementationDisplayLine(sections, "数据量规模", snapshot.dataScale);
            addImplementationDisplayLine(sections, "认证方式", snapshot.authMethods);
            addImplementationDisplayLine(sections, "嵌入方式", snapshot.embeddedMode);
            addImplementationDisplayLine(sections, "客户接入状态", snapshot.customerJoined);
            addImplementationDisplayLine(sections, "分析及展示方向", snapshot.analysisDirection);
            addImplementationDisplayLine(sections, "客户核心关注点", snapshot.customerFocus);
        }
        addImplementationDisplayBlock(sections, "遗留问题", snapshot.remainingIssues);
        addImplementationDisplayBlock(sections, "备注", snapshot.remark);

        if (sections.isEmpty()) {
            return rawContent;
        }
        return String.join("\n\n", sections);
    }

    private void addImplementationDisplayLine(List<String> sections, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        sections.add(label + "：" + normalized);
    }

    private void addImplementationDisplayBlock(List<String> sections, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return;
        }
        sections.add(label + "：\n" + normalized);
    }

    private static final class SubscriptionContext {
        private Long subscriptionId;
        private Long clientId;
        private String clientName;
        private String contractNumber;
        private Long productId;
        private String productName;
        private String serviceTypeName;
        private String regionId;
        private String subscriptionStartDate;
        private String supportEndDate;
    }

    private static final class ImplementationContentSnapshot {
        private String template;
        private String deploymentTime;
        private String deploymentMethod;
        private String version;
        private String backupMethod;
        private String assetTypes;
        private String assetCount;
        private String virtualizationType;
        private String applicationServer;
        private String databaseSync;
        private String databaseExternal;
        private String redisExternal;
        private String sharedNfs;
        private String customerFocus;
        private String deploymentArchitecture;
        private String deploymentRecord;
        private String authMethods;
        private String businessDirections;
        private String dataEaseDatabase;
        private String dorisUsage;
        private String dataSourceType;
        private String dataScale;
        private String embeddedMode;
        private String customerJoined;
        private String analysisDirection;
        private String remainingIssues;
        private String remark;
    }

    private boolean isTemplate(String template, String productKeyword) {
        if (template == null || productKeyword == null) {
            return false;
        }
        return template.toUpperCase(Locale.ROOT).contains(productKeyword.toUpperCase(Locale.ROOT));
    }

    private boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        String v = value.toLowerCase(Locale.ROOT);
        return v.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    private String resolveRegionId(Long clientId, String extChatId) {
        // 优先按订阅数据读取 region_id（你的库字段为 support_subscription.region_id）
        if (extChatId != null && !extChatId.isEmpty()) {
            try {
                String subRegionSql = "SELECT ss.region_id " +
                        "FROM support_subscription ss " +
                        "INNER JOIN group_chat gc ON gc.name = ss.group_chat_name " +
                        "WHERE gc.ext_chat_id = ? " +
                        "AND (? IS NULL OR ss.client_id = ?) " +
                        "AND ss.region_id IS NOT NULL " +
                        "ORDER BY ss.support_end_date DESC LIMIT 1";
                var subRegionResult = com.util.JdbcUtils.query(subRegionSql, extChatId, clientId, clientId);
                if (!subRegionResult.isEmpty() && subRegionResult.get(0)[0] != null) {
                    String regionId = subRegionResult.get(0)[0].toString();
                    LOGGER.info("resolveRegionId by subscription success extChatId={}, clientId={}, regionId={}",
                            extChatId, clientId, regionId);
                    return regionId;
                }
            } catch (Exception ignored) {
                // ignore and fallback
            }
        }

        // 优先按客户历史维护记录
        if (clientId != null) {
            try {
                String regionSql = "SELECT smr.region_id " +
                        "FROM support_maintenance_record smr " +
                        "WHERE smr.client_id = ? AND smr.region_id IS NOT NULL AND smr.region_id != '' " +
                        "ORDER BY smr.create_time DESC LIMIT 1";
                var regionResult = com.util.JdbcUtils.query(regionSql, clientId);
                if (!regionResult.isEmpty() && regionResult.get(0)[0] != null) {
                    return regionResult.get(0)[0].toString();
                }
            } catch (Exception ignored) {
                // ignore and fallback
            }
        }

        // 次选按群聊对应的维护记录
        if (extChatId != null && !extChatId.isEmpty()) {
            try {
                String regionSqlByChat = "SELECT smr.region_id " +
                        "FROM support_maintenance_record smr " +
                        "WHERE smr.client_id IN ( " +
                        "    SELECT DISTINCT ss.client_id " +
                        "    FROM group_chat gc " +
                        "    INNER JOIN support_subscription ss ON gc.name = ss.group_chat_name " +
                        "    WHERE gc.ext_chat_id = ? " +
                        ") " +
                        "AND smr.region_id IS NOT NULL AND smr.region_id != '' " +
                        "ORDER BY smr.create_time DESC LIMIT 1";
                var regionResultByChat = com.util.JdbcUtils.query(regionSqlByChat, extChatId);
                if (!regionResultByChat.isEmpty() && regionResultByChat.get(0)[0] != null) {
                    return regionResultByChat.get(0)[0].toString();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }

        return null;
    }

    public List<String> getStaffList(String userId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            // 查询当前用户的部门ID
            String currentUserSql = "SELECT sd.ext_department_id FROM staff s " +
                    "LEFT JOIN staff_department sd ON s.id = sd.staff_id " +
                    "WHERE s.ext_id = ? LIMIT 1";
            var currentUserResult = com.util.JdbcUtils.query(currentUserSql, userId);

            Long currentUserDeptId = null;
            if (!currentUserResult.isEmpty() && currentUserResult.get(0)[0] != null) {
                currentUserDeptId = Long.parseLong(currentUserResult.get(0)[0].toString());
            }

            // 获取当前用户所属区域的所有部门ID
            List<Long> regionDeptIds = getRegionDeptIds(currentUserDeptId);
            if (regionDeptIds == null || regionDeptIds.isEmpty()) {
                // 如果找不到区域，返回空列表
                return new ArrayList<>();
            }

            // 构建 IN 子句
            String deptIdsStr = regionDeptIds.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            // 查询同区域所有员工
            String sql = "SELECT DISTINCT s.name FROM staff s " +
                    "INNER JOIN staff_department sd ON s.id = sd.staff_id " +
                    "WHERE sd.ext_department_id IN (" + deptIdsStr + ") " +
                    "AND s.name IS NOT NULL AND s.name != '' " +
                    "ORDER BY s.name";
            var result = com.util.JdbcUtils.query(sql);
            List<String> staffList = new ArrayList<>();

            for (Object[] row : result) {
                if (row[0] != null) {
                    staffList.add(row[0].toString());
                }
            }

            return staffList;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private List<String> fetchProductVersions(Long productId) throws Exception {
        List<String> versions = new ArrayList<>();
        String url = cscrmBaseUrl + cscrmApiPath + "/support-info/products/" + productId + "/versions";
        LOGGER.info("fetchProductVersions start productId={}, url={}", productId, url);
        String response = requestWithDnsRetry(url);
        JSONObject responseJson = JSONObject.parseObject(response);
        Integer code = responseJson.getInteger("code");
        if (code != null && code != 0) {
            LOGGER.warn("fetchProductVersions non-zero code productId={}, code={}, msg={}",
                    productId, code, responseJson.getString("msg"));
            return versions;
        }

        Object dataObj = responseJson.get("data");
        JSONArray list = null;
        if (dataObj instanceof JSONArray) {
            list = (JSONArray) dataObj;
        } else if (dataObj instanceof JSONObject) {
            JSONObject dataJson = (JSONObject) dataObj;
            if (dataJson.get("items") instanceof JSONArray) {
                list = dataJson.getJSONArray("items");
            }
        }
        if (list == null) {
            return versions;
        }

        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof String) {
                versions.add((String) item);
            } else if (item instanceof JSONObject) {
                JSONObject itemJson = (JSONObject) item;
                String version = itemJson.getString("version");
                if (version == null || version.isEmpty()) {
                    version = itemJson.getString("name");
                }
                if (version != null && !version.isEmpty()) {
                    versions.add(version);
                }
            }
        }
        LOGGER.info("fetchProductVersions done productId={}, count={}", productId, versions.size());
        return versions;
    }

    private String requestWithDnsRetry(String url) throws Exception {
        Exception last = null;
        int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return HttpClientUtil.getRequestWithApiKey(url, cscrmApiKey);
            } catch (Exception e) {
                last = e;
                if (!containsUnknownHost(e) || attempt >= maxAttempts) {
                    throw e;
                }
                LOGGER.warn("requestWithDnsRetry unknown host, retrying attempt={}/{}, url={}", attempt, maxAttempts, url);
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    private boolean containsUnknownHost(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof UnknownHostException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    public List<Long> resolveVersionProductIds(Long productId, String extChatId) {
        String alias = getProductAliasByExtChatId(extChatId);
        LOGGER.info("resolveVersionProductIds input productId={}, extChatId={}, alias={}", productId, extChatId, alias);

        if ("MK".equals(alias)) {
            return new ArrayList<>(MAXKB_PRODUCT_IDS);
        }
        if ("DE".equals(alias)) {
            return new ArrayList<>(DATAEASE_PRODUCT_IDS);
        }
        if (productId != null && MAXKB_PRODUCT_IDS.contains(productId)) {
            return new ArrayList<>(MAXKB_PRODUCT_IDS);
        }
        if (productId != null && DATAEASE_PRODUCT_IDS.contains(productId)) {
            return new ArrayList<>(DATAEASE_PRODUCT_IDS);
        }
        if (productId == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(productId));
    }

    public List<String> getProductVersions(Long productId, String extChatId) throws Exception {
        List<Long> productIds = resolveVersionProductIds(productId, extChatId);
        if (productIds.isEmpty()) {
            throw new Exception("productId 不能为空");
        }
        LOGGER.info("getProductVersions aggregate start extChatId={}, baseProductId={}, productIds={}", extChatId, productId, productIds);

        Set<String> merged = new LinkedHashSet<>();
        for (Long pid : productIds) {
            merged.addAll(fetchProductVersions(pid));
        }

        List<String> sorted = new ArrayList<>(merged);
        sorted.sort(new VersionDescComparator());
        LOGGER.info("getProductVersions aggregate done extChatId={}, productIds={}, mergedCount={}", extChatId, productIds, sorted.size());
        return sorted;
    }

    public Long getProductIdByExtChatId(String extChatId) {
        if (extChatId == null || extChatId.isEmpty()) {
            return null;
        }
        try {
            CustomerData customerData = getCustomerData(extChatId);
            Long productId = customerData != null ? customerData.getProductId() : null;
            LOGGER.info("getProductIdByExtChatId extChatId={}, productId={}", extChatId, productId);
            return productId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String getProductAliasByExtChatId(String extChatId) {
        if (extChatId == null || extChatId.isEmpty()) {
            return null;
        }
        try {
            String sql = "SELECT name FROM group_chat WHERE ext_chat_id = ? LIMIT 1";
            var result = com.util.JdbcUtils.query(sql, extChatId);
            if (result.isEmpty() || result.get(0)[0] == null) {
                return null;
            }
            String chatName = result.get(0)[0].toString().toUpperCase();
            if (chatName.contains("MK") || chatName.contains("MAXKB")) {
                return "MK";
            }
            if (chatName.contains("DE") || chatName.contains("DATAEASE")) {
                return "DE";
            }
            if (chatName.contains("JS") || chatName.contains("JUMPSERVER")) {
                return "JS";
            }
            if (chatName.contains("SQLBOT")) {
                return "SQLBOT";
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public ProductVersionSnapshot getProductVersionSnapshot(String extChatId) {
        ProductVersionSnapshot snapshot = new ProductVersionSnapshot();
        if (extChatId == null || extChatId.isEmpty()) {
            return snapshot;
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            String aliasCode = getProductAliasByExtChatId(extChatId);
            String productAlias = toStandardProductAlias(aliasCode);
            snapshot.setProductAlias(productAlias);

            VersionCandidate implementationCandidate = getLatestImplementationVersion(extChatId);
            if (implementationCandidate != null) {
                snapshot.setVersion(implementationCandidate.version);
                snapshot.setVersionTs(implementationCandidate.versionTs);
                snapshot.setSource("implementation");
            }
            return snapshot;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    private String toStandardProductAlias(String aliasCode) {
        if (aliasCode == null || aliasCode.isEmpty()) {
            return null;
        }
        return switch (aliasCode) {
            case "JS" -> "JumpServer";
            case "MK" -> "MaxKB";
            case "DE" -> "DataEase";
            case "SQLBOT" -> "SQLBot";
            default -> null;
        };
    }

    private VersionCandidate pickVersionCandidate(VersionCandidate serviceCandidate, VersionCandidate implementationCandidate) {
        if (serviceCandidate == null || serviceCandidate.versionTs == null) {
            return implementationCandidate;
        }
        if (implementationCandidate == null || implementationCandidate.versionTs == null) {
            return serviceCandidate;
        }
        if (isSameDay(serviceCandidate.versionTs, implementationCandidate.versionTs)) {
            return serviceCandidate;
        }
        return implementationCandidate.versionTs > serviceCandidate.versionTs ? implementationCandidate : serviceCandidate;
    }

    private boolean isSameDay(Long a, Long b) {
        if (a == null || b == null) {
            return false;
        }
        LocalDate ad = Instant.ofEpochMilli(a).atZone(ZONE_SHANGHAI).toLocalDate();
        LocalDate bd = Instant.ofEpochMilli(b).atZone(ZONE_SHANGHAI).toLocalDate();
        return ad.equals(bd);
    }

    private VersionCandidate getLatestImplementationVersion(String extChatId) {
        String sql = "WITH chat_product AS ( " +
                "    SELECT gc.name, " +
                "           CASE " +
                "               WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                "               WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                "               WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                "               WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBOT' " +
                "               ELSE NULL " +
                "           END AS product " +
                "    FROM group_chat gc " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "SELECT sm.version, COALESCE(sm.deployment_time, sm.create_time) AS version_ts " +
                "FROM support_maintenance sm " +
                "INNER JOIN support_subscription ss ON sm.subscription_id = ss.id " +
                "CROSS JOIN chat_product cp " +
                "WHERE ss.client_id IN ( " +
                "    SELECT DISTINCT ss2.client_id " +
                "    FROM group_chat gc " +
                "    INNER JOIN support_subscription ss2 ON gc.name = ss2.group_chat_name " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "AND (cp.product IS NULL OR UPPER(sm.template) LIKE CONCAT('%', UPPER(cp.product), '%')) " +
                "AND sm.version IS NOT NULL AND sm.version != '' " +
                "ORDER BY version_ts DESC, sm.create_time DESC LIMIT 1";

        var result = com.util.JdbcUtils.query(sql, extChatId, extChatId);
        if (result.isEmpty()) {
            return null;
        }
        Object[] row = result.get(0);
        if (row[0] == null || row[1] == null) {
            return null;
        }
        VersionCandidate candidate = new VersionCandidate();
        candidate.version = row[0].toString();
        candidate.versionTs = toLong(row[1]);
        if (candidate.versionTs == null) {
            return null;
        }
        return candidate;
    }

    private VersionCandidate getLatestServiceVersion(String extChatId) {
        String sql = "WITH chat_product AS ( " +
                "    SELECT gc.name, " +
                "           CASE " +
                "               WHEN UPPER(gc.name) LIKE '%JS%' OR UPPER(gc.name) LIKE '%JUMPSERVER%' THEN 'JumpServer' " +
                "               WHEN UPPER(gc.name) LIKE '%MK%' OR UPPER(gc.name) LIKE '%MAXKB%' THEN 'MaxKB' " +
                "               WHEN UPPER(gc.name) LIKE '%DE%' OR UPPER(gc.name) LIKE '%DATAEASE%' THEN 'DataEase' " +
                "               WHEN UPPER(gc.name) LIKE '%SQLBOT%' THEN 'SQLBot' " +
                "               ELSE NULL " +
                "           END AS product " +
                "    FROM group_chat gc " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "SELECT smr.maintenance_version, smr.maintenance_time " +
                "FROM support_maintenance_record smr " +
                "CROSS JOIN chat_product cp " +
                "WHERE smr.client_id IN ( " +
                "    SELECT DISTINCT ss.client_id " +
                "    FROM group_chat gc " +
                "    INNER JOIN support_subscription ss ON gc.name = ss.group_chat_name " +
                "    WHERE gc.ext_chat_id = ? " +
                ") " +
                "AND ( " +
                "    (cp.product = 'MaxKB' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%MAXKB%' " +
                "    )) " +
                "    OR " +
                "    (cp.product = 'JumpServer' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%JUMPSERVER%' " +
                "    )) " +
                "    OR " +
                "    (cp.product = 'DataEase' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%DATAEASE%' " +
                "    )) " +
                "    OR " +
                "    (cp.product = 'SQLBot' AND smr.product_id IN ( " +
                "        SELECT DISTINCT product_id FROM support_product_service WHERE UPPER(name) LIKE '%SQLBOT%' " +
                "    )) " +
                "    OR " +
                "    smr.product_id IS NULL OR smr.product_id = 0 " +
                ") " +
                "AND smr.maintenance_version IS NOT NULL AND smr.maintenance_version != '' " +
                "ORDER BY smr.maintenance_time DESC, smr.create_time DESC LIMIT 1";

        var result = com.util.JdbcUtils.query(sql, extChatId, extChatId);
        if (result.isEmpty()) {
            return null;
        }
        Object[] row = result.get(0);
        if (row[0] == null || row[1] == null) {
            return null;
        }
        VersionCandidate candidate = new VersionCandidate();
        candidate.version = row[0].toString();
        candidate.versionTs = toLong(row[1]);
        if (candidate.versionTs == null) {
            return null;
        }
        return candidate;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static class VersionCandidate {
        private String version;
        private Long versionTs;
    }

    private static class VersionDescComparator implements Comparator<String> {
        private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

        @Override
        public int compare(String a, String b) {
            List<Integer> av = extractNumbers(a);
            List<Integer> bv = extractNumbers(b);
            int size = Math.max(av.size(), bv.size());
            for (int i = 0; i < size; i++) {
                int ai = i < av.size() ? av.get(i) : 0;
                int bi = i < bv.size() ? bv.get(i) : 0;
                if (ai != bi) {
                    return Integer.compare(bi, ai);
                }
            }
            return b.compareToIgnoreCase(a);
        }

        private List<Integer> extractNumbers(String version) {
            List<Integer> numbers = new ArrayList<>();
            if (version == null) {
                return numbers;
            }
            Matcher matcher = NUMBER_PATTERN.matcher(version);
            while (matcher.find()) {
                try {
                    numbers.add(Integer.parseInt(matcher.group()));
                } catch (NumberFormatException ignored) {
                    // ignore malformed part
                }
            }
            return numbers;
        }
    }

    // 判断是否跨团队
    private boolean isCrossTeam(Long deptId1, Long deptId2) {
        if (deptId1 == null || deptId2 == null) {
            return false;
        }

        // 首先检查是否在同一区域
        if (!isSameRegion(deptId1, deptId2)) {
            // 不在同一区域，不属于跨团队（因为只有同区才有跨团队的概念）
            return false;
        }

        // 在同一区域内，检查特殊规则
        boolean dept1IsVirtual = VIRTUAL_DEPT_IDS.contains(deptId1);
        boolean dept2IsVirtual = VIRTUAL_DEPT_IDS.contains(deptId2);
        boolean dept1IsOnline = ONLINE_DEPT_IDS.contains(deptId1);
        boolean dept2IsOnline = ONLINE_DEPT_IDS.contains(deptId2);

        // 虚拟账号和线上不跨团队
        if ((dept1IsVirtual && dept2IsOnline) || (dept2IsVirtual && dept1IsOnline)) {
            return false;
        }

        // 虚拟账号和虚拟账号不跨团队
        if (dept1IsVirtual && dept2IsVirtual) {
            return false;
        }

        // 线上和线上不跨团队
        if (dept1IsOnline && dept2IsOnline) {
            return false;
        }

        // 其他情况（包括虚拟账号和线下、线上和线下、线下和线下）都是跨团队
        return true;
    }

    // 判断两个部门是否在同一区域
    private boolean isSameRegion(Long deptId1, Long deptId2) {
        if (deptId1 == null || deptId2 == null) {
            return false;
        }

        // 检查是否都在东区
        if (EAST_REGION_DEPT_IDS.contains(deptId1) && EAST_REGION_DEPT_IDS.contains(deptId2)) {
            return true;
        }
        // 检查是否都在北区
        if (NORTH_REGION_DEPT_IDS.contains(deptId1) && NORTH_REGION_DEPT_IDS.contains(deptId2)) {
            return true;
        }
        // 检查是否都在南区
        if (SOUTH_REGION_DEPT_IDS.contains(deptId1) && SOUTH_REGION_DEPT_IDS.contains(deptId2)) {
            return true;
        }
        return false;
    }

    // 获取部门所属区域的所有部门ID
    private List<Long> getRegionDeptIds(Long deptId) {
        if (deptId == null) {
            return new ArrayList<>();
        }

        if (EAST_REGION_DEPT_IDS.contains(deptId)) {
            return EAST_REGION_DEPT_IDS;
        }
        if (NORTH_REGION_DEPT_IDS.contains(deptId)) {
            return NORTH_REGION_DEPT_IDS;
        }
        if (SOUTH_REGION_DEPT_IDS.contains(deptId)) {
            return SOUTH_REGION_DEPT_IDS;
        }
        return new ArrayList<>();
    }
}
