package com.model;

import java.util.ArrayList;
import java.util.List;

public class ImplementationCreateContext {
    private Long subscriptionId;
    private Long clientId;
    private String clientName;
    private String contractNumber;
    private Long productId;
    private String productName;
    private String serviceTypeName;
    private String salesName;
    private String regionId;
    private String regionName;
    private String subscriptionStartDate;
    private String supportEndDate;
    private String defaultSubmitterUserId;
    private String defaultSubmitterName;
    private String productAlias;
    private String template;
    private String formType;
    private String subscriptionDisplayText;
    private List<String> availableVersions = new ArrayList<>();
    private List<ImplementationProductOption> productOptions = new ArrayList<>();
    private boolean draftMode;

    public Long getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getServiceTypeName() {
        return serviceTypeName;
    }

    public void setServiceTypeName(String serviceTypeName) {
        this.serviceTypeName = serviceTypeName;
    }

    public String getSalesName() {
        return salesName;
    }

    public void setSalesName(String salesName) {
        this.salesName = salesName;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getSubscriptionStartDate() {
        return subscriptionStartDate;
    }

    public void setSubscriptionStartDate(String subscriptionStartDate) {
        this.subscriptionStartDate = subscriptionStartDate;
    }

    public String getSupportEndDate() {
        return supportEndDate;
    }

    public void setSupportEndDate(String supportEndDate) {
        this.supportEndDate = supportEndDate;
    }

    public String getDefaultSubmitterUserId() {
        return defaultSubmitterUserId;
    }

    public void setDefaultSubmitterUserId(String defaultSubmitterUserId) {
        this.defaultSubmitterUserId = defaultSubmitterUserId;
    }

    public String getDefaultSubmitterName() {
        return defaultSubmitterName;
    }

    public void setDefaultSubmitterName(String defaultSubmitterName) {
        this.defaultSubmitterName = defaultSubmitterName;
    }

    public String getProductAlias() {
        return productAlias;
    }

    public void setProductAlias(String productAlias) {
        this.productAlias = productAlias;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getFormType() {
        return formType;
    }

    public void setFormType(String formType) {
        this.formType = formType;
    }

    public String getSubscriptionDisplayText() {
        return subscriptionDisplayText;
    }

    public void setSubscriptionDisplayText(String subscriptionDisplayText) {
        this.subscriptionDisplayText = subscriptionDisplayText;
    }

    public List<String> getAvailableVersions() {
        return availableVersions;
    }

    public void setAvailableVersions(List<String> availableVersions) {
        this.availableVersions = availableVersions != null ? availableVersions : new ArrayList<>();
    }

    public List<ImplementationProductOption> getProductOptions() {
        return productOptions;
    }

    public void setProductOptions(List<ImplementationProductOption> productOptions) {
        this.productOptions = productOptions != null ? productOptions : new ArrayList<>();
    }

    public boolean isDraftMode() {
        return draftMode;
    }

    public void setDraftMode(boolean draftMode) {
        this.draftMode = draftMode;
    }
}
