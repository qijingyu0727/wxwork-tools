package com.model.request;

public class CreateMaintenanceRecordRequest {
    private Long clientId;
    private String ownerId;
    private String editorUserId;
    private String maintenanceTypes;
    private String maintenanceTitle;
    private Long maintenanceTime;
    private String regionId;
    private String maintenanceVersion;
    private String maintenanceContext;
    private Long productId;
    private String extChatId;

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public String getEditorUserId() {
        return editorUserId;
    }

    public void setEditorUserId(String editorUserId) {
        this.editorUserId = editorUserId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getMaintenanceTypes() {
        return maintenanceTypes;
    }

    public void setMaintenanceTypes(String maintenanceTypes) {
        this.maintenanceTypes = maintenanceTypes;
    }

    public String getMaintenanceTitle() {
        return maintenanceTitle;
    }

    public void setMaintenanceTitle(String maintenanceTitle) {
        this.maintenanceTitle = maintenanceTitle;
    }

    public Long getMaintenanceTime() {
        return maintenanceTime;
    }

    public void setMaintenanceTime(Long maintenanceTime) {
        this.maintenanceTime = maintenanceTime;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getMaintenanceVersion() {
        return maintenanceVersion;
    }

    public void setMaintenanceVersion(String maintenanceVersion) {
        this.maintenanceVersion = maintenanceVersion;
    }

    public String getMaintenanceContext() {
        return maintenanceContext;
    }

    public void setMaintenanceContext(String maintenanceContext) {
        this.maintenanceContext = maintenanceContext;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getExtChatId() {
        return extChatId;
    }

    public void setExtChatId(String extChatId) {
        this.extChatId = extChatId;
    }
}
