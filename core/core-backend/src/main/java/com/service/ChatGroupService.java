package com.service;

import org.springframework.stereotype.Service;
import com.model.CustomerData;
import com.model.MaintenanceRecord;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatGroupService {

    public CustomerData getCustomerData(String extChatId) {
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
    }

    public List<MaintenanceRecord> getMaintenanceRecords(String extChatId) {
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
    }

    public List<ServiceRecord> getServiceRecords(String extChatId) {
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
    }

    public List<Ticket> getTickets(String extChatId) {
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
    }

    public List<TicketLog> getTicketLogs(Long ticketId) {
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
    }
}