package com.dao;

import com.model.ArchiveMsgInfo;
import com.model.ArchiveMsgInfoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ArchiveMsgInfoMapper {
    int countByExample(ArchiveMsgInfoExample example);

    int deleteByExample(ArchiveMsgInfoExample example);

    int deleteByPrimaryKey(Integer seq);

    int insert(ArchiveMsgInfo record);

    int insertSelective(ArchiveMsgInfo record);

    List<ArchiveMsgInfo> selectByExample(ArchiveMsgInfoExample example);

    ArchiveMsgInfo selectByPrimaryKey(Integer seq);

    int updateByExampleSelective(@Param("record") ArchiveMsgInfo record, @Param("example") ArchiveMsgInfoExample example);

    int updateByExample(@Param("record") ArchiveMsgInfo record, @Param("example") ArchiveMsgInfoExample example);

    int updateByPrimaryKeySelective(ArchiveMsgInfo record);

    int updateByPrimaryKey(ArchiveMsgInfo record);
}