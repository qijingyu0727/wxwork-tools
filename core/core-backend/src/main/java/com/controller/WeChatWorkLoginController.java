package com.controller;

import com.alibaba.fastjson.JSONObject;
import com.model.ApiResponse;
import com.service.DocService;
import com.service.WxworkService;
import com.util.HttpClientUtil;
import com.util.JdbcUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Controller
@RequestMapping("/wechat/work/login")
public class WeChatWorkLoginController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeChatWorkLoginController.class);

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

    @Value("${login_oauth2_redirect_uri}")
    private String loginOauth2RedirectUri;

    private static final String API_OAUTH2_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String API_GET_USER_TICKET_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo";
    private static final String API_GET_USER_DETAIL_WITH_TICKET_URL = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserdetail";

    @Resource
    private WxworkService wxworkService;

    @Resource
    private DocService docService;

    @GetMapping("/generate-qrcode")
    public String generateLoginQrcode(HttpSession session) throws Exception {
        String state = UUID.randomUUID().toString().replace("-", "");
        session.setAttribute("login_state", state);
        String encodedRedirectUri = URLEncoder.encode(loginRedirectUri, StandardCharsets.UTF_8.toString());
        String qrcodeUrl = String.format(apiGetLoginQrcodeUrl, cropId, agentid, encodedRedirectUri, state);
        return "redirect:" + qrcodeUrl;
    }

    @GetMapping("/callback")
    public String handleLoginCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpSession session) {
        try {
            String savedState = (String) session.getAttribute("login_state");
            if (savedState == null || !savedState.equals(state)) {
                String errorMessage = "无效的登录请求";
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error?error=" + errorMessage;
            }

            session.removeAttribute("login_state");

            String accessToken = wxworkService.getAccessToken();

            String userInfoUrl = String.format(apiGetUserInfoUrl, accessToken, code);
            String userInfoResponse = wxworkService.getHttpRequest(userInfoUrl);
            JSONObject userInfoJson = JSONObject.parseObject(userInfoResponse);

            if (userInfoJson.getInteger("errcode") != 0) {
                String errorMessage = "获取用户信息失败：" + userInfoJson.getString("errmsg");
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error?error=" + errorMessage;
            }

            String userId = userInfoJson.getString("UserId");
            String userDetailUrl = String.format(apiGetUserDetailUrl, accessToken, userId);
            String userDetailResponse = wxworkService.getHttpRequest(userDetailUrl);
            JSONObject userDetailJson = JSONObject.parseObject(userDetailResponse);

            if (userDetailJson.getInteger("errcode") != 0) {
                String errorMessage = "获取用户详情失败：" + userDetailJson.getString("errmsg");
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error?error=" + errorMessage;
            }

            session.setAttribute("login_user", userDetailJson);
            session.setAttribute("is_login", true);

            return "redirect:/";
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "登录失败：" + e.getMessage();
            try {
                errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
            } catch (Exception ignored) {}
            return "redirect:/login-error?error=" + errorMessage;
        }
    }

    @GetMapping("/check-login")
    @ResponseBody
    public ApiResponse<JSONObject> checkLogin(HttpSession session) {
        Boolean isLogin = (Boolean) session.getAttribute("is_login");
        JSONObject userInfo = (JSONObject) session.getAttribute("login_user");

        if (isLogin != null && isLogin && userInfo != null) {
            String userId = firstNonBlank(
                    userInfo.getString("userid"),
                    userInfo.getString("UserId"),
                    userInfo.getString("user_id")
            );
            String resolvedName = resolveStaffName(userId);
            if (StringUtils.isNotBlank(resolvedName)) {
                userInfo.put("name", resolvedName);
            }
            userInfo.put("isAdmin", isAdminUser(userId));
            
            return ApiResponse.success(userInfo);
        } else {
            return ApiResponse.error("用户未登录");
        }
    }

    @GetMapping("/debug-login")
    @ResponseBody
    public ApiResponse<JSONObject> debugLogin(@RequestParam("userId") String userId, HttpSession session) {
        String normalizedUserId = StringUtils.trimToNull(userId);
        if (normalizedUserId == null) {
            return ApiResponse.error("缺少调试用户ID");
        }

        try {
            JSONObject userInfo = buildDebugUserInfo(normalizedUserId);
            session.setAttribute("login_user", userInfo);
            session.setAttribute("is_login", true);
            LOGGER.info("debug login success userId={}, name={}", normalizedUserId, userInfo.getString("name"));
            return ApiResponse.success(userInfo);
        } catch (Exception e) {
            LOGGER.error("debug login failed userId={}, err={}", normalizedUserId, e.getMessage(), e);
            return ApiResponse.error("调试登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("is_login");
        session.removeAttribute("login_user");
        return "redirect:/logout-success";
    }

    @GetMapping("/generate-oauth2-url")
    public String generateOauth2Url(HttpServletRequest request, HttpSession session) throws Exception {
        String state = UUID.randomUUID().toString().replace("-", "");
        session.setAttribute("oauth2_state", state);
        
        String encodedRedirectUri = URLEncoder.encode(loginOauth2RedirectUri, StandardCharsets.UTF_8.toString());
        
        String oauth2Url = API_OAUTH2_AUTHORIZE_URL + "?appid=" + cropId + "&redirect_uri=" + encodedRedirectUri + "&response_type=code&scope=snsapi_privateinfo&agentid=" + agentid + "&state=" + state + "#wechat_redirect";
        
        return "redirect:" + oauth2Url;
    }

    @GetMapping("/oauth2-callback")
    public String handleOauth2Callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpSession session) {
        try {
            System.out.println("企业微信免密登录开始处理...");
            long startTime = System.currentTimeMillis();
            
            String savedState = (String) session.getAttribute("oauth2_state");
            if (savedState == null || !savedState.equals(state)) {
                String errorMessage = "无效的登录请求";
                try {
                    errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
                } catch (Exception ignored) {}
                return "redirect:/login-error?error=" + errorMessage;
            }
            System.out.println("验证state耗时：" + (System.currentTimeMillis() - startTime) + "ms");

            session.removeAttribute("oauth2_state");

            long accessTokenStart = System.currentTimeMillis();
            System.out.println("开始获取access_token...");
            String accessToken = wxworkService.getAccessToken();
            System.out.println("获取access_token耗时：" + (System.currentTimeMillis() - accessTokenStart) + "ms");

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
                return "redirect:/login-error?error=" + errorMessage;
            }

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
                return "redirect:/login-error?error=" + errorMessage;
            }

            session.setAttribute("login_user", userDetailJson);
            session.setAttribute("is_login", true);

            return "redirect:/";
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = "登录失败：" + e.getMessage();
            try {
                errorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
            } catch (Exception ignored) {}
            return "redirect:/login-error?error=" + errorMessage;
        }
    }

    private JSONObject buildDebugUserInfo(String userId) throws Exception {
        String name = userId;
        String email = null;

        JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT s.name, s.email FROM staff s WHERE s.ext_id = ? LIMIT 1";
            var result = JdbcUtils.query(sql, userId);
            if (!result.isEmpty()) {
                if (result.get(0)[0] != null) {
                    name = result.get(0)[0].toString();
                }
                if (result.get(0)[1] != null) {
                    email = result.get(0)[1].toString();
                }
            }
        } finally {
            JdbcUtils.clearConfig();
        }

        JSONObject userInfo = new JSONObject();
        userInfo.put("userid", userId);
        userInfo.put("UserId", userId);
        userInfo.put("user_id", userId);
        userInfo.put("name", name);
        userInfo.put("email", email);
        userInfo.put("avatar", "");
        userInfo.put("isAdmin", isAdminUser(userId));
        return userInfo;
    }

    private boolean isAdminUser(String userId) {
        if (StringUtils.isBlank(docService.getAdminIds()) || StringUtils.isBlank(userId)) {
            return false;
        }
        String[] adminIds = docService.getAdminIds().split(",");
        for (String adminId : adminIds) {
            if (userId.equals(adminId.trim())) {
                return true;
            }
        }
        return false;
    }

    private String resolveStaffName(String userId) {
        if (StringUtils.isBlank(userId)) {
            return null;
        }
        JdbcUtils.setCscrmConfig();
        try {
            String sql = "SELECT s.name FROM staff s WHERE s.ext_id = ? LIMIT 1";
            var result = JdbcUtils.query(sql, userId);
            if (!result.isEmpty() && result.get(0)[0] != null) {
                return result.get(0)[0].toString();
            }
        } catch (Exception e) {
            LOGGER.warn("resolveStaffName failed userId={}, err={}", userId, e.getMessage());
        } finally {
            JdbcUtils.clearConfig();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
