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
     * 获取 Access Token
     * @return
     * @throws Exception
     */
    public  String getAccessToken() throws Exception {
        List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("corpid", cropId));
        params.add(new BasicNameValuePair("corpsecret", customerSecret));
        String response = HttpClientUtil.getRequest(apiAccessTokenUrl,params);
        AccessTokenModel accessTokenModel = JSONObject.parseObject(response, AccessTokenModel.class);
        return accessTokenModel.getAccessToken();
    }

    /**
     * 创建企业微信文档
     */
    public CreateDocModel createDoc(CreateDocRequest request) throws Exception {
        // 获取access_token
        String accessToken = getAccessToken();
        List<String> adminUsers = new ArrayList<>();
        request.getAdminUserPhoneNumbers().forEach(adminUserPhoneNumber -> {
            GetUserIdRequest getUserIdRequest = new GetUserIdRequest();
            getUserIdRequest.setMobile(adminUserPhoneNumber);
            try {
                GetUserIdModel userIdModel = getUserId(getUserIdRequest, accessToken);
                if (userIdModel.getErrcode().compareTo(0)==0) {
                    adminUsers.add(userIdModel.getUserid());
                } else {
                    throw new RuntimeException(userIdModel.getErrmsg());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        request.setAdminUsers(adminUsers);

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
