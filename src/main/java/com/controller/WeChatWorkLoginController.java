package com.controller;

import com.alibaba.fastjson.JSONObject;
import com.model.ApiResponse;
import com.service.DocService;
import com.service.WxworkService;
import com.util.HttpClientUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 企业微信扫码登录控制器
 */
@Controller
@RequestMapping("/wechat/work/login")
public class WeChatWorkLoginController {

    @Value("${crop_id}")
    private String cropId;

    @Value("${agentid}")
    private String agentid;

    @Value("${api_get_login_qrcode_url}")
    private String apiGetLoginQrcodeUrl;

    @Value("${api_get_user_info_url}")
    private String apiGetUserInfoUrl;

    @Value("${api_get_user_detail_url}")
    private String apiGetUserDetailUrl;

    @Value("${login_redirect_uri}")
    private String loginRedirectUri;

    // 企业微信应用内免密登录相关配置
    @Value("${login_oauth2_redirect_uri}")
    private String loginOauth2RedirectUri;

    private static final String API_OAUTH2_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String API_GET_USER_TICKET_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
    private static final String API_GET_USER_DETAIL_WITH_TICKET_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserdetail";

    @Resource
    private WxworkService wxworkService;

    @Resource
    private DocService docService;

    /**
     * 生成企业微信扫码登录链接
     */
    @GetMapping("/generate-qrcode")
    public String generateLoginQrcode(HttpSession session) throws Exception {
        // 生成state参数，用于防止CSRF攻击
        String state = UUID.randomUUID().toString().replace("-", "");
        // 将state存入session，用于后续验证
        session.setAttribute("login_state", state);
        // 生成扫码登录URL
        String encodedRedirectUri = URLEncoder.encode(loginRedirectUri, StandardCharsets.UTF_8.toString());
        // 注意：这里的agentid需要根据实际情况设置，通常是应用的agentid
        String qrcodeUrl = String.format(apiGetLoginQrcodeUrl, cropId, agentid, encodedRedirectUri, state);
        // 重定向到企业微信扫码登录页面
        return "redirect:" + qrcodeUrl;
    }

    /**
     * 处理企业微信扫码登录回调
     */
    @GetMapping("/callback")
    public String handleLoginCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpSession session) {
        try {
            // 验证state，防止CSRF攻击
            String savedState = (String) session.getAttribute("login_state");
            if (savedState == null || !savedState.equals(state)) {
                String errorMessage = "无效的登录请求";
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error.html?error=" + errorMessage;
            }

            // 清除session中的state
            session.removeAttribute("login_state");

            // 获取access_token
            String accessToken = wxworkService.getAccessToken();

            // 获取用户信息
            String userInfoUrl = String.format(apiGetUserInfoUrl, accessToken, code);
            String userInfoResponse = wxworkService.getHttpRequest(userInfoUrl);
            JSONObject userInfoJson = JSONObject.parseObject(userInfoResponse);

            if (userInfoJson.getInteger("errcode") != 0) {
                String errorMessage = "获取用户信息失败：" + userInfoJson.getString("errmsg");
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error.html?error=" + errorMessage;
            }

            // 获取用户详情
            String userId = userInfoJson.getString("UserId");
            String userDetailUrl = String.format(apiGetUserDetailUrl, accessToken, userId);
            String userDetailResponse = wxworkService.getHttpRequest(userDetailUrl);
            JSONObject userDetailJson = JSONObject.parseObject(userDetailResponse);

            if (userDetailJson.getInteger("errcode") != 0) {
                String errorMessage = "获取用户详情失败：" + userDetailJson.getString("errmsg");
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error.html?error=" + errorMessage;
            }

            // 将用户信息存入session
            session.setAttribute("login_user", userDetailJson);
            session.setAttribute("is_login", true);

            // 登录成功后重定向到index.html页面
            return "redirect:/index.html";
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "登录失败：" + e.getMessage();
            try {
                errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
            } catch (Exception ignored) {}
            return "redirect:/login-error.html?error=" + errorMessage;
        }
    }

    /**
     * 检查用户是否已登录
     */
    @GetMapping("/check-login")
    @ResponseBody
    public ApiResponse<JSONObject> checkLogin(HttpSession session) {
        Boolean isLogin = (Boolean) session.getAttribute("is_login");
        JSONObject userInfo = (JSONObject) session.getAttribute("login_user");

        if (isLogin != null && isLogin && userInfo != null) {
            // 检查用户是否为管理员
            String userId = userInfo.getString("userid");
            boolean isAdmin = false;
            
            if (StringUtils.isNotBlank(docService.getAdminIds()) && StringUtils.isNotBlank(userId)) {
                // 分割admin_ids配置，检查当前用户是否在管理员列表中
                String[] adminIds = docService.getAdminIds().split(",");
                for (String adminId : adminIds) {
                    if (userId.equals(adminId.trim())) {
                        isAdmin = true;
                        break;
                    }
                }
            }
            
            // 将管理员状态添加到用户信息中
            userInfo.put("isAdmin", isAdmin);
            
            return ApiResponse.success(userInfo);
        } else {
            return ApiResponse.error("用户未登录");
        }
    }

    /**
     * 退出登录
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        // 清除登录相关的session数据
        session.removeAttribute("is_login");
        session.removeAttribute("login_user");
        // 退出登录后重定向到退出页面
        return "redirect:/logout-success.html";
    }

    /**
     * 生成企业微信应用内免密登录的授权链接
     * 用于在企业微信应用内打开时，引导用户授权获取code
     */
    @GetMapping("/generate-oauth2-url")
    public String generateOauth2Url(HttpServletRequest request, HttpSession session) throws Exception {
        // 生成state参数，用于防止CSRF攻击
        String state = UUID.randomUUID().toString().replace("-", "");
        // 将state存入session，用于后续验证
        session.setAttribute("oauth2_state", state);
        
        String encodedRedirectUri = URLEncoder.encode(loginOauth2RedirectUri, StandardCharsets.UTF_8.toString());
        
        // 生成授权URL，scope=snsapi_privateinfo可以获取更多用户信息
        String oauth2Url = API_OAUTH2_AUTHORIZE_URL + "?appid=" + cropId + "&redirect_uri=" + encodedRedirectUri + "&response_type=code&scope=snsapi_privateinfo&agentid=" + agentid + "&state=" + state + "#wechat_redirect";
        
        // 重定向到企业微信授权页面
        return "redirect:" + oauth2Url;
    }

    /**
     * 处理企业微信应用内免密登录的回调
     */
    @GetMapping("/oauth2-callback")
    public String handleOauth2Callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpSession session) {
        try {
            System.out.println("企业微信免密登录开始处理...");
            long startTime = System.currentTimeMillis();
            
            // 验证state，防止CSRF攻击
            long stateVerifyStart = System.currentTimeMillis();
            String savedState = (String) session.getAttribute("oauth2_state");
            if (savedState == null || !savedState.equals(state)) {
                String errorMessage = "无效的登录请求";
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error.html?error=" + errorMessage;
            }
            System.out.println("验证state耗时：" + (System.currentTimeMillis() - stateVerifyStart) + "ms");

            // 清除session中的state
            session.removeAttribute("oauth2_state");

            // 获取access_token
            long accessTokenStart = System.currentTimeMillis();
            System.out.println("开始获取access_token...");
            String accessToken = wxworkService.getAccessToken();
            System.out.println("获取access_token耗时：" + (System.currentTimeMillis() - accessTokenStart) + "ms");

            // 第一步：使用code获取user_ticket
            long userTicketStart = System.currentTimeMillis();
            System.out.println("开始获取user_ticket...");
            String userTicketUrl = API_GET_USER_TICKET_URL + "?access_token=" + accessToken + "&code=" + code;
            String userTicketResponse = wxworkService.getHttpRequest(userTicketUrl);
            JSONObject userTicketJson = JSONObject.parseObject(userTicketResponse);
            System.out.println("获取user_ticket耗时：" + (System.currentTimeMillis() - userTicketStart) + "ms");

            if (userTicketJson.getInteger("errcode") != 0) {
                String errorMessage = "获取用户票据失败：" + userTicketJson.getString("errmsg");
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error.html?error=" + errorMessage;
            }

            // 第二步：使用user_ticket获取用户详情
            long userDetailStart = System.currentTimeMillis();
            System.out.println("开始获取用户详情...");
            String userTicket = userTicketJson.getString("user_ticket");
            JSONObject ticketParam = new JSONObject();
            ticketParam.put("user_ticket", userTicket);
            
            String userDetailUrl = API_GET_USER_DETAIL_WITH_TICKET_URL + "?access_token=" + accessToken;
            String userDetailResponse = HttpClientUtil.postJSON(userDetailUrl, ticketParam.toJSONString());
            JSONObject userDetailJson = JSONObject.parseObject(userDetailResponse);
            System.out.println("获取用户详情耗时：" + (System.currentTimeMillis() - userDetailStart) + "ms");

            if (userDetailJson.getInteger("errcode") != 0) {
                String errorMessage = "获取用户详情失败：" + userDetailJson.getString("errmsg");
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error.html?error=" + errorMessage;
            }

            // 将用户信息存入session
            session.setAttribute("login_user", userDetailJson);
            session.setAttribute("is_login", true);

            // 登录成功后重定向到index.html页面
            return "redirect:/index.html";
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "登录失败：" + e.getMessage();
            try {
                errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
            } catch (Exception ignored) {}
            return "redirect:/login-error.html?error=" + errorMessage;
        }
    }
}