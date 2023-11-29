package com.controller;

import com.service.ArchiveMsgInfoService;
import com.service.MsgArchiveService;
import com.service.WxworkService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

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


    @RequestMapping(value = "/getArchiveMsg", method = RequestMethod.GET)
    @ResponseBody
    public void getArchiveMsg (){
       msgArchiveService.getArchiveMsg();
    }


    @RequestMapping(value = "/test", method = RequestMethod.GET)
    @ResponseBody
    public Object test() throws Exception {
        return archiveMsgInfoService.getMaxSeq();
    }


}
