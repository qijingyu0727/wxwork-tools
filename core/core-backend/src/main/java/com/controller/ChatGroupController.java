package com.controller;

import com.alibaba.fastjson.JSONObject;
import com.model.ApiResponse;
import com.model.CustomerData;
import com.model.MaintenanceRecord;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import com.model.request.UpdateTicketRequest;
import com.service.ChatGroupService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
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

    // 更新工单接口
    @PutMapping("/tickets/{ticketId}")
    public ApiResponse<Void> updateTicket(
            @PathVariable String ticketId,
            @RequestBody UpdateTicketRequest request,
            HttpSession session) {
        try {
            // 从 session 获取当前用户信息
            JSONObject userInfo = (JSONObject) session.getAttribute("login_user");
            if (userInfo == null) {
                return ApiResponse.error("用户未登录");
            }

            String userId = userInfo.getString("userid");
            String userName = userInfo.getString("name");

            if (userId == null || userName == null) {
                return ApiResponse.error("无法获取用户信息");
            }

            // 设置工单ID
            request.setTicketId(ticketId);

            // 调用 service 更新工单
            chatGroupService.updateTicket(request, userId, userName);

            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.error("更新工单失败: " + e.getMessage());
        }
    }

    // 获取员工列表接口
    @GetMapping("/staff-list")
    public ApiResponse<List<String>> getStaffList(HttpSession session) {
        try {
            // 从 session 获取当前用户信息
            JSONObject userInfo = (JSONObject) session.getAttribute("login_user");
            if (userInfo == null) {
                return ApiResponse.error("用户未登录");
            }
            String userId = userInfo.getString("userid");

            List<String> staffList = chatGroupService.getStaffList(userId);
            return ApiResponse.success(staffList);
        } catch (Exception e) {
            return ApiResponse.error("获取员工列表失败: " + e.getMessage());
        }
    }
}

