package com.model;

public class CustomerData {
    private String name;
    private Long clientId;
    private Long productId;
    private String regionId;
    private String subscriptionEndDate;
    private String isAccepted;
    private Integer notResolvedTicketCount;
    private Integer allTicketCount;
    private Integer criticalTicketCount;
    private Integer allIssueCount;
    private Integer notResolvedIssueCount;
    private Integer allBugCount;
    private Integer notResolvedBugCount;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getSubscriptionEndDate() {
        return subscriptionEndDate;
    }

    public void setSubscriptionEndDate(String subscriptionEndDate) {
        this.subscriptionEndDate = subscriptionEndDate;
    }

    public String getIsAccepted() {
        return isAccepted;
    }

    public void setIsAccepted(String isAccepted) {
        this.isAccepted = isAccepted;
    }

    public Integer getNotResolvedTicketCount() {
        return notResolvedTicketCount;
    }

    public void setNotResolvedTicketCount(Integer notResolvedTicketCount) {
        this.notResolvedTicketCount = notResolvedTicketCount;
    }

    public Integer getAllTicketCount() {
        return allTicketCount;
    }

    public void setAllTicketCount(Integer allTicketCount) {
        this.allTicketCount = allTicketCount;
    }

    public Integer getCriticalTicketCount() {
        return criticalTicketCount;
    }

    public void setCriticalTicketCount(Integer criticalTicketCount) {
        this.criticalTicketCount = criticalTicketCount;
    }

    public Integer getAllIssueCount() {
        return allIssueCount;
    }

    public void setAllIssueCount(Integer allIssueCount) {
        this.allIssueCount = allIssueCount;
    }

    public Integer getNotResolvedIssueCount() {
        return notResolvedIssueCount;
    }

    public void setNotResolvedIssueCount(Integer notResolvedIssueCount) {
        this.notResolvedIssueCount = notResolvedIssueCount;
    }

    public Integer getAllBugCount() {
        return allBugCount;
    }

    public void setAllBugCount(Integer allBugCount) {
        this.allBugCount = allBugCount;
    }

    public Integer getNotResolvedBugCount() {
        return notResolvedBugCount;
    }

    public void setNotResolvedBugCount(Integer notResolvedBugCount) {
        this.notResolvedBugCount = notResolvedBugCount;
    }
}
