package com.service;

import com.alibaba.fastjson.JSONObject;
import com.model.AccessTokenModel;
import com.model.ArchiveMsgDecryptModel;
import com.model.CustomerGroupDetailModel;
import com.model.request.CustomerGroupDetailsRequest;
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

    @Value("${customer_secret}")
    private String customerSecret;

    @Value("${crop_id}")
    private String cropId;

    @Value("${api_access_token_url}")
    private String apiAccessTokenUrl;

    @Value("${api_customer_group_details_url}")
    private String customerGroupDetailsUrl;

    public CustomerGroupDetailModel getCustomerGroupChat(String chatId) throws Exception {
        String accessToken = getAccessToken();
        System.out.println(accessToken);
        CustomerGroupDetailsRequest customerGroupDetailsRequest = new CustomerGroupDetailsRequest();
        customerGroupDetailsRequest.setChatId(chatId);
        customerGroupDetailsRequest.setNeedName(1);

        String response = HttpClientUtil.postJSON(String.format(customerGroupDetailsUrl, accessToken), JSONObject.toJSONString(customerGroupDetailsRequest));

        if (response.contains("chat is not external group chat")) {
            return null;
        }
        return JSONObject.parseObject(response, CustomerGroupDetailModel.class);
    }


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




}
