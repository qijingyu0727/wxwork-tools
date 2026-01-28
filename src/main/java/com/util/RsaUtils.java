package com.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;

/**
 * @program: wxwork-tools
 * @description:
 * @author: wanziliang
 * @create: 2023-10-18 10:39
 **/
public class RsaUtils {



    public static PrivateKey getPrivateKey(String privKeyPEM) throws Exception{

        String privKeyPEMnew = privKeyPEM.replaceAll("\\n", "").replace("-----BEGIN RSA PRIVATE KEY-----", "").replace("-----END RSA PRIVATE KEY-----", "");
        byte[] bytes = java.util.Base64.getDecoder().decode(privKeyPEMnew);

        DerInputStream derReader = new DerInputStream(bytes);
        DerValue[] seq = derReader.getSequence(0);
        BigInteger modulus = seq[1].getBigInteger();
        BigInteger publicExp = seq[2].getBigInteger();
        BigInteger privateExp = seq[3].getBigInteger();
        BigInteger prime1 = seq[4].getBigInteger();
        BigInteger prime2 = seq[5].getBigInteger();
        BigInteger exp1 = seq[6].getBigInteger();
        BigInteger exp2 = seq[7].getBigInteger();
        BigInteger crtCoef = seq[8].getBigInteger();

        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        return privateKey;
    }

    /**
     * 使用私钥进行RSA解密
     *
     * @param content    需要解密的内容
     * @param privateKey 私钥
     * @return 解密后的内容
     */
    public static String decrypt(String content, String privateKey) throws Exception {
        if (StringUtils.isBlank(content)) {
            return "";
        }
        // Base64解码加密后的字符串
        byte[] inputByte = Base64.decodeBase64(content.getBytes("UTF-8"));
        // Base64编码的私钥
        byte[] decoded = Base64.decodeBase64(privateKey);
        PrivateKey priKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        // RSA解密
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, priKey);
        return new String(cipher.doFinal(inputByte));
    }


    public static String decrypt2(String content, String privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, getPrivateKey(privateKey));

        byte[] randomkeybyte = Base64.decodeBase64(content.getBytes("UTF-8"));
        byte[] finalrandomkeybyte = cipher.doFinal(randomkeybyte);
        String finalrandomkey = new String(finalrandomkeybyte);

        return finalrandomkey;
    }




}
