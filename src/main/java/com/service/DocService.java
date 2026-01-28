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
import org.springframework.beans.factory.annotation.Value;

@Service
public class DocService {

    @Resource
    private WxworkService wxworkService;

    @Resource
    private SmartFormRecordMapper smartFormRecordMapper;

    // 管理员用户ID列表（逗号分隔）
    @Value("${admin_ids}")
    private String admin_ids;
    
    // 获取管理员用户ID列表
    public String getAdminIds() {
        return admin_ids;
    }


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

    public List<SmartFormRecord> searchSmartForms(String tableName, String adminId, String adminPhone ,String currentLoginUserId ) {
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

        if (!StringUtils.contains(admin_ids, currentLoginUserId)) {
            // 如果当前登录用户不是管理员，只返回当前登录用户创建的记录
            if (currentLoginUserId != null && !currentLoginUserId.isEmpty()) {
                criteria.andAdminUserIdsLike("%" + currentLoginUserId + "%");
            }
        } 
        


        // 按ID倒序排序（默认排序，可根据需求调整）
        example.setOrderByClause("id desc");

        return smartFormRecordMapper.selectByExample(example);
    }


}