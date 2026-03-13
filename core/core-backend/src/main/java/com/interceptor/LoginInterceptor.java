package com.interceptor;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 登录状态拦截器
 * 拦截所有需要登录才能访问的API请求
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    // 不需要登录即可访问的URL路径
    private static final String[] EXCLUDE_URLS = {
            "/wechat/work/login/generate-qrcode",
            "/wechat/work/login/callback",
            "/wechat/work/login/check-login",
            "/wechat/work/login/generate-oauth2-url",
            "/wechat/work/login/oauth2-callback",
            "/wechat/work/callback",
            "/logout-success.html",
            "/login-error.html"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 检查请求路径是否需要排除
        // String requestUri = request.getRequestURI();
        // for (String excludeUrl : EXCLUDE_URLS) {
        //     if (requestUri.startsWith(excludeUrl)) {
        //         return true;
        //     }
        // }

        // //检查登录状态
        // Boolean isLogin = (Boolean) request.getSession().getAttribute("is_login");
        
        // if (isLogin == null || !isLogin) {
        //     // 未登录
        //     if (requestUri.startsWith("/api")) {
        //         // 对于API请求，返回未授权的错误信息
        //         response.setContentType("application/json;charset=UTF-8");
        //         PrintWriter writer = response.getWriter();
        //         JSONObject result = new JSONObject();
        //         result.put("success", false);
        //         result.put("errmsg", "请先登录");
        //         writer.write(result.toJSONString());
        //         writer.flush();
        //         writer.close();
        //         return false;
        //     } else {
        //         // 对于非API请求（如首页），重定向到企业微信登录页面
        //         response.sendRedirect("/wechat/work/login/generate-qrcode");
        //         return false;
        //     }
        // }

        // 已登录的请求，继续处理
        return true;
    }
}