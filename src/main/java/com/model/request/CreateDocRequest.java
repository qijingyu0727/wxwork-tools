package com.model.request;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@Data
public class CreateDocRequest {
    @JSONField(name = "doc_type")
    private Integer docType;
    
    @JSONField(name = "doc_name")
    private String docName;
    
    @JSONField(name = "admin_users")
    private List<String> adminUsers;

    @JSONField(name = "admin_user_phone_numbers")
    private List<String> adminUserPhoneNumbers;
}