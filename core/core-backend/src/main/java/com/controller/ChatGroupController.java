package com.controller;

import com.model.ApiResponse;
import com.model.CustomerData;
import com.model.MaintenanceRecord;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import com.service.ChatGroupService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/chat-group")
public class ChatGroupController {

    @Resource
    private ChatGroupService chatGroupService;

    // 获取客户数据接口
    @GetMapping("/customer-data")
    public ApiResponse<CustomerData> getCustomerData(@RequestParam String extChatId) {
        try {
            CustomerData data = chatGroupService.getCustomerData(extChatId);
            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("获取客户数据失败: " + e.getMessage());
        }
    }

    // 获取实施记录接口
    @GetMapping("/maintenance-records")
    public ApiResponse<List<MaintenanceRecord>> getMaintenanceRecords(@RequestParam String extChatId) {
        try {
            List<MaintenanceRecord> records = chatGroupService.getMaintenanceRecords(extChatId);
            return ApiResponse.success(records);
        } catch (Exception e) {
            return ApiResponse.error("获取实施记录失败: " + e.getMessage());
        }
    }

    // 获取维护记录接口
    @GetMapping("/service-records")
    public ApiResponse<List<ServiceRecord>> getServiceRecords(@RequestParam String extChatId) {
        try {
            List<ServiceRecord> records = chatGroupService.getServiceRecords(extChatId);
            return ApiResponse.success(records);
        } catch (Exception e) {
            return ApiResponse.error("获取维护记录失败: " + e.getMessage());
        }
    }

    // 获取工单接口
    @GetMapping("/tickets")
    public ApiResponse<List<Ticket>> getTickets(@RequestParam String extChatId) {
        try {
            List<Ticket> tickets = chatGroupService.getTickets(extChatId);
            return ApiResponse.success(tickets);
        } catch (Exception e) {
            return ApiResponse.error("获取工单失败: " + e.getMessage());
        }
    }

    // 获取工单日志接口
    @GetMapping("/ticket-logs")
    public ApiResponse<List<TicketLog>> getTicketLogs(@RequestParam Long ticketId) {
        try {
            List<TicketLog> logs = chatGroupService.getTicketLogs(ticketId);
            return ApiResponse.success(logs);
        } catch (Exception e) {
            return ApiResponse.error("获取工单日志失败: " + e.getMessage());
        }
    }
}

