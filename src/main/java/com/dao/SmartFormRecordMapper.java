package com.dao;

import com.model.SmartFormRecord;
import com.model.SmartFormRecordExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmartFormRecordMapper {
    long countByExample(SmartFormRecordExample example);

    int deleteByExample(SmartFormRecordExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(SmartFormRecord record);

    int insertSelective(SmartFormRecord record);

    List<SmartFormRecord> selectByExample(SmartFormRecordExample example);

    SmartFormRecord selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") SmartFormRecord record, @Param("example") SmartFormRecordExample example);

    int updateByExample(@Param("record") SmartFormRecord record, @Param("example") SmartFormRecordExample example);

    int updateByPrimaryKeySelective(SmartFormRecord record);

    int updateByPrimaryKey(SmartFormRecord record);
}