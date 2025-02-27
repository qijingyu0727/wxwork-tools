package com.service;

import com.dao.CustomerGroupInfoMapper;
import com.dao.ext.CustomerGroupInfoExtMapper;
import com.model.CustomerGroupInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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


    @Resource
    private CustomerGroupInfoExtMapper customerGroupInfoExtMapper;


    public List<CustomerGroupInfo> list () {
        return customerGroupInfoMapper.selectByExample(null);
    }


    public void insert (CustomerGroupInfo customerGroupInfo){
        if ( null == customerGroupInfoMapper.selectByPrimaryKey(customerGroupInfo.getRoomId())){
            customerGroupInfoMapper.insertSelective(customerGroupInfo);
        }
    }

    public void batchInsert (List<CustomerGroupInfo> customerGroupInfos){
        if (!CollectionUtils.isEmpty(customerGroupInfos)) {
            //过滤掉已经存储过的
            List<CustomerGroupInfo> customerGroupInfoList = list();
            List<String> savedRoomId = new ArrayList<>();
            if (!CollectionUtils.isEmpty(customerGroupInfoList)) {
                savedRoomId.addAll(customerGroupInfoList.stream().map(CustomerGroupInfo::getRoomId).collect(Collectors.toList()));
            }
            List<String> toSaveRoomId = new ArrayList<>();
            toSaveRoomId.addAll(customerGroupInfos.stream().map(CustomerGroupInfo::getRoomId).collect(Collectors.toList()));
            //只存储未储存的
            toSaveRoomId.removeAll(savedRoomId);
            List<CustomerGroupInfo> toSaveInfos = customerGroupInfos.stream().filter(customerGroupInfo -> toSaveRoomId.contains(customerGroupInfo.getRoomId())).collect(Collectors.toList());
            customerGroupInfoExtMapper.batchInsert(toSaveInfos);
        }
    }


}
