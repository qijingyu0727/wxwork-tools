package com.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import java.net.URLEncoder;


/**
 * 钉钉机器人处理工具
 */
public class RobotUtil {
    /**
     * 根据当前时间戳和机器人的密钥生成sign
     * @param timestamp
     * @param secret
     * @return
     * @throws Exception
     */
    public static  String getSign(Long timestamp,String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
        String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)),"UTF-8");
        return sign;
    }
}
