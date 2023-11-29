package com.service;

import com.dao.CustomerGroupInfoMapper;
import com.model.CustomerGroupInfo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-18 23:55
 **/
@Service
public class CustomerGroupInfoService {

    @Resource
    private CustomerGroupInfoMapper customerGroupInfoMapper;

    public void insert (CustomerGroupInfo customerGroupInfo){
        if ( null == customerGroupInfoMapper.selectByPrimaryKey(customerGroupInfo.getRoomId())){
            customerGroupInfoMapper.insertSelective(customerGroupInfo);
        }
    }



}
