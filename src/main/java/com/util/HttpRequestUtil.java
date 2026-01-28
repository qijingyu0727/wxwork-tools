package com.util;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

/**
 * @program: wxwork-tools
 * @description:
 * @create: 2020-08-18 17:52
 **/
public class HttpRequestUtil {
    public static Map<String, String> getRequest(String url, String json, ApiType type, Map<String, String> stringMap) throws Exception {
        Map<String, String> map = new HashMap<>();
        EntityBuilder entityBuilder = EntityBuilder.create();
        entityBuilder.setText(json);
        entityBuilder.setContentType(ContentType.APPLICATION_JSON);
        entityBuilder.setContentEncoding("UTF-8");
        HttpEntity requestEntity = entityBuilder.build();
        switch (type) {
            case API_POST:
                HttpPost httpPost = new HttpPost(url);
                httpPost.setEntity(requestEntity);
                map = apiRequest(httpPost, stringMap);
                break;
            case API_GET:
                HttpGet httpGet = new HttpGet(url);
                map = apiRequest(httpGet, stringMap);
                break;
            default:
                break;
        }
        return map;
    }
    private static Map<String, String> apiRequest(HttpRequestBase request, Map<String, String> stringMap) throws Exception {
        request.addHeader("Content-Type", "application/json");
        request.addHeader("Content-Encoding", "UTF-8");
        if (stringMap.size() > 0){
            stringMap.forEach((key, value) -> request.addHeader(key, value));
        }

        HttpRequestRetryHandler requestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                System.out.printf("重试：" + executionCount);
                if (executionCount > 3) {
                    // Do not retry if over max retry count
                    System.out.println(" // Do not retry if over max retry count");
                    return false;
                }
                if (exception instanceof SocketTimeoutException) {
                    // Read timed out
                    System.out.println(" // Read timed out");
                    return true;
                }

                if (exception instanceof InterruptedIOException) {
                    // Timeout
                    System.out.println(" // Timeout");
                    return false;
                }
                if (exception instanceof UnknownHostException) {
                    // Unknown host
                    System.out.println("   // Unknown host");
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {
                    // Connection refused
                    System.out.println("// Connection refused");
                    return false;
                }
                if (exception instanceof SSLException) {
                    // SSL handshake exception
                    System.out.println(" // SSL handshake exception");
                    return false;
                }
                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                boolean idempotent = !(request instanceof HttpEntityEnclosingRequest);
                if (idempotent) {
                    // Retry if the request is considered idempotent
                    System.out.println(" // Retry if the request is considered idempotent");
                    return true;
                }
                return false;
            }
        };

        //增加重试机制
        CloseableHttpClient httpclient = HttpClients.custom().setRetryHandler(requestRetryHandler).build();
        RequestConfig config = RequestConfig.custom().setSocketTimeout(30 * 1000).setConnectTimeout(10 * 1000).
                setConnectionRequestTimeout(10 * 1000).build();
        request.setConfig(config);
        HttpResponse response = httpclient.execute(request);
        return checkoutApiResult(httpclient, response);
    }

    private static Map<String, String> checkoutApiResult(CloseableHttpClient httpclient, HttpResponse response) throws Exception {
        Map<String, String> map = new HashMap<>();
        int code = response.getStatusLine().getStatusCode();
        map.put("code", String.valueOf(code));
        String strResult = "";
        if (code >= 400) {
            if (null != response.getEntity()) {
                strResult += EntityUtils.toString(response.getEntity(), "UTF-8");
            }
            throw new Exception(strResult);
        } else {
            //删除成功也不返回数据
            if (null != response.getEntity()) {
                strResult += EntityUtils.toString(response.getEntity(), "UTF-8");
            }
        }
        map.put("resultStr", strResult);
        if (httpclient != null) {
            httpclient.close();
        }
        return map;
    }
}