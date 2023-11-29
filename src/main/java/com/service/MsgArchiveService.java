package com.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.support.odps.udf.CodecCheck;
import com.model.*;
import com.tencent.wework.Finance;
import com.util.FileUtils;
import com.util.RsaUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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

        String privateKey = getPrivateKey();
        //存档消息序号
        Integer seq = archiveMsgInfoService.getMaxSeq();
        //一次取数条数
        Integer limit = 1000;
        //超时时间
        Integer timeout = 5;

        AtomicLong ret = new AtomicLong();
        long sdk = Finance.NewSdk();
        ret.set(Finance.Init(sdk, cropId, archiveMsgSecret));

        if(ret.get() != 0){
            Finance.DestroySdk(sdk);
            System.out.println("init sdk err ret " + ret);
        }

        long slice = Finance.NewSlice();

        ret.set(Finance.GetChatData(sdk, seq, limit, null, null, timeout, slice));

        if (ret.get() != 0) {
            System.out.println("getchatdata ret " + ret);
            Finance.FreeSlice(slice);
        }

        String archiveMsg = Finance.GetContentFromSlice(slice);

        ArchiveMsgModel archiveMsgModel = JSONObject.parseObject(archiveMsg, ArchiveMsgModel.class);
        Finance.FreeSlice(slice);

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
                    storageCustomerGroupInfo(archiveMsgDecryptModel.getRoomid());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //存储聊天记录相关信息
                storageArchiveMsgInfo(archiveMsgDecryptModel , chatdataDTO);
                Finance.FreeSlice(msg);
            }
        });
        Finance.DestroySdk(sdk);
    }


    private void storageCustomerGroupInfo (String roomId) throws Exception {
        CustomerGroupInfo customerGroupInfo = new CustomerGroupInfo();
        customerGroupInfo.setRoomId(roomId);
        CustomerGroupDetailModel customerGroupChat = wxworkService.getCustomerGroupChat(roomId);
        if (null != customerGroupChat.getGroupChat()) {
            customerGroupInfo.setRoomName(customerGroupChat.getGroupChat().getName());
        }
        customerGroupInfoService.insert(customerGroupInfo);
    }


    private void storageArchiveMsgInfo (ArchiveMsgDecryptModel archiveMsgDecryptModel , ArchiveMsgModel.ChatdataDTO chatdataDTO) {

        ArchiveMsgInfo archiveMsgInfo = new ArchiveMsgInfo();
        archiveMsgInfo.setSeq(chatdataDTO.getSeq());
        archiveMsgInfo.setFrom(archiveMsgDecryptModel.getFrom());
        archiveMsgInfo.setPublickeyVer(chatdataDTO.getPublickeyVer());
        archiveMsgInfo.setRoomId(archiveMsgDecryptModel.getRoomid());
        archiveMsgInfo.setContext(archiveMsgDecryptModel.getText()==null?null:archiveMsgDecryptModel.getText().getContent());
        archiveMsgInfo.setMsgTime(archiveMsgDecryptModel.getMsgtime());

        archiveMsgInfoService.insert(archiveMsgInfo);
    }

}