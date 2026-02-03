package com.model;

public class ServiceRecord {
    private Long id;
    private String maintenanceTypes;
    private String maintenanceVersion;
    private String maintenanceTitle;
    private String maintenanceContext;
    private String maintenanceTime;
    private String creatorName;
    private String createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMaintenanceTypes() {
        return maintenanceTypes;
    }

    public void setMaintenanceTypes(String maintenanceTypes) {
        this.maintenanceTypes = maintenanceTypes;
    }

    public String getMaintenanceVersion() {
        return maintenanceVersion;
    }

    public void setMaintenanceVersion(String maintenanceVersion) {
        this.maintenanceVersion = maintenanceVersion;
    }

    public String getMaintenanceTitle() {
        return maintenanceTitle;
    }

    public void setMaintenanceTitle(String maintenanceTitle) {
        this.maintenanceTitle = maintenanceTitle;
    }

    public String getMaintenanceContext() {
        return maintenanceContext;
    }

    public void setMaintenanceContext(String maintenanceContext) {
        this.maintenanceContext = maintenanceContext;
    }

    public String getMaintenanceTime() {
        return maintenanceTime;
    }

    public void setMaintenanceTime(String maintenanceTime) {
        this.maintenanceTime = maintenanceTime;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}
