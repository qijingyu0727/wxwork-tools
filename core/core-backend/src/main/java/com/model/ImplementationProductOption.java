package com.model;

public class ImplementationProductOption {
    private Long productId;
    private String productName;
    private String productAlias;
    private String template;
    private String formType;

    public ImplementationProductOption() {
    }

    public ImplementationProductOption(Long productId, String productName, String productAlias, String template, String formType) {
        this.productId = productId;
        this.productName = productName;
        this.productAlias = productAlias;
        this.template = template;
        this.formType = formType;
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
}
