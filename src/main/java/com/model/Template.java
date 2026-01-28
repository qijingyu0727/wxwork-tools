package com.model;

import java.util.Date;

public class Template {
    private Long id;

    private String name;

    private String description;

    private String type;

    private String version;

    private Long updateTime;

    private Integer view;

    private Integer download;

    private Date syncDate;

    private Integer incrementView;

    private Integer incrementDownload;

    private String domain;

    private Boolean isApp;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? null : description.trim();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type == null ? null : type.trim();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? null : version.trim();
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getView() {
        return view;
    }

    public void setView(Integer view) {
        this.view = view;
    }

    public Integer getDownload() {
        return download;
    }

    public void setDownload(Integer download) {
        this.download = download;
    }

    public Date getSyncDate() {
        return syncDate;
    }

    public void setSyncDate(Date syncDate) {
        this.syncDate = syncDate;
    }

    public Integer getIncrementView() {
        return incrementView;
    }

    public void setIncrementView(Integer incrementView) {
        this.incrementView = incrementView;
    }

    public Integer getIncrementDownload() {
        return incrementDownload;
    }

    public void setIncrementDownload(Integer incrementDownload) {
        this.incrementDownload = incrementDownload;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain == null ? null : domain.trim();
    }

    public Boolean getIsApp() {
        return isApp;
    }

    public void setIsApp(Boolean isApp) {
        this.isApp = isApp;
    }
}