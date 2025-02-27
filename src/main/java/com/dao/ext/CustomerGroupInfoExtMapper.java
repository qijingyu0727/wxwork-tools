package com.dao.ext;

import com.model.CustomerGroupInfo;

import java.util.List;

public interface CustomerGroupInfoExtMapper {
    int batchInsert(List<CustomerGroupInfo> record);
}