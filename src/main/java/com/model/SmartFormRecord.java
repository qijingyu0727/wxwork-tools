package com.model;

public class SmartFormRecord {
    private Integer id;

    private String docName;

    private String docId;

    private String adminPhoneNumbers;

    private String adminUserIds;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName == null ? null : docName.trim();
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId == null ? null : docId.trim();
    }

    public String getAdminPhoneNumbers() {
        return adminPhoneNumbers;
    }

    public void setAdminPhoneNumbers(String adminPhoneNumbers) {
        this.adminPhoneNumbers = adminPhoneNumbers == null ? null : adminPhoneNumbers.trim();
    }

    public String getAdminUserIds() {
        return adminUserIds;
    }

    public void setAdminUserIds(String adminUserIds) {
        this.adminUserIds = adminUserIds == null ? null : adminUserIds.trim();
    }
}