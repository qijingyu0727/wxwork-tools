package com.controller;

import com.model.ApiResponse;
import com.model.CustomerData;
import com.service.ChatGroupService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
