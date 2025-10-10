package com.controller;


import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.model.ApiResponse;
import com.model.SmartFormRecord;
import com.model.request.CreateDocRequest;
import com.service.DocService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@RestController
@RequestMapping("/api/doc")
public class DocController {

    @Resource
    private DocService docService;

    // 创建文档接口（原返回SmartFormRecord，改为封装响应）
    @PostMapping("/create")
    public ApiResponse<SmartFormRecord> createDoc(@RequestBody CreateDocRequest request) {
        try {
            SmartFormRecord record = docService.createDoc(request);
            return ApiResponse.success(record); // 成功时返回data
        } catch (Exception e) {
            // 失败时返回errmsg，前端可通过result.errmsg读取
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    // 查询接口（原返回Map，改为封装响应）
    @GetMapping("/search")
    public ApiResponse<List<SmartFormRecord>> searchSmartForms(
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) String adminName,
            @RequestParam(required = false) String adminPhone,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            List<SmartFormRecord> records = docService.searchSmartForms(tableName, adminName, adminPhone);
            Page<SmartFormRecord> page = (records instanceof Page) ? (Page<SmartFormRecord>) records : new Page<>(pageNum, pageSize);

            // 封装分页信息（与前端预期的pagination结构一致）
            Map<String, Object> pageMap = new HashMap<>();
            pageMap.put("pageNum", page.getPageNum());
            pageMap.put("pageSize", page.getPageSize());
            pageMap.put("total", page.getTotal());
            pageMap.put("totalPages", page.getPages());

            return ApiResponse.success(page.getResult(), pageMap); // 包含data和pagination
        } catch (Exception e) {
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }
}