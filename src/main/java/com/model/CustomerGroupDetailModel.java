package com.model;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-18 23:53
 **/
@NoArgsConstructor
@Data
public class CustomerGroupDetailModel {


    @JSONField(name = "errcode")
    private Integer errcode;
    @JSONField(name = "errmsg")
    private String errmsg;
    @JSONField(name = "group_chat")
    private GroupChatDTO groupChat;

    @NoArgsConstructor
    @Data
    public static class GroupChatDTO {
        @JSONField(name = "chat_id")
        private String chatId;
        @JSONField(name = "name")
        private String name;
        @JSONField(name = "create_time")
        private Integer createTime;
        @JSONField(name = "member_list")
        private List<MemberListDTO> memberList;
        @JSONField(name = "admin_list")
        private List<?> adminList;
        @JSONField(name = "owner")
        private String owner;

        @NoArgsConstructor
        @Data
        public static class MemberListDTO {
            @JSONField(name = "userid")
            private String userid;
            @JSONField(name = "type")
            private Integer type;
            @JSONField(name = "join_time")
            private Integer joinTime;
            @JSONField(name = "join_scene")
            private Integer joinScene;
            @JSONField(name = "invitor")
            private InvitorDTO invitor;
            @JSONField(name = "group_nickname")
            private String groupNickname;
            @JSONField(name = "name")
            private String name;

            @NoArgsConstructor
            @Data
            public static class InvitorDTO {
                @JSONField(name = "userid")
                private String userid;
            }
        }
    }
}
