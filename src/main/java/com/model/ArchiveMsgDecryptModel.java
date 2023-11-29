package com.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-18 16:41
 **/
@NoArgsConstructor
@Data
public class ArchiveMsgDecryptModel {

    @JSONField(name = "msgid")
    private String msgid;
    @JSONField(name = "action")
    private String action;
    @JSONField(name = "from")
    private String from;
    @JSONField(name = "tolist")
    private List<String> tolist;
    @JSONField(name = "roomid")
    private String roomid;
    @JSONField(name = "msgtime")
    private Long msgtime;
    @JSONField(name = "msgtype")
    private String msgtype;
    @JSONField(name = "text")
    private TextDTO text;

    @NoArgsConstructor
    @Data
    public static class TextDTO {
        @JSONField(name = "content")
        private String content;
    }
}
