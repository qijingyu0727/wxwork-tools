package com.controller;

import com.model.ApiResponse;
import com.model.request.AcceptanceReportRequest;
import com.model.request.MailDiagnoseRequest;
import com.model.request.SendToolMailRequest;
import com.service.ToolService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolController.class);

    @Resource
    private ToolService toolService;

    @PostMapping("/send-mail")
    public ApiResponse<Map<String, Object>> sendMail(@RequestBody SendToolMailRequest request) {
        try {
            if (request == null) {
                return ApiResponse.error("请求体不能为空");
            }
            Map<String, Object> data = toolService.sendToolMail(request);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("send tool mail failed: {}", e.getMessage(), e);
            return ApiResponse.error("邮件发送失败: " + e.getMessage());
        }
    }

    @PostMapping("/mail-diagnose")
    public ApiResponse<Map<String, Object>> mailDiagnose(@RequestBody(required = false) MailDiagnoseRequest request) {
        try {
            Map<String, Object> data = toolService.diagnoseMail(request);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("mail diagnose failed: {}", e.getMessage(), e);
            return ApiResponse.error("邮件诊断失败: " + e.getMessage());
        }
    }

    @GetMapping("/mail-default-cc")
    public ApiResponse<Map<String, Object>> mailDefaultCc(@RequestParam String extChatId) {
        try {
            Map<String, Object> data = toolService.getMailDefaultCc(extChatId);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("mail default cc failed extChatId={}, err={}", extChatId, e.getMessage(), e);
            return ApiResponse.error("获取默认抄送失败: " + e.getMessage());
        }
    }

    @PostMapping("/acceptance-report")
    public ApiResponse<Map<String, Object>> acceptanceReport(@RequestBody AcceptanceReportRequest request) {
        try {
            if (request == null) {
                return ApiResponse.error("请求体不能为空");
            }
            LOGGER.info("acceptance report api called, extChatId={}", request.getExtChatId());
            Map<String, Object> data = toolService.getAcceptanceReport(request);
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            LOGGER.error("get acceptance report failed: {}", e.getMessage(), e);
            return ApiResponse.error("获取验收报告失败: " + e.getMessage());
        }
    }
}
