package com.dao;

import com.model.CustomerGroupInfo;
import com.model.CustomerGroupInfoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CustomerGroupInfoMapper {
    int countByExample(CustomerGroupInfoExample example);

    int deleteByExample(CustomerGroupInfoExample example);

    int deleteByPrimaryKey(String roomId);

    int insert(CustomerGroupInfo record);

    int insertSelective(CustomerGroupInfo record);

    List<CustomerGroupInfo> selectByExample(CustomerGroupInfoExample example);

    CustomerGroupInfo selectByPrimaryKey(String roomId);

    int updateByExampleSelective(@Param("record") CustomerGroupInfo record, @Param("example") CustomerGroupInfoExample example);

    int updateByExample(@Param("record") CustomerGroupInfo record, @Param("example") CustomerGroupInfoExample example);

    int updateByPrimaryKeySelective(CustomerGroupInfo record);

    int updateByPrimaryKey(CustomerGroupInfo record);
}