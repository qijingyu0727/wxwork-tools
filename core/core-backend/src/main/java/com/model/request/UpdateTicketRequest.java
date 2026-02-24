package com.model.request;

public class UpdateTicketRequest {
    private String ticketId;
    private Boolean urgent;
    private String customerSentiment;
    private String ownerName;
    private String ownerId;
    private String comment;

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
}
