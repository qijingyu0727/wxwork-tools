package com.model.request;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-18 15:09
 **/
@NoArgsConstructor
@Data
public class CustomerGroupDetailsRequest {

    @JSONField(name = "chat_id")
    private String chatId;
    @JSONField(name = "need_name")
    private Integer needName;
}
