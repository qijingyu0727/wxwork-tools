package com.quartz.job;

import com.service.TemplateService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service("templateJob")
public class TemplateJob {
    @Resource
    private TemplateService templateService;

    public void start(){
        templateService.sync();
    }
}
