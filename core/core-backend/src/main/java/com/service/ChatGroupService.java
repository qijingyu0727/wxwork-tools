package com.service;

import org.springframework.stereotype.Service;
import com.model.CustomerData;
import com.model.MaintenanceRecord;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import com.model.request.UpdateTicketRequest;
import com.model.request.CreateMaintenanceRecordRequest;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONArray;
import com.util.HttpClientUtil;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.UnknownHostException;

@Service
public class ChatGroupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGroupService.class);

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

    public CustomerData getCustomerData(String extChatId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
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

            var result = com.util.JdbcUtils.query(sql, extChatId);
            CustomerData data = new CustomerData();

            if (!result.isEmpty()) {
                Object[] row = result.get(0);
                data.setName(row[0] != null ? row[0].toString() : null);
                data.setClientId(row[1] != null ? Long.parseLong(row[1].toString()) : null);
                data.setSubscriptionEndDate(row[2] != null ? row[2].toString() : null);
                data.setIsAccepted("无需验收");
                data.setNotResolvedTicketCount(row[3] != null ? Integer.parseInt(row[3].toString()) : 0);
                data.setAllTicketCount(row[4] != null ? Integer.parseInt(row[4].toString()) : 0);
                data.setCriticalTicketCount(row[5] != null ? Integer.parseInt(row[5].toString()) : 0);
                data.setAllIssueCount(row[6] != null ? Integer.parseInt(row[6].toString()) : 0);
                data.setNotResolvedIssueCount(row[7] != null ? Integer.parseInt(row[7].toString()) : 0);
                data.setAllBugCount(row[8] != null ? Integer.parseInt(row[8].toString()) : 0);
                data.setNotResolvedBugCount(row[9] != null ? Integer.parseInt(row[9].toString()) : 0);
                fillCustomerProductMeta(extChatId, data);
            }

            return data;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
    }

    // 补齐客户产品/区域信息（优先订阅数据，次选群聊产品映射，再次选维护记录）
    private void fillCustomerProductMeta(String extChatId, CustomerData data) {
        if (data == null) {
            return;
        }
        LOGGER.info("fillCustomerProductMeta start extChatId={}, clientId={}", extChatId, data.getClientId());

        // 1) 如果 productId 为空，按群聊名称映射产品再去 support_product_service 取 product_id
        if (data.getProductId() == null) {
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
            }
        }

        // 2) 如果 productId 仍为空，从该群实时工单记录里的 product 名称反查 product_id
        if (data.getProductId() == null) {
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
            }
        }

        // 3) regionId 兜底取最近维护记录
        if ((data.getRegionId() == null || data.getRegionId().isEmpty()) && data.getClientId() != null) {
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
            }
        }
        LOGGER.info("fillCustomerProductMeta done extChatId={}, productId={}, regionId={}",
                extChatId, data.getProductId(), data.getRegionId());
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
                    "SELECT sm.id, " +
                    "       sm.status, " +
                    "       FROM_UNIXTIME(sm.deployment_time/1000, '%Y-%m-%d') as deployment_time, " +
                    "       sm.deployment_method, " +
                    "       sm.template, " +
                    "       sm.creator_name, " +
                    "       sm.version, " +
                    "       sm.content, " +
                    "       FROM_UNIXTIME(sm.create_time/1000, '%Y-%m-%d') as create_time " +
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
                    "ORDER BY sm.create_time DESC";

            var result = com.util.JdbcUtils.query(sql, extChatId, extChatId);
            List<MaintenanceRecord> records = new ArrayList<>();

            for (Object[] row : result) {
                MaintenanceRecord record = new MaintenanceRecord();
                record.setId(row[0] != null ? Long.parseLong(row[0].toString()) : null);
                record.setStatus(row[1] != null ? row[1].toString() : null);
                record.setDeploymentTime(row[2] != null ? row[2].toString() : null);
                record.setDeploymentMethod(row[3] != null ? row[3].toString() : null);
                record.setTemplate(row[4] != null ? row[4].toString() : null);
                record.setCreatorName(row[5] != null ? row[5].toString() : null);
                record.setVersion(row[6] != null ? row[6].toString() : null);
                record.setContent(row[7] != null ? row[7].toString() : null);
                record.setCreateTime(row[8] != null ? row[8].toString() : null);
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

        String ownerId = request.getOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = request.getEditorUserId();
        }
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = loginUserId;
        }
        if (ownerId == null || ownerId.isEmpty()) {
            throw new Exception("缺少提交人ID");
        }
        ownerId = resolveSupportUserId(ownerId);
        LOGGER.info("createMaintenanceRecord resolved ownerId={}", ownerId);

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

    // 维护记录接口需要 support_user.user_id（UUID）。当前登录态常见为企业微信 userid（如 QiJingYu），这里做自动映射。
    private String resolveSupportUserId(String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return candidate;
        }
        if (isUuid(candidate)) {
            return candidate;
        }

        com.util.JdbcUtils.setCscrmConfig();
        try {
            // 1) 直接按 support_user.username / name / email 兜底匹配
            try {
                String sql = "SELECT su.user_id FROM support_user su " +
                        "WHERE su.username = ? OR su.name = ? OR su.email = ? LIMIT 1";
                var result = com.util.JdbcUtils.query(sql, candidate, candidate, candidate);
                if (!result.isEmpty() && result.get(0)[0] != null) {
                    return result.get(0)[0].toString();
                }
            } catch (Exception ignored) {
                // ignore and fallback
            }

            // 2) 先从 staff 按 ext_id 找到姓名/邮箱，再映射 support_user.user_id
            try {
                String staffSql = "SELECT s.name, s.email FROM staff s WHERE s.ext_id = ? LIMIT 1";
                var staffResult = com.util.JdbcUtils.query(staffSql, candidate);
                if (!staffResult.isEmpty()) {
                    String name = staffResult.get(0)[0] != null ? staffResult.get(0)[0].toString() : null;
                    String email = staffResult.get(0)[1] != null ? staffResult.get(0)[1].toString() : null;

                    String supportSql = "SELECT su.user_id FROM support_user su " +
                            "WHERE (? IS NOT NULL AND su.email = ?) " +
                            "   OR (? IS NOT NULL AND su.name = ?) " +
                            "LIMIT 1";
                    var supportResult = com.util.JdbcUtils.query(supportSql, email, email, name, name);
                    if (!supportResult.isEmpty() && supportResult.get(0)[0] != null) {
                        return supportResult.get(0)[0].toString();
                    }
                }
            } catch (Exception ignored) {
                // ignore
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
        return candidate;
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
