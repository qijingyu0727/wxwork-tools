package com.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class CreateDocModel {
    @JSONField(name = "errcode")
    private Integer errcode;
    
    @JSONField(name = "errmsg")
    private String errmsg;
    
    @JSONField(name = "url")
    private String url;
    
    @JSONField(name = "docid")
    private String docid;
}