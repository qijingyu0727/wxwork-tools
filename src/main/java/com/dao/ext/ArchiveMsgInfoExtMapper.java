package com.dao.ext;

import com.model.ArchiveMsgInfo;

import java.util.List;

public interface ArchiveMsgInfoExtMapper {
    int batchInsert(List<ArchiveMsgInfo> archiveMsgInfoList);
}