package com.model;

import lombok.Data;
import java.util.Map;

@Data
public class ApiResponse<T> {
    private boolean success; // 前端判断成功/失败的标志
    private String message;  // 成功消息
    private String errmsg;   // 错误消息（与前端错误处理兼容）
    private T data;          // 业务数据
    private Map<String, Object> pagination; // 分页信息（如需要）

    // 成功响应（带数据）
    public static <T> ApiResponse<T> success(T data, Map<String, Object> pagination) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage("操作成功");
        response.setData(data);
        response.setPagination(pagination);
        return response;
    }

    // 成功响应（无分页）
    public static <T> ApiResponse<T> success(T data) {
        return success(data, null);
    }

    // 失败响应（兼容前端errmsg读取）
    public static <T> ApiResponse<T> error(String errmsg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setErrmsg(errmsg);
        response.setMessage(errmsg); // 保持message与errmsg一致，兼容前端不同读取方式
        return response;
    }
}