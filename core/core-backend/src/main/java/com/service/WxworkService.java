package com.service;

import com.alibaba.fastjson.JSONObject;
import com.model.*;
import com.model.request.CreateDocRequest;
import com.model.request.GetUserIdRequest;
import com.util.HttpClientUtil;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WxworkService {

    private static final long CACHE_REFRESH_BUFFER_MILLIS = 5 * 60 * 1000L;

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

    private final Object accessTokenLock = new Object();
    private final Object jsapiTicketLock = new Object();
    private final Object agentJsapiTicketLock = new Object();

    private volatile String cachedAccessToken;
    private volatile long accessTokenExpireAt;
    private volatile JsapiTicketModel cachedJsapiTicket;
    private volatile long jsapiTicketExpireAt;
    private volatile JsapiTicketModel cachedAgentJsapiTicket;
    private volatile long agentJsapiTicketExpireAt;

    public String getHttpRequest(String url) throws Exception {
        return HttpClientUtil.getRequest(url);
    }

    public String getAccessToken() throws Exception {
        if (isCacheValid(accessTokenExpireAt)) {
            return cachedAccessToken;
        }

        synchronized (accessTokenLock) {
            if (isCacheValid(accessTokenExpireAt)) {
                return cachedAccessToken;
            }

            return refreshAccessToken();
        }
    }

    private String refreshAccessToken() throws Exception {
        System.out.println("开始调用企业微信API获取access_token...");
        long start = System.currentTimeMillis();

        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("corpid", cropId));
        params.add(new BasicNameValuePair("corpsecret", customerSecret));

        System.out.println("请求参数: corpid=" + cropId + ", corpsecret=******");
        System.out.println("请求URL: " + apiAccessTokenUrl);

        long requestStart = System.currentTimeMillis();
        String response = HttpClientUtil.getRequest(apiAccessTokenUrl, params);
        System.out.println("HTTP请求耗时: " + (System.currentTimeMillis() - requestStart) + "ms");

        System.out.println("响应内容: " + response);

        AccessTokenModel accessTokenModel = JSONObject.parseObject(response, AccessTokenModel.class);
        if (accessTokenModel == null || accessTokenModel.getErrcode() == null) {
            throw new Exception("获取access_token响应异常");
        }
        if (accessTokenModel.getErrcode() != 0 || accessTokenModel.getAccessToken() == null || accessTokenModel.getAccessToken().isEmpty()) {
            throw new Exception("获取access_token失败: " + accessTokenModel.getErrmsg());
        }

        long totalTime = System.currentTimeMillis() - start;
        System.out.println("获取access_token完成，总耗时: " + totalTime + "ms");
        cachedAccessToken = accessTokenModel.getAccessToken();
        accessTokenExpireAt = buildExpireAt(accessTokenModel.getExpiresIn());
        return cachedAccessToken;
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
        if (isCacheValid(jsapiTicketExpireAt)) {
            return cachedJsapiTicket;
        }

        synchronized (jsapiTicketLock) {
            if (isCacheValid(jsapiTicketExpireAt)) {
                return cachedJsapiTicket;
            }

            JsapiTicketModel ticketModel = getTicket(apiGetJsapiTicketUrl, "企业jsapi_ticket");
            cachedJsapiTicket = ticketModel;
            jsapiTicketExpireAt = buildExpireAt(ticketModel.getExpiresIn());
            return cachedJsapiTicket;
        }
    }

    public JsapiTicketModel getAgentJsapiTicket() throws Exception {
        if (isCacheValid(agentJsapiTicketExpireAt)) {
            return cachedAgentJsapiTicket;
        }

        synchronized (agentJsapiTicketLock) {
            if (isCacheValid(agentJsapiTicketExpireAt)) {
                return cachedAgentJsapiTicket;
            }

            JsapiTicketModel ticketModel = getTicket(apiGetAgentJsapiTicketUrl, "应用jsapi_ticket");
            cachedAgentJsapiTicket = ticketModel;
            agentJsapiTicketExpireAt = buildExpireAt(ticketModel.getExpiresIn());
            return cachedAgentJsapiTicket;
        }
    }

    public String getCorpId() {
        return cropId;
    }

    public String getAgentId() {
        return agentid;
    }

    private JsapiTicketModel getTicket(String ticketUrlTemplate, String ticketName) throws Exception {
        System.out.println("开始调用企业微信API获取" + ticketName + "...");
        long start = System.currentTimeMillis();

        String accessToken = getAccessToken();
        String url = String.format(ticketUrlTemplate, accessToken);

        System.out.println("请求URL: " + url);

        long requestStart = System.currentTimeMillis();
        String response = HttpClientUtil.getRequest(url);
        System.out.println("HTTP请求耗时: " + (System.currentTimeMillis() - requestStart) + "ms");

        System.out.println("响应内容: " + response);

        JsapiTicketModel ticketModel = JSONObject.parseObject(response, JsapiTicketModel.class);
        if (ticketModel == null || ticketModel.getErrcode() == null) {
            throw new Exception("获取" + ticketName + "响应异常");
        }
        if (ticketModel.getErrcode() != 0 || ticketModel.getTicket() == null || ticketModel.getTicket().isEmpty()) {
            throw new Exception("获取" + ticketName + "失败: " + ticketModel.getErrmsg());
        }

        long totalTime = System.currentTimeMillis() - start;
        System.out.println("获取" + ticketName + "完成，总耗时: " + totalTime + "ms");
        return ticketModel;
    }

    private boolean isCacheValid(long expireAt) {
        return expireAt > System.currentTimeMillis() + CACHE_REFRESH_BUFFER_MILLIS;
    }

    private long buildExpireAt(Integer expiresInSeconds) {
        int safeExpiresInSeconds = expiresInSeconds == null || expiresInSeconds <= 0 ? 7200 : expiresInSeconds;
        return System.currentTimeMillis() + safeExpiresInSeconds * 1000L;
    }

}
