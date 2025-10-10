package com.controller;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

@RestController
@RequestMapping("/wechat/work/callback") // 回调URL路径，需与企业微信后台配置一致
public class WeChatWorkCallbackController {

    @Value("${crop_id}")
    private String cropId;

    @Value("${app_token}")
    private String token;

    @Value("${app_encodingAESKey}")
    private String encodingAESKey;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }



    // -------------------------- 1. URL有效性验证接口（GET请求） --------------------------
    /**
     * 企业微信服务器会发送GET请求到回调URL，验证URL有效性（文档3.1节）
     * 请求参数：msg_signature、timestamp、nonce、echostr
     */
    @GetMapping
    public String validateCallback(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {

        try {
            // 步骤1：验证请求签名（确认请求来自企业微信）
            boolean isSignatureValid = verifySignature(msgSignature, timestamp, nonce, echostr);
            if (!isSignatureValid) {
                return "Invalid Signature"; // 签名验证失败，返回错误
            }

            // 步骤2：签名验证通过，解密echostr并返回（文档3.1.2节，加密模式下需解密）
            String decryptedEchostr = decryptEchostr(echostr);
            return decryptedEchostr;

        } catch (Exception e) {
            e.printStackTrace();
            return "Validation Failed"; // 验证过程异常，返回错误
        }
    }


    // -------------------------- 2. 接收消息/事件接口（POST请求） --------------------------
    /**
     * 企业微信服务器通过POST请求推送加密的消息/事件（文档3.2节）
     * 请求参数：msg_signature、timestamp、nonce；请求体：加密的XML/JSON数据
     */
    @PostMapping(produces = "application/xml;charset=UTF-8") // 企业微信默认推送XML格式，若为JSON需修改
    public String receiveMessage(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestBody String encryptedData) {

        try {
            // 步骤1：验证消息签名（确认消息未被篡改）
            boolean isSignatureValid = verifySignature(msgSignature, timestamp, nonce, encryptedData);
            if (!isSignatureValid) {
                return "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[Invalid Signature]]></return_msg></xml>";
            }

            // 步骤2：解密消息内容（文档3.2.2节，获取原始消息XML/JSON）
            String decryptedMessage = decryptMessage(encryptedData);
            System.out.println("Received WeChat Work Message: " + decryptedMessage); // 打印原始消息，便于后续处理

            // 步骤3：处理消息/事件（根据业务需求实现，如解析消息类型、触发业务逻辑等）
            // TODO: 此处添加自定义业务逻辑（例：解析XML中的MsgType，处理文本消息/事件推送）

            // 步骤4：返回成功响应（企业微信要求返回特定格式，否则会重试）
            return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";

        } catch (Exception e) {
            e.printStackTrace();
            return "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[Process Failed]]></return_msg></xml>";
        }
    }


    // -------------------------- 工具方法（核心逻辑，遵循文档规范） --------------------------
    /**
     * 验证签名（文档3.1.1节、3.2.1节）：按 token + timestamp + nonce + encryptedData 排序后SHA1加密，对比msg_signature
     */
    private boolean verifySignature(String msgSignature, String timestamp, String nonce, String encryptedData) throws NoSuchAlgorithmException {
        // 1. 拼接参数：token + timestamp + nonce + 加密数据（echostr或消息体）
        String[] arr = new String[]{token, timestamp, nonce, encryptedData};
        // 2. 字典序排序
        Arrays.sort(arr);
        // 3. 拼接为字符串
        StringBuilder content = new StringBuilder();
        for (String s : arr) {
            content.append(s);
        }
        // 4. SHA1加密
        String sha1Result = sha1Encrypt(content.toString());
        // 5. 对比加密结果与msg_signature
        return sha1Result != null && sha1Result.equals(msgSignature);
    }

    /**
     * 解密echostr（文档3.1.2节）：EncodingAESKey解码后作为AES密钥，解密echostr获取原始字符串
     */
    private String decryptEchostr(String echostr) throws Exception {
        // 1. 处理EncodingAESKey（企业微信提供的43位字符串，需补全Base64后缀）
        byte[] aesKey = Base64.getDecoder().decode(encodingAESKey + "=");

        // 2. 初始化AES解密器（指定BouncyCastle提供者，使用PKCS7Padding）
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", "BC"); // 关键：添加"BC"提供者
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(aesKey, 0, 16); // IV为密钥前16字节
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        // 3. 解密并处理结果
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(echostr));
        String decryptedStr = new String(decryptedBytes, StandardCharsets.UTF_8);

        // 4. 验证并提取有效内容（去除16位随机数和CorpID后缀）
        int corpIdIndex = decryptedStr.indexOf(cropId);
        if (corpIdIndex == -1) {
            throw new Exception("解密失败：CorpID不匹配");
        }
        return decryptedStr.substring(20, corpIdIndex);
    }

    /**
     * 解密消息体（文档3.2.2节）：逻辑与解密echostr一致，仅输入为加密消息体
     */
    private String decryptMessage(String encryptedData) throws Exception {
        return decryptEchostr(encryptedData); // 复用解密echostr的逻辑
    }

    /**
     * SHA1加密工具：将字符串转为SHA1哈希值（小写）
     */
    private String sha1Encrypt(String content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] digestBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        // 字节数组转为十六进制字符串
        StringBuilder hexBuilder = new StringBuilder();
        for (byte b : digestBytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                hexBuilder.append("0");
            }
            hexBuilder.append(hex);
        }
        return hexBuilder.toString();
    }
}