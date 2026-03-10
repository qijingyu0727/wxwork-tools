package com.model;

public class ProductVersionSnapshot {
    private String productAlias;
    private String version;
    private Long versionTs;
    private String source;

    public String getProductAlias() {
        return productAlias;
    }

    public void setProductAlias(String productAlias) {
        this.productAlias = productAlias;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Long getVersionTs() {
        return versionTs;
    }

    public void setVersionTs(Long versionTs) {
        this.versionTs = versionTs;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
