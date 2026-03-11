package com.model;

public class AcceptanceStatusData {
    private String isAccepted;
    private String acceptanceStatusCode;
    private String needAcceptanceReport;
    private String accepted;

    public String getIsAccepted() {
        return isAccepted;
    }

    public void setIsAccepted(String isAccepted) {
        this.isAccepted = isAccepted;
    }

    public String getAcceptanceStatusCode() {
        return acceptanceStatusCode;
    }

    public void setAcceptanceStatusCode(String acceptanceStatusCode) {
        this.acceptanceStatusCode = acceptanceStatusCode;
    }

    public String getNeedAcceptanceReport() {
        return needAcceptanceReport;
    }

    public void setNeedAcceptanceReport(String needAcceptanceReport) {
        this.needAcceptanceReport = needAcceptanceReport;
    }

    public String getAccepted() {
        return accepted;
    }

    public void setAccepted(String accepted) {
        this.accepted = accepted;
    }
}
