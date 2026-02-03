package com.service;

import com.alibaba.fastjson.JSONObject;
import com.model.*;
import com.model.request.CreateDocRequest;
import com.model.request.GetUserIdRequest;
import com.util.HttpClientUtil;
import com.util.TicketCache;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class WxworkService {

    @Value("${app_secret}")
    private String customerSecret;

    @Value("${crop_id}")
    private String cropId;

    @Value("${agentid}")
    private String agentid;

    @Value("${api_access_token_url}")
    private String apiAccessTokenUrl;

    @Value("${api_create_doc_url}")
    private String apiCreateDocUrl;

    @Value("${api_get_user_id_url}")
    private String getUserIdUrl;

    @Value("${api_get_jsapi_ticket_url}")
    private String apiGetJsapiTicketUrl;

    @Value("${api_get_agent_jsapi_ticket_url}")
    private String apiGetAgentJsapiTicketUrl;

    public String getHttpRequest(String url) throws Exception {
        return HttpClientUtil.getRequest(url);
    }
    
    public String getAccessToken() throws Exception {
        System.out.println("开始调用企业微信API获取access_token...");
        long start = System.currentTimeMillis();
        
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("corpid", cropId));
        params.add(new BasicNameValuePair("corpsecret", customerSecret));
        
        System.out.println("请求参数: corpid=" + cropId + ", corpsecret=******");
        System.out.println("请求URL: " + apiAccessTokenUrl);
        
        long requestStart = System.currentTimeMillis();
        String response = HttpClientUtil.getRequest(apiAccessTokenUrl,params);
        System.out.println("HTTP请求耗时: " + (System.currentTimeMillis() - requestStart) + "ms");
        
        System.out.println("响应内容: " + response);
        
        AccessTokenModel accessTokenModel = JSONObject.parseObject(response, AccessTokenModel.class);
        
        long totalTime = System.currentTimeMillis() - start;
        System.out.println("获取access_token完成，总耗时: " + totalTime + "ms");
        
        return accessTokenModel.getAccessToken();
    }

    public CreateDocModel createDoc(CreateDocRequest request) throws Exception {
        String accessToken = getAccessToken();
        
        String url = String.format(apiCreateDocUrl, accessToken);
        String response = HttpClientUtil.postJSON(url, JSONObject.toJSONString(request));
        return JSONObject.parseObject(response, CreateDocModel.class);
    }

    public GetUserIdModel getUserId(GetUserIdRequest request , String accessToken) throws Exception {
        String url = String.format(getUserIdUrl, accessToken);
        String response = HttpClientUtil.postJSON(url, JSONObject.toJSONString(request));
        return JSONObject.parseObject(response, GetUserIdModel.class);
    }

    public JsapiTicketModel getJsapiTicket() throws Exception {
        String cacheKey = "jsapi_ticket:" + cropId;
        
        String cachedTicket = TicketCache.get(cacheKey);
        if (cachedTicket != null) {
            System.out.println("从缓存中获取企业jsapi_ticket");
            JsapiTicketModel model = new JsapiTicketModel();
            model.setTicket(cachedTicket);
            model.setErrcode(0);
            model.setErrmsg("ok");
            return model;
        }
        
        System.out.println("开始调用企业微信API获取企业jsapi_ticket...");
        long start = System.currentTimeMillis();
        
        String accessToken = getAccessToken();
        String url = String.format(apiGetJsapiTicketUrl, accessToken);
        
        System.out.println("请求URL: " + url);
        
        long requestStart = System.currentTimeMillis();
        String response = HttpClientUtil.getRequest(url);
        System.out.println("HTTP请求耗时: " + (System.currentTimeMillis() - requestStart) + "ms");
        
        System.out.println("响应内容: " + response);
        
        JsapiTicketModel ticketModel = JSONObject.parseObject(response, JsapiTicketModel.class);
        
        if (ticketModel.getErrcode() == 0) {
            TicketCache.put(cacheKey, ticketModel.getTicket(), ticketModel.getExpiresIn());
            System.out.println("企业jsapi_ticket已缓存，有效期: " + ticketModel.getExpiresIn() + "秒");
        }
        
        long totalTime = System.currentTimeMillis() - start;
        System.out.println("获取企业jsapi_ticket完成，总耗时: " + totalTime + "ms");
        
        return ticketModel;
    }

    public JsapiTicketModel getAgentJsapiTicket() throws Exception {
        String cacheKey = "agent_jsapi_ticket:" + cropId;
        
        String cachedTicket = TicketCache.get(cacheKey);
        if (cachedTicket != null) {
            System.out.println("从缓存中获取应用jsapi_ticket");
            JsapiTicketModel model = new JsapiTicketModel();
            model.setTicket(cachedTicket);
            model.setErrcode(0);
            model.setErrmsg("ok");
            return model;
        }
        
        System.out.println("开始调用企业微信API获取应用jsapi_ticket...");
        long start = System.currentTimeMillis();
        
        String accessToken = getAccessToken();
        String url = String.format(apiGetAgentJsapiTicketUrl, accessToken);
        
        System.out.println("请求URL: " + url);
        
        long requestStart = System.currentTimeMillis();
        String response = HttpClientUtil.getRequest(url);
        System.out.println("HTTP请求耗时: " + (System.currentTimeMillis() - requestStart) + "ms");
        
        System.out.println("响应内容: " + response);
        
        JsapiTicketModel ticketModel = JSONObject.parseObject(response, JsapiTicketModel.class);
        
        if (ticketModel.getErrcode() == 0) {
            TicketCache.put(cacheKey, ticketModel.getTicket(), ticketModel.getExpiresIn());
            System.out.println("应用jsapi_ticket已缓存，有效期: " + ticketModel.getExpiresIn() + "秒");
        }
        
        long totalTime = System.currentTimeMillis() - start;
        System.out.println("获取应用jsapi_ticket完成，总耗时: " + totalTime + "ms");
        
        return ticketModel;
    }

    public String getCorpId() {
        return cropId;
    }

    public String getAgentId() {
        return agentid;
    }

}
