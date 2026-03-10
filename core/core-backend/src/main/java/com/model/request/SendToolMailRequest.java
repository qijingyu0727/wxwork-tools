package com.model.request;

public class SendToolMailRequest {
    private String toEmail;
    private String ccEmails;
    private String extChatId;
    private String customerName;
    private String latestVersion;

    public String getToEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public String getCcEmails() {
        return ccEmails;
    }

    public void setCcEmails(String ccEmails) {
        this.ccEmails = ccEmails;
    }

    public String getExtChatId() {
        return extChatId;
    }

    public void setExtChatId(String extChatId) {
        this.extChatId = extChatId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }
}
