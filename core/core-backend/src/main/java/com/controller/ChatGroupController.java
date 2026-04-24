package com.controller;

import com.alibaba.fastjson.JSONObject;
import com.model.AcceptanceStatusData;
import com.model.ApiResponse;
import com.model.CustomerData;
import com.model.ImplementationCreateContext;
import com.model.MaintenanceCreateContext;
import com.model.MaintenanceRecord;
import com.model.ServiceRecord;
import com.model.Ticket;
import com.model.TicketLog;
import com.model.request.UpdateTicketRequest;
import com.model.request.CreateMaintenanceRecordRequest;
import com.model.request.CreateImplementationRecordRequest;
import com.service.ChatGroupService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-group")
public class ChatGroupController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatGroupController.class);

    @Resource
    private ChatGroupService chatGroupService;

    private String getLoginUserId(HttpSession session) {
        JSONObject userInfo = (JSONObject) session.getAttribute("login_user");
        if (userInfo == null) {
            return null;
        }
        String userId = userInfo.getString("userid");
        if (userId == null) {
            userId = userInfo.getString("UserId");
        }
        if (userId == null) {
            userId = userInfo.getString("user_id");
        }
        return userId;
    }

    // 获取客户数据接口
    @GetMapping("/customer-data")
    public ApiResponse<CustomerData> getCustomerData(@RequestParam String extChatId) {
        try {
            CustomerData data = chatGroupService.getCustomerData(extChatId);
            LOGGER.info("customer-data extChatId={}, clientId={}, productId={}, regionId={}",
                    extChatId,
                    data != null ? data.getClientId() : null,
                    data != null ? data.getProductId() : null,
                    data != null ? data.getRegionId() : null);
            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("获取客户数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/acceptance-status")
    public ApiResponse<AcceptanceStatusData> getAcceptanceStatus(@RequestParam String extChatId) {
        try {
            return ApiResponse.success(chatGroupService.getAcceptanceStatus(extChatId));
        } catch (Exception e) {
            return ApiResponse.error("获取验收状态失败: " + e.getMessage());
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

    @GetMapping("/implementation-create-context")
    public ApiResponse<ImplementationCreateContext> getImplementationCreateContext(
            @RequestParam String extChatId,
            HttpSession session) {
        try {
            String loginUserId = getLoginUserId(session);
            ImplementationCreateContext context = chatGroupService.getImplementationCreateContext(extChatId, loginUserId);
            return ApiResponse.success(context);
        } catch (Exception e) {
            LOGGER.error("getImplementationCreateContext failed: {}", e.getMessage(), e);
            return ApiResponse.error("获取新增实施上下文失败: " + e.getMessage());
        }
    }

    @GetMapping("/maintenance-create-context")
    public ApiResponse<MaintenanceCreateContext> getMaintenanceCreateContext(
            @RequestParam String extChatId,
            HttpSession session) {
        try {
            String loginUserId = getLoginUserId(session);
            MaintenanceCreateContext context = chatGroupService.getMaintenanceCreateContext(extChatId, loginUserId);
            return ApiResponse.success(context);
        } catch (Exception e) {
            LOGGER.error("getMaintenanceCreateContext failed: {}", e.getMessage(), e);
            return ApiResponse.error("获取新增维护上下文失败: " + e.getMessage());
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

    // 新增维护记录接口（后端代调 CSCRM）
    @PostMapping("/maintenance-records")
    public ApiResponse<JSONObject> createMaintenanceRecord(
            @RequestBody CreateMaintenanceRecordRequest request,
            HttpSession session) {
        try {
            JSONObject userInfo = (JSONObject) session.getAttribute("login_user");
            String loginUserId = null;
            if (userInfo != null) {
                loginUserId = userInfo.getString("userid");
                if (loginUserId == null) {
                    loginUserId = userInfo.getString("UserId");
                }
                if (loginUserId == null) {
                    loginUserId = userInfo.getString("user_id");
                }
            }

            JSONObject data = chatGroupService.createMaintenanceRecord(request, loginUserId);
            return ApiResponse.success(data);
        } catch (Exception e) {
            LOGGER.error("createMaintenanceRecord failed: {}", e.getMessage(), e);
            return ApiResponse.error("新增维护记录失败: " + e.getMessage());
        }
    }

    @PostMapping("/implementation-records")
    public ApiResponse<JSONObject> createImplementationRecord(
            @RequestBody CreateImplementationRecordRequest request,
            HttpSession session) {
        try {
            String loginUserId = getLoginUserId(session);
            JSONObject data = chatGroupService.createImplementationRecord(request, loginUserId);
            return ApiResponse.success(data);
        } catch (Exception e) {
            LOGGER.error("createImplementationRecord failed: {}", e.getMessage(), e);
            return ApiResponse.error("新增实施记录失败: " + e.getMessage());
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

            // 打印调试信息
            LOGGER.debug("userInfo keys: {}", userInfo.keySet());
            LOGGER.debug("userInfo: {}", userInfo.toJSONString());

            // 尝试多种可能的字段名获取userId
            String userId = userInfo.getString("userid");
            if (userId == null) {
                userId = userInfo.getString("UserId");
            }
            if (userId == null) {
                userId = userInfo.getString("user_id");
            }

            if (userId == null) {
                return ApiResponse.error("无法获取用户ID");
            }

            // 从数据库查询真实姓名
            String userName = null;
            try {
                com.util.JdbcUtils.setCscrmConfig();
                String sql = "SELECT name FROM staff WHERE ext_id = ? LIMIT 1";
                var result = com.util.JdbcUtils.query(sql, userId);
                if (!result.isEmpty() && result.get(0)[0] != null) {
                    userName = result.get(0)[0].toString();
                }
            } finally {
                com.util.JdbcUtils.clearConfig();
            }

            // 如果数据库查询失败，使用 userId 作为备用
            if (userName == null || userName.isEmpty()) {
                userName = userId;
            }

            // 打印当前登录用户信息
            LOGGER.info("========== 当前登录用户信息 ==========");
            LOGGER.info("当前登录用户ID (modifiedById): {}", userId);
            LOGGER.info("当前登录用户姓名 (modifiedByName): {}", userName);
            LOGGER.info("====================================");

            // 设置工单ID
            request.setTicketId(ticketId);

            // 调用 service 更新工单
            chatGroupService.updateTicket(request, userId, userName);

            return ApiResponse.success(null);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("更新工单失败: " + e.getMessage());
        }
    }

    // 更新需求工单接口（按 api.md 需求工单参数组装）
    @PutMapping("/issue-tickets/{ticketId}")
    public ApiResponse<Void> updateIssueTicket(
            @PathVariable String ticketId,
            @RequestBody UpdateTicketRequest request,
            HttpSession session) {
        try {
            String loginUserId = getLoginUserId(session);
            if (loginUserId == null) {
                return ApiResponse.error("用户未登录");
            }
            request.setTicketId(ticketId);
            chatGroupService.updateIssueOrBugTicket(request, loginUserId, "issue");
            return ApiResponse.success(null);
        } catch (Exception e) {
            LOGGER.error("updateIssueTicket failed: {}", e.getMessage(), e);
            return ApiResponse.error("更新需求工单失败: " + e.getMessage());
        }
    }

    // 更新缺陷工单接口（按 api.md 缺陷工单参数组装）
    @PutMapping("/bug-tickets/{ticketId}")
    public ApiResponse<Void> updateBugTicket(
            @PathVariable String ticketId,
            @RequestBody UpdateTicketRequest request,
            HttpSession session) {
        try {
            String loginUserId = getLoginUserId(session);
            if (loginUserId == null) {
                return ApiResponse.error("用户未登录");
            }
            request.setTicketId(ticketId);
            chatGroupService.updateIssueOrBugTicket(request, loginUserId, "bug");
            return ApiResponse.success(null);
        } catch (Exception e) {
            LOGGER.error("updateBugTicket failed: {}", e.getMessage(), e);
            return ApiResponse.error("更新缺陷工单失败: " + e.getMessage());
        }
    }

    // 获取需求工单接口
    @GetMapping("/issue-tickets")
    public ApiResponse<List<Ticket>> getIssueTickets(@RequestParam String extChatId) {
        try {
            List<Ticket> tickets = chatGroupService.getIssueTickets(extChatId);
            return ApiResponse.success(tickets);
        } catch (Exception e) {
            return ApiResponse.error("获取需求工单失败: " + e.getMessage());
        }
    }

    // 获取缺陷工单接口
    @GetMapping("/bug-tickets")
    public ApiResponse<List<Ticket>> getBugTickets(@RequestParam String extChatId) {
        try {
            List<Ticket> tickets = chatGroupService.getBugTickets(extChatId);
            return ApiResponse.success(tickets);
        } catch (Exception e) {
            return ApiResponse.error("获取缺陷工单失败: " + e.getMessage());
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

            // 尝试多种可能的字段名
            String userId = userInfo.getString("userid");
            if (userId == null) {
                userId = userInfo.getString("UserId");
            }
            if (userId == null) {
                userId = userInfo.getString("user_id");
            }

            if (userId == null) {
                return ApiResponse.error("无法获取用户ID");
            }

            List<String> staffList = chatGroupService.getStaffList(userId);
            return ApiResponse.success(staffList);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取员工列表失败: " + e.getMessage());
        }
    }

    // 获取产品版本列表接口
    @GetMapping("/product-versions")
    public ApiResponse<Map<String, Object>> getProductVersions(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String extChatId) {
        try {
            LOGGER.info("product-versions request productId={}, extChatId={}", productId, extChatId);
            if (productId == null && extChatId != null && !extChatId.isEmpty()) {
                productId = chatGroupService.getProductIdByExtChatId(extChatId);
                LOGGER.info("product-versions resolved productId by extChatId={}, productId={}", extChatId, productId);
            }
            if (productId == null) {
                LOGGER.warn("product-versions resolve failed, productId is null, extChatId={}", extChatId);
                return ApiResponse.error("无法识别产品ID");
            }
            List<String> versions = chatGroupService.getProductVersions(productId, extChatId);
            Map<String, Object> data = new HashMap<>();
            data.put("productId", productId);
            data.put("items", versions);
            LOGGER.info("product-versions success productId={}, count={}", productId, versions.size());
            return ApiResponse.success(data);
        } catch (Exception e) {
            LOGGER.error("product-versions failed productId={}, extChatId={}, err={}", productId, extChatId, e.getMessage(), e);
            return ApiResponse.error("获取产品版本失败: " + e.getMessage());
        }
    }

    @GetMapping("/product-download-url")
    public ApiResponse<Map<String, Object>> getProductDownloadUrl(
            @RequestParam String extChatId,
            @RequestParam String version) {
        try {
            Map<String, Object> data = chatGroupService.getProductDownloadUrl(extChatId, version);
            return ApiResponse.success(data);
        } catch (Exception e) {
            LOGGER.error("product-download-url failed extChatId={}, version={}, err={}", extChatId, version, e.getMessage(), e);
            return ApiResponse.error("获取下载链接失败: " + e.getMessage());
        }
    }
}
