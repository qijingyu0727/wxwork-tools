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
import java.util.Map;
import java.util.HashMap;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-18 14:46
 **/
@Service
public class WxworkService {

    @Value("${app_secret}")
    private String customerSecret;

    @Value("${crop_id}")
    private String cropId;

    @Value("${api_access_token_url}")
    private String apiAccessTokenUrl;

    @Value("${api_create_doc_url}")
    private String apiCreateDocUrl;

    @Value("${api_get_user_id_url}")
    private String getUserIdUrl;

    /**
     * 发送HTTP GET请求
     * @param url 请求URL
     * @return 请求响应内容
     * @throws Exception 异常信息
     */
    public String getHttpRequest(String url) throws Exception {
        // 使用现有的HttpClientUtil工具类发送GET请求
        return HttpClientUtil.getRequest(url);
    }
    
    /**
     * 获取 Access Token
     * @return
     * @throws Exception
     */
    public  String getAccessToken() throws Exception {
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

    /**
     * 创建企业微信文档
     */
    public CreateDocModel createDoc(CreateDocRequest request) throws Exception {
        // 获取access_token
        String accessToken = getAccessToken();
        
        // 现在管理员用户ID已经在Controller层设置好了，不再需要根据手机号查询用户ID
        // 直接使用request中已设置的adminUsers
        
        // 构建请求URL
        String url = String.format(apiCreateDocUrl, accessToken);
        // 发送POST请求
        String response = HttpClientUtil.postJSON(url, JSONObject.toJSONString(request));
        // 转换响应结果
        return JSONObject.parseObject(response, CreateDocModel.class);
    }


    /**
     * 创建企业微信文档
     */
    public GetUserIdModel getUserId(GetUserIdRequest request , String accessToken) throws Exception {
        // 构建请求URL
        String url = String.format(getUserIdUrl, accessToken);
        // 发送POST请求
        String response = HttpClientUtil.postJSON(url, JSONObject.toJSONString(request));
        // 转换响应结果
        return JSONObject.parseObject(response, GetUserIdModel.class);
    }


}
