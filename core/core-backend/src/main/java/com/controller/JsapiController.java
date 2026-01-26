package com.controller;

import com.alibaba.fastjson.JSONObject;
import com.model.ApiResponse;
import com.model.JsapiTicketModel;
import com.service.WxworkService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jsapi")
public class JsapiController {

    @Resource
    private WxworkService wxworkService;

    @GetMapping("/get-ticket")
    public ApiResponse<JSONObject> getJsapiTicket() {
        try {
            JsapiTicketModel ticketModel = wxworkService.getJsapiTicket();
            
            if (ticketModel.getErrcode() == 0) {
                JSONObject data = new JSONObject();
                data.put("ticket", ticketModel.getTicket());
                data.put("expires_in", ticketModel.getExpiresIn());
                return ApiResponse.success(data);
            } else {
                return ApiResponse.error(ticketModel.getErrmsg());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取企业jsapi_ticket失败: " + e.getMessage());
        }
    }

    @GetMapping("/get-agent-ticket")
    public ApiResponse<JSONObject> getAgentJsapiTicket() {
        try {
            JsapiTicketModel ticketModel = wxworkService.getAgentJsapiTicket();
            
            if (ticketModel.getErrcode() == 0) {
                JSONObject data = new JSONObject();
                data.put("ticket", ticketModel.getTicket());
                data.put("expires_in", ticketModel.getExpiresIn());
                return ApiResponse.success(data);
            } else {
                return ApiResponse.error(ticketModel.getErrmsg());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取应用jsapi_ticket失败: " + e.getMessage());
        }
    }

    @GetMapping("/get-corp-id")
    public ApiResponse<String> getCorpId() {
        try {
            String corpId = wxworkService.getCorpId();
            return ApiResponse.success(corpId);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取corpId失败: " + e.getMessage());
        }
    }

    @GetMapping("/get-agent-id")
    public ApiResponse<String> getAgentId() {
        try {
            String agentId = wxworkService.getAgentId();
            return ApiResponse.success(agentId);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error("获取agentId失败: " + e.getMessage());
        }
    }
}
