package com.model.request;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class GetUserIdRequest {
    @JSONField(name = "mobile")
    private String mobile;

}