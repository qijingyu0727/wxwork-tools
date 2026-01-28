package com.dao;

import com.model.TemplateLabel;
import com.model.TemplateLabelExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TemplateLabelMapper {
    long countByExample(TemplateLabelExample example);

    int deleteByExample(TemplateLabelExample example);

    int insert(TemplateLabel record);

    int insertSelective(TemplateLabel record);

    List<TemplateLabel> selectByExample(TemplateLabelExample example);

    int updateByExampleSelective(@Param("record") TemplateLabel record, @Param("example") TemplateLabelExample example);

    int updateByExample(@Param("record") TemplateLabel record, @Param("example") TemplateLabelExample example);
}