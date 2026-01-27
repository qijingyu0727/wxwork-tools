package com.service;

import org.springframework.stereotype.Service;
import com.model.CustomerData;

@Service
public class ChatGroupService {

    public CustomerData getCustomerData(String extChatId) {
        String sql = "with ticket_count as ( " +
                "     select room_id, " +
                "            sum(if(resolved, 0, 1)) not_resolved_ticket_count, " +
                "            count(1) all_ticket_count " +
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
                "        0 all_issue_count, " +
                "        0 not_resolved_issue_count, " +
                "        0 all_bug_count, " +
                "        0 not_resolved_bug_count " +
                " from group_chat " +
                " inner join subscription_info on group_chat.name = subscription_info.group_chat_name " +
                " inner join support_client on subscription_info.client_id = support_client.id " +
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
            data.setAllIssueCount(row[4] != null ? Integer.parseInt(row[4].toString()) : 0);
            data.setNotResolvedIssueCount(row[5] != null ? Integer.parseInt(row[5].toString()) : 0);
            data.setAllBugCount(row[6] != null ? Integer.parseInt(row[6].toString()) : 0);
            data.setNotResolvedBugCount(row[7] != null ? Integer.parseInt(row[7].toString()) : 0);
        }

        return data;
    }
}