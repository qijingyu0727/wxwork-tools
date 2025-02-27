package com.service;

import com.alibaba.fastjson.JSONObject;
import com.model.*;
import com.tencent.wework.Finance;
import com.util.FileUtils;
import com.util.RsaUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-17 17:31
 **/
@Service
public class MsgArchiveService {

    @Value("${private_key_path}")
    private String privatePath;

    @Value("${archive_msg_secret}")
    private String archiveMsgSecret;

    @Value("${crop_id}")
    private String cropId;

    @Resource
    private ArchiveMsgInfoService archiveMsgInfoService;

    @Resource
    private WxworkService wxworkService;

    @Resource
    private CustomerGroupInfoService customerGroupInfoService;

    public String getPrivateKey(){
        return FileUtils.readFileAsString(privatePath);
    }

    public void getArchiveMsg(){

        //消息List
        List<ArchiveMsgInfo> archiveMsgInfoList = new ArrayList<>();
        //群组List
        List<CustomerGroupInfo> customerGroupInfoList = new ArrayList<>();

        String privateKey = getPrivateKey();
        //存档消息序号
        Integer seq = archiveMsgInfoService.getMaxSeq();
        //一次取数条数
        Integer limit = 50;
        //超时时间
        Integer timeout = 5;

        System.out.println(1);

        AtomicLong ret = new AtomicLong();
        long sdk = Finance.NewSdk();
        ret.set(Finance.Init(sdk, cropId, archiveMsgSecret));

        if(ret.get() != 0){
            Finance.DestroySdk(sdk);
            System.out.println("init sdk err ret " + ret);
        }

        System.out.println(2);


        long slice = Finance.NewSlice();

        ret.set(Finance.GetChatData(sdk, seq, limit, null, null, timeout, slice));

        if (ret.get() != 0) {
            System.out.println("getchatdata ret " + ret);
            Finance.FreeSlice(slice);
        }

        System.out.println(3);


        String archiveMsg = Finance.GetContentFromSlice(slice);

        ArchiveMsgModel archiveMsgModel = JSONObject.parseObject(archiveMsg, ArchiveMsgModel.class);
        Finance.FreeSlice(slice);

        System.out.println(4);


        archiveMsgModel.getChatdata().forEach(chatdataDTO -> {
            // 当前仅当公钥版本为 4 时，解密消息（前面版本的公钥已经找不到了）
            if(chatdataDTO.getPublickeyVer()==4) {
                String encryptRandomKey = chatdataDTO.getEncryptRandomKey();
                String encrypt_key = null;
                try {
                    encrypt_key = RsaUtils.decrypt2(encryptRandomKey, privateKey);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String encrypt_chat_msg = chatdataDTO.getEncryptChatMsg();
                //每次使用DecryptData解密会话存档前需要调用NewSlice获取一个slice，在使用完slice中数据后，还需要调用FreeSlice释放。
                long msg = Finance.NewSlice();
                ret.set(Finance.DecryptData(sdk, encrypt_key, encrypt_chat_msg, msg));
                if (ret.get() != 0) {
                    System.out.println("getchatdata ret " + ret);
                    Finance.FreeSlice(msg);
                    return;
                }
                String decryptMsg = Finance.GetContentFromSlice(msg);

                ArchiveMsgDecryptModel archiveMsgDecryptModel = JSONObject.parseObject(decryptMsg, ArchiveMsgDecryptModel.class);
                //存储群聊相关信息
                try {
                    Optional<CustomerGroupInfo> customerGroupInfoOptional = customerGroupInfoList.stream().filter(groupInfoDTO -> groupInfoDTO.getRoomId().equals(archiveMsgDecryptModel.getRoomid())).findFirst();
                    if (!customerGroupInfoOptional.isPresent()) {
                        customerGroupInfoList.add(generateCustomerGroupInfo(archiveMsgDecryptModel.getRoomid()));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //存储聊天记录相关信息
                archiveMsgInfoList.add(generateArchiveMsgInfo(archiveMsgDecryptModel , chatdataDTO));
                Finance.FreeSlice(msg);
            }
        });
        Finance.DestroySdk(sdk);

        //批量插入归档信息
        archiveMsgInfoService.batchInsert(archiveMsgInfoList);
        //批量插入群聊信息
        customerGroupInfoService.batchInsert(customerGroupInfoList);

    }


    private CustomerGroupInfo generateCustomerGroupInfo (String roomId) throws Exception {
        CustomerGroupInfo customerGroupInfo = new CustomerGroupInfo();
        customerGroupInfo.setRoomId(roomId);
        CustomerGroupDetailModel customerGroupChat = wxworkService.getCustomerGroupChat(roomId);
        if (null != customerGroupChat && null != customerGroupChat.getGroupChat()) {
            customerGroupInfo.setRoomName(customerGroupChat.getGroupChat().getName());
        }
        return  customerGroupInfo;
    }


    private ArchiveMsgInfo generateArchiveMsgInfo (ArchiveMsgDecryptModel archiveMsgDecryptModel , ArchiveMsgModel.ChatdataDTO chatdataDTO) {
        ArchiveMsgInfo archiveMsgInfo = new ArchiveMsgInfo();
        archiveMsgInfo.setSeq(chatdataDTO.getSeq());
        archiveMsgInfo.setSender(archiveMsgDecryptModel.getFrom());
        archiveMsgInfo.setPublickeyVer(chatdataDTO.getPublickeyVer());
        archiveMsgInfo.setRoomId(archiveMsgDecryptModel.getRoomid());
        archiveMsgInfo.setContext(archiveMsgDecryptModel.getText()==null?null:archiveMsgDecryptModel.getText().getContent());
        archiveMsgInfo.setMsgTime(archiveMsgDecryptModel.getMsgtime());
        return archiveMsgInfo;
    }

}
