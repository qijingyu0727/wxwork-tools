package com.model;

public class ArchiveMsgInfo {
    private Integer seq;

    private Integer publickeyVer;

    private String roomId;

    private Long msgTime;

    private String context;

    private String sender;

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Integer getPublickeyVer() {
        return publickeyVer;
    }

    public void setPublickeyVer(Integer publickeyVer) {
        this.publickeyVer = publickeyVer;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId == null ? null : roomId.trim();
    }

    public Long getMsgTime() {
        return msgTime;
    }

    public void setMsgTime(Long msgTime) {
        this.msgTime = msgTime;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context == null ? null : context.trim();
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender == null ? null : sender.trim();
    }
}