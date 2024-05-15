package com.service;

import com.alibaba.fastjson.JSONObject;
import com.dao.TemplateLabelMapper;
import com.dao.TemplateMapper;
import com.model.Template;
import com.model.TemplateExample;
import com.model.TemplateLabel;
import com.model.TemplateLabelExample;
import com.model.template.v1.MarketCategory;
import com.model.template.v1.TemplateMarketDTO;
import com.model.template.v2.*;
import com.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class TemplateService {

    @Value("${template_market_url_v1:https://dataease.io/templates}")
    private String template_market_url_v1;
    private final static String TEMPLATES_LIST_V1 = "/api/content/posts?page=0&size=2000";

    @Value("${template_market_url_v2:https://templates-de.fit2cloud.com}")
    private String template_market_url_v2;
    private final static String TEMPLATES_LIST_V2 = "/apis/api.store.halo.run/v1alpha1/applications?page=1&size=2000";
    private final static String LABEL_LIST_V2 = "/upload/meta_data.json";
    private final static String TEMPLATE_INFO_V2 = "/apis/api.store.halo.run/v1alpha1/applications/%s";
    @Resource
    private TemplateMapper templateMapper;
    @Resource
    private TemplateLabelMapper templateLabelMapper;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public void sync() {
        try {
            Exception exception = null;
            try {
                deleteAll();
            } catch (Exception e) {
                exception = e;
            }
            try {
                syncDataEaseV1();
            } catch (Exception e) {
                exception = e;
            }
            try {
                syncDataEaseV2();
            } catch (Exception e) {
                exception = e;
            }
            if (exception != null) {
                throw exception;
            }
        } catch (Exception e) {
            logger.error("sync template error: ", e);
        }
    }

    public void deleteAll() {
        templateMapper.deleteByExample(new TemplateExample());
        templateLabelMapper.deleteByExample(new TemplateLabelExample());
    }

    public String marketGet(String url, String accessKey) {
        HttpClientConfig config = new HttpClientConfig();
        config.addHeader("API-Authorization", accessKey);
        return HttpUtil.get(url, config);
    }

    public void syncDataEaseV1() {
        String result = null;
        try {
            result = marketGet(template_market_url_v1 + TEMPLATES_LIST_V1, "dataease");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<TemplateMarketDTO> postsResult = JSONObject.parseObject(result).getJSONObject("data").getJSONArray("content").toJavaList(TemplateMarketDTO.class);
        postsResult.forEach(markDTO -> {
            List<String> tags = markDTO.getCategories().stream().map(MarketCategory::getName).collect(Collectors.toList());
            tags.remove("全部");
            if (!tags.contains("应用系列")) {
                Long uuid = IDUtils.snowID();

                // insert template
                Template template = new Template();
                template.setId(uuid);
                template.setName(markDTO.getTitle());
                template.setDescription(markDTO.getSummary());
                template.setType("PANEL");
                template.setVersion("V1");
                template.setUpdateTime(markDTO.getUpdateTime());
                template.setView(markDTO.getVisits());
                templateMapper.insertSelective(template);

                // insert label
                tags.forEach(tag -> {
                    TemplateLabel templateLabel = new TemplateLabel();
                    templateLabel.setTemplateId(uuid);
                    templateLabel.setLabel(tag);
                    templateLabelMapper.insertSelective(templateLabel);
                });
            } else {
                tags.remove("应用系列");

                // insert template
                Long uuid = IDUtils.snowID();
                Template template = new Template();
                template.setId(uuid);
                template.setName(markDTO.getTitle());
                template.setDescription(markDTO.getSummary());
                template.setType("APPS");
                template.setVersion("V1");
                template.setUpdateTime(markDTO.getUpdateTime());
                template.setView(markDTO.getVisits());
                templateMapper.insertSelective(template);

                // insert label
                tags.forEach(tag -> {
                    TemplateLabel templateLabel = new TemplateLabel();
                    templateLabel.setTemplateId(uuid);
                    templateLabel.setLabel(tag);
                    templateLabelMapper.insertSelective(templateLabel);
                });
            }
        });
    }

    public List<MarketMetaDataVO> getCategoriesV2() {
        try {
            String resultStr = marketGet(template_market_url_v2 + LABEL_LIST_V2, null);
            MarketMetaDataBaseResponse metaData = JsonUtil.parseObject(resultStr, MarketMetaDataBaseResponse.class);
            return metaData.getLabels();
        } catch (Exception e) {
            logger.error("模板市场分类获取错误", e);
        }
        return new ArrayList<>();
    }

    public void syncDataEaseV2() throws ParseException {
        String result = null;
        try {
            result = marketGet(template_market_url_v2 + TEMPLATES_LIST_V2, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        MarketTemplateV2BaseResponse postsResult = JsonUtil.parseObject(result, MarketTemplateV2BaseResponse.class);
        Map<String, String> categoryVO = getCategoriesV2().stream().filter(node -> !"全部".equalsIgnoreCase(node.getLabel())).collect(Collectors.toMap(MarketMetaDataVO::getValue, MarketMetaDataVO::getLabel));
        if (postsResult != null) {
            for (MarketTemplateV2ItemResult item : postsResult.getItems()) {
                Long uuid = IDUtils.snowID();

                // insert template
                Template template = new Template();
                template.setId(uuid);
                template.setName(item.getApplication().getSpec().getDisplayName());
                template.setDescription(item.getApplication().getSpec().getDescription());
                template.setType(item.getApplication().getSpec().getTemplateType());
                template.setVersion(item.getApplication().getSpec().getDeVersion());

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'");
                Date date = sdf.parse(item.getApplication().getMetadata().getCreationTimestamp());
                template.setUpdateTime(date.getTime());

                String applicationName = item.getApplication().getMetadata().getName();
                MarketTemplateV2Info templateInfo = JsonUtil.parseObject(marketGet(String.format(template_market_url_v2 + TEMPLATE_INFO_V2, applicationName), null), MarketTemplateV2Info.class);
                template.setView(templateInfo.getViews());
                template.setDownload(templateInfo.getDownloads());
                templateMapper.insertSelective(template);

                // insert label
                TemplateLabel label = new TemplateLabel();
                label.setTemplateId(uuid);
                label.setLabel(categoryVO.get(item.getApplication().getSpec().getLabel()));
                templateLabelMapper.insertSelective(label);
            }
        }
    }

}
