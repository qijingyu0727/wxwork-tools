package com.model;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.List;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-18 11:20
 **/
@lombok.NoArgsConstructor
@lombok.Data
public class ArchiveMsgModel {
    @JSONField(name = "errcode")
    private Integer errcode;
    @JSONField(name = "errmsg")
    private String errmsg;
    @JSONField(name = "chatdata")
    private List<ChatdataDTO> chatdata;

    @lombok.NoArgsConstructor
    @lombok.Data
    public static class ChatdataDTO {
        @JSONField(name = "seq")
        private Integer seq;
        @JSONField(name = "msgid")
        private String msgid;
        @JSONField(name = "publickey_ver")
        private Integer publickeyVer;
        @JSONField(name = "encrypt_random_key")
        private String encryptRandomKey;
        @JSONField(name = "encrypt_chat_msg")
        private String encryptChatMsg;
    }
}
