package com.service;

import com.dao.SmartFormRecordMapper;
import com.model.CreateDocModel;
import com.model.SmartFormRecord;
import com.model.SmartFormRecordExample;
import com.model.request.CreateDocRequest;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocService {

    @Resource
    private WxworkService wxworkService;

    @Resource
    private SmartFormRecordMapper smartFormRecordMapper;


    public SmartFormRecord createDoc(CreateDocRequest request) throws Exception {
        CreateDocModel createDocModel = wxworkService.createDoc(request);
        SmartFormRecord record = new SmartFormRecord();
        record.setDocId(createDocModel.getDocid());
        record.setDocName(request.getDocName());
        record.setAdminPhoneNumbers(StringUtils.join(request.getAdminUserPhoneNumbers(),','));
        record.setAdminUserIds(StringUtils.join(request.getAdminUsers(),','));
        smartFormRecordMapper.insert(record);

        return record;
    }

    public List<SmartFormRecord> searchSmartForms(String tableName, String adminId, String adminPhone  ) {
        SmartFormRecordExample example = new SmartFormRecordExample();
        SmartFormRecordExample.Criteria criteria = example.createCriteria();

        // 添加查询条件
        if (tableName != null && !tableName.isEmpty()) {
            criteria.andDocNameLike("%" + tableName + "%");
        }
        if (adminPhone != null && !adminPhone.isEmpty()) {
            criteria.andAdminPhoneNumbersLike("%" + adminPhone + "%");
        }

        if (adminId != null && !adminId.isEmpty()) {
            criteria.andAdminUserIdsLike("%" + adminId + "%");
        }

        // 按ID倒序排序（默认排序，可根据需求调整）
        example.setOrderByClause("id desc");

        return smartFormRecordMapper.selectByExample(example);
    }


}