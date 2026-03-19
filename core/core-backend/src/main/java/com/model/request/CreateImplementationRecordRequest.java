package com.model.request;

import java.util.ArrayList;
import java.util.List;

public class CreateImplementationRecordRequest {
    private String extChatId;
    private Long subscriptionId;
    private Long clientId;
    private Long productId;
    private String regionId;
    private String editorUserId;
    private String deploymentDate;
    private String deploymentMethod;
    private String version;
    private List<String> assetTypes = new ArrayList<>();
    private String assetCount;
    private String virtualizationType;
    private String applicationServer;
    private String databaseSync;
    private String databaseExternal;
    private String redisExternal;
    private String sharedNfs;
    private String customerFocus;
    private String deploymentArchitecture;
    private String deploymentRecord;
    private List<String> authMethods = new ArrayList<>();
    private List<String> businessDirections = new ArrayList<>();
    private String backupMethod;
    private String dataEaseDatabase;
    private String dorisUsage;
    private String dataSourceType;
    private String dataScale;
    private String embeddedMode;
    private String customerJoined;
    private String analysisDirection;
    private String remainingIssues;
    private String remark;

    public String getExtChatId() {
        return extChatId;
    }

    public void setExtChatId(String extChatId) {
        this.extChatId = extChatId;
    }

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

    public String getEditorUserId() {
        return editorUserId;
    }

    public void setEditorUserId(String editorUserId) {
        this.editorUserId = editorUserId;
    }

    public String getDeploymentDate() {
        return deploymentDate;
    }

    public void setDeploymentDate(String deploymentDate) {
        this.deploymentDate = deploymentDate;
    }

    public String getDeploymentMethod() {
        return deploymentMethod;
    }

    public void setDeploymentMethod(String deploymentMethod) {
        this.deploymentMethod = deploymentMethod;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getAssetTypes() {
        return assetTypes;
    }

    public void setAssetTypes(List<String> assetTypes) {
        this.assetTypes = assetTypes != null ? assetTypes : new ArrayList<>();
    }

    public String getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(String assetCount) {
        this.assetCount = assetCount;
    }

    public String getVirtualizationType() {
        return virtualizationType;
    }

    public void setVirtualizationType(String virtualizationType) {
        this.virtualizationType = virtualizationType;
    }

    public String getApplicationServer() {
        return applicationServer;
    }

    public void setApplicationServer(String applicationServer) {
        this.applicationServer = applicationServer;
    }

    public String getDatabaseSync() {
        return databaseSync;
    }

    public void setDatabaseSync(String databaseSync) {
        this.databaseSync = databaseSync;
    }

    public String getDatabaseExternal() {
        return databaseExternal;
    }

    public void setDatabaseExternal(String databaseExternal) {
        this.databaseExternal = databaseExternal;
    }

    public String getRedisExternal() {
        return redisExternal;
    }

    public void setRedisExternal(String redisExternal) {
        this.redisExternal = redisExternal;
    }

    public String getSharedNfs() {
        return sharedNfs;
    }

    public void setSharedNfs(String sharedNfs) {
        this.sharedNfs = sharedNfs;
    }

    public String getCustomerFocus() {
        return customerFocus;
    }

    public void setCustomerFocus(String customerFocus) {
        this.customerFocus = customerFocus;
    }

    public String getDeploymentArchitecture() {
        return deploymentArchitecture;
    }

    public void setDeploymentArchitecture(String deploymentArchitecture) {
        this.deploymentArchitecture = deploymentArchitecture;
    }

    public String getDeploymentRecord() {
        return deploymentRecord;
    }

    public void setDeploymentRecord(String deploymentRecord) {
        this.deploymentRecord = deploymentRecord;
    }

    public List<String> getAuthMethods() {
        return authMethods;
    }

    public void setAuthMethods(List<String> authMethods) {
        this.authMethods = authMethods != null ? authMethods : new ArrayList<>();
    }

    public List<String> getBusinessDirections() {
        return businessDirections;
    }

    public void setBusinessDirections(List<String> businessDirections) {
        this.businessDirections = businessDirections != null ? businessDirections : new ArrayList<>();
    }

    public String getBackupMethod() {
        return backupMethod;
    }

    public void setBackupMethod(String backupMethod) {
        this.backupMethod = backupMethod;
    }

    public String getDataEaseDatabase() {
        return dataEaseDatabase;
    }

    public void setDataEaseDatabase(String dataEaseDatabase) {
        this.dataEaseDatabase = dataEaseDatabase;
    }

    public String getDorisUsage() {
        return dorisUsage;
    }

    public void setDorisUsage(String dorisUsage) {
        this.dorisUsage = dorisUsage;
    }

    public String getDataSourceType() {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType) {
        this.dataSourceType = dataSourceType;
    }

    public String getDataScale() {
        return dataScale;
    }

    public void setDataScale(String dataScale) {
        this.dataScale = dataScale;
    }

    public String getEmbeddedMode() {
        return embeddedMode;
    }

    public void setEmbeddedMode(String embeddedMode) {
        this.embeddedMode = embeddedMode;
    }

    public String getCustomerJoined() {
        return customerJoined;
    }

    public void setCustomerJoined(String customerJoined) {
        this.customerJoined = customerJoined;
    }

    public String getAnalysisDirection() {
        return analysisDirection;
    }

    public void setAnalysisDirection(String analysisDirection) {
        this.analysisDirection = analysisDirection;
    }

    public String getRemainingIssues() {
        return remainingIssues;
    }

    public void setRemainingIssues(String remainingIssues) {
        this.remainingIssues = remainingIssues;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
