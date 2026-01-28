package com.quartz.job;

import com.service.MsgArchiveService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service("archiveMsgJob")
public class ArchiveMsgJob {

    @Resource
    private MsgArchiveService msgArchiveService;

    public void start(){
        msgArchiveService.getArchiveMsg();
    }
}
