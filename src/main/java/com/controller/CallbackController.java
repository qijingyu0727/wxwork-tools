package com.controller;

import com.model.ArchiveMsgInfo;
import com.model.CustomerGroupInfo;
import com.service.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * E应用回调信息处理
 */
@RestController
public class CallbackController {

    @Resource
    private MsgArchiveService msgArchiveService;

    @Resource
    private ArchiveMsgInfoService archiveMsgInfoService;

    @Resource
    private WxworkService wxworkService;

    @Resource
    private TemplateService templateService;

    @Resource
    private CustomerGroupInfoService customerGroupInfoService;


    @RequestMapping(value = "/getArchiveMsg", method = RequestMethod.GET)
    @ResponseBody
    public void getArchiveMsg (){
       msgArchiveService.getArchiveMsg();
    }


    @RequestMapping(value = "/syncTemplate", method = RequestMethod.GET)
    @ResponseBody
    public void syncTemplate (){
        templateService.sync();
    }


    @RequestMapping(value = "/test", method = RequestMethod.GET)
    @ResponseBody
    public Object test() throws Exception {
        return archiveMsgInfoService.getMaxSeq();
    }


}
