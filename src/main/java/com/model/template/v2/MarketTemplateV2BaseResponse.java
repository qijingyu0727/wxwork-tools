package com.model.template.v2;

import lombok.Data;

import java.util.List;

/**
 * @author : WangJiaHao
 * @date : 2023/11/17 13:41
 */
@Data
public class MarketTemplateV2BaseResponse {
    private List<MarketTemplateV2ItemResult> items;
}
