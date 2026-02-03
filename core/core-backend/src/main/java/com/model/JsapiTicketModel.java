package com.model;

import com.alibaba.fastjson.annotation.JSONField;

public class JsapiTicketModel {
    @JSONField(name = "errcode")
    private Integer errcode;

    @JSONField(name = "errmsg")
    private String errmsg;

    @JSONField(name = "ticket")
    private String ticket;

    @JSONField(name = "expires_in")
    private Integer expiresIn;

    public Integer getErrcode() {
        return errcode;
    }

    public void setErrcode(Integer errcode) {
        this.errcode = errcode;
    }

    public String getErrmsg() {
        return errmsg;
    }

    public void setErrmsg(String errmsg) {
        this.errmsg = errmsg;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
}
