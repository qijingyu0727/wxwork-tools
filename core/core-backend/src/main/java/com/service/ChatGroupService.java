package com.service;

import org.springframework.stereotype.Service;
import com.model.CustomerData;
import com.model.MaintenanceRecord;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import com.model.request.UpdateTicketRequest;
import com.alibaba.fastjson.JSONObject;
import com.util.HttpClientUtil;
import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatGroupService {

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

    public CustomerData getCustomerData(String extChatId) {
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "with ticket_count as ( " +
                    "     select room_id, " +
                    "            sum(if(resolved, 0, 1)) not_resolved_ticket_count, " +
                    "            count(1) all_ticket_count, " +
                    "            sum(if(status = 3, 1, 0)) critical_ticket_count " +
                    "     from chat_analysis_tickets " +
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
                    "        subscription_info.subscription_end_date, " +
                    "        ticket_count.not_resolved_ticket_count, " +
                    "        ticket_count.all_ticket_count, " +
                    "        ticket_count.critical_ticket_count, " +
                    "        0 all_issue_count, " +
                    "        0 not_resolved_issue_count, " +
                    "        0 all_bug_count, " +
                    "        0 not_resolved_bug_count " +
                    " from group_chat " +
                    " left join subscription_info on group_chat.name = subscription_info.group_chat_name " +
                    " left join support_client on subscription_info.client_id = support_client.id " +
                    " left  join ticket_count on ticket_count.room_id = group_chat.ext_chat_id " +
                    " where ext_chat_id = ?";

            var result = com.util.JdbcUtils.query(sql, extChatId);
            CustomerData data = new CustomerData();

            if (!result.isEmpty()) {
                Object[] row = result.get(0);
                data.setName(row[0] != null ? row[0].toString() : null);
                data.setSubscriptionEndDate(row[1] != null ? row[1].toString() : null);
                data.setIsAccepted("无需验收");
                data.setNotResolvedTicketCount(row[2] != null ? Integer.parseInt(row[2].toString()) : 0);
                data.setAllTicketCount(row[3] != null ? Integer.parseInt(row[3].toString()) : 0);
                data.setCriticalTicketCount(row[4] != null ? Integer.parseInt(row[4].toString()) : 0);
                data.setAllIssueCount(row[5] != null ? Integer.parseInt(row[5].toString()) : 0);
                data.setNotResolvedIssueCount(row[6] != null ? Integer.parseInt(row[6].toString()) : 0);
                data.setAllBugCount(row[7] != null ? Integer.parseInt(row[7].toString()) : 0);
                data.setNotResolvedBugCount(row[8] != null ? Integer.parseInt(row[8].toString()) : 0);
            }

            return data;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
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
        com.util.JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT cat.id, " +
                    "       cat.title, " +
                    "       cat.description, " +
                    "       cat.product, " +
                    "       cat.status, " +
                    "       cat.urgent, " +
                    "       cat.resolved, " +
                    "       cat.owner_name, " +
                    "       cat.issue_category, " +
                    "       cat.customer_sentiment, " +
                    "       DATE_FORMAT(cat.created_at, '%Y-%m-%d %H:%i:%s') as created_at, " +
                    "       DATE_FORMAT(cat.updated_at, '%Y-%m-%d %H:%i:%s') as updated_at " +
                    "FROM chat_analysis_tickets cat " +
                    "WHERE cat.room_id = ? AND cat.deleted_at IS NULL " +
                    "ORDER BY cat.created_at DESC";

            var result = com.util.JdbcUtils.query(sql, extChatId);
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
                ticket.setCreatedAt(row[10] != null ? row[10].toString() : null);
                ticket.setUpdatedAt(row[11] != null ? row[11].toString() : null);
                tickets.add(ticket);
            }

            return tickets;
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
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
            // 根据处理人姓名查询处理人ID
            String ownerSql = "SELECT s.ext_id FROM staff s WHERE s.name = ? LIMIT 1";
            var ownerResult = com.util.JdbcUtils.query(ownerSql, request.getOwnerName());

            String ownerId = null;
            if (!ownerResult.isEmpty() && ownerResult.get(0)[0] != null) {
                ownerId = ownerResult.get(0)[0].toString();
            } else {
                throw new Exception("未找到处理人: " + request.getOwnerName());
            }

            // 构建请求体
            JSONObject payload = new JSONObject();
            payload.put("id", request.getTicketId());
            payload.put("urgent", request.getUrgent());
            payload.put("customer_sentiment", request.getCustomerSentiment());
            payload.put("owner_name", request.getOwnerName());
            payload.put("owner_id", ownerId);
            payload.put("modified_by_id", modifiedById);
            payload.put("modified_by_name", modifiedByName);
            payload.put("comment", request.getComment());
            payload.put("status", request.getStatus());
            payload.put("resolved", request.getResolved());

            // 调用 CSCRM API
            String url = cscrmBaseUrl + cscrmApiPath + "/smart-tickets/tickets/" + request.getTicketId();
            String response = HttpClientUtil.putJSONWithApiKey(url, payload.toJSONString(), cscrmApiKey);

            JSONObject responseJson = JSONObject.parseObject(response);
            if (responseJson.getInteger("code") != 0) {
                throw new Exception("更新工单失败: " + responseJson.getString("msg"));
            }
        } finally {
            com.util.JdbcUtils.clearConfig();
        }
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
