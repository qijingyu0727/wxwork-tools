package com.model.request;

public class UpdateTicketRequest {
    private String ticketId;
    private Boolean urgent;
    private String customerSentiment;
    private String ownerName;
    private String ownerId;
    private String comment;
    private Integer reminderCycle;
    private Integer status;
    private Boolean resolved;
    private String trackingLinks;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean urgent) {
        this.urgent = urgent;
    }

    public String getCustomerSentiment() {
        return customerSentiment;
    }

    public void setCustomerSentiment(String customerSentiment) {
        this.customerSentiment = customerSentiment;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getReminderCycle() {
        return reminderCycle;
    }

    public void setReminderCycle(Integer reminderCycle) {
        this.reminderCycle = reminderCycle;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }

    public String getTrackingLinks() {
        return trackingLinks;
    }

    public void setTrackingLinks(String trackingLinks) {
        this.trackingLinks = trackingLinks;
    }
}
