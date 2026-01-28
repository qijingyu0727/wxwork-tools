package com.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class GetUserIdModel {
    @JSONField(name = "errcode")
    private Integer errcode;
    
    @JSONField(name = "errmsg")
    private String errmsg;
    
    @JSONField(name = "userid")
    private String userid;

}