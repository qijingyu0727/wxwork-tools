package com.service;

import com.alibaba.fastjson.JSONObject;
import com.dao.ArchiveMsgInfoMapper;
import com.model.ArchiveMsgInfo;
import com.model.ArchiveMsgInfoExample;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-19 00:04
 **/
@Service
public class ArchiveMsgInfoService {

    @Resource
    private ArchiveMsgInfoMapper archiveMsgInfoMapper;

    /**
     * 取出最大的 seq
     * @return
     */
    public Integer getMaxSeq () {
        ArchiveMsgInfoExample archiveMsgInfoExample = new ArchiveMsgInfoExample();
        archiveMsgInfoExample.setOrderByClause(" seq desc ");
        List<ArchiveMsgInfo> archiveMsgInfos = archiveMsgInfoMapper.selectByExample(archiveMsgInfoExample);

        if (CollectionUtils.isEmpty(archiveMsgInfos)) {
            return 0;
        } else {
            return archiveMsgInfos.get(0).getSeq();
        }
    }

    public void insert (ArchiveMsgInfo archiveMsgInfo) {

        if (null == archiveMsgInfoMapper.selectByPrimaryKey(archiveMsgInfo.getSeq())) {
            archiveMsgInfoMapper.insertSelective(archiveMsgInfo);
        }
    }

}
