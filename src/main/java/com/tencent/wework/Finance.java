package com.tencent.wework;

public class Finance {
    public native static long NewSdk();

    public native static int Init(long sdk, String corpid, String secret);


    public native static int GetChatData(long sdk, long seq, long limit, String proxy, String passwd, long timeout, long chatData);


    public native static int GetMediaData(long sdk, String indexbuf, String sdkField, String proxy, String passwd, long timeout, long mediaData);


    public native static int DecryptData(long sdk, String encrypt_key, String encrypt_msg, long msg);
	
    public native static void DestroySdk(long sdk);
    public native static long NewSlice();

    public native static void FreeSlice(long slice);

    public native static String GetContentFromSlice(long slice);


    public native static int GetSliceLen(long slice);
    public native static long NewMediaData();
    public native static void FreeMediaData(long mediaData);


    public native static String GetOutIndexBuf(long mediaData);

    public native static byte[] GetData(long mediaData);
    public native static int GetIndexLen(long mediaData);
    public native static int GetDataLen(long mediaData);

    public native static int IsMediaDataFinish(long mediaData);

    static {
        System.load("/opt/wxwork/libWeWorkFinanceSdk_Java.so");
    }
}
