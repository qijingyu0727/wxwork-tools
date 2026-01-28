package com.model.template.v1;

import lombok.Data;

import java.util.List;

@Data
public class TemplateMarketDTO {
    private String id;
    private String title;
    private String summary;
    private Long updateTime;
    private Integer visits;
    private List<MarketCategory> categories;
}
