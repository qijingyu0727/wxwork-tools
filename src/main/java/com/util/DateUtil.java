package com.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtil {

    public static String dateToStamp(String s) throws Exception {
        String res;
        //设置时间格式，将该时间格式的时间转换为时间戳
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = simpleDateFormat.parse(s);
        long time = date.getTime()/1000;
        res = String.valueOf(time);
        return res;
    }
    public static String dateToStamp1(String s) throws Exception {
        String res;
        //设置时间格式，将该时间格式的时间转换为时间戳
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date date = simpleDateFormat.parse(s);
        long time = date.getTime();
        res = String.valueOf(time);
        return res;
    }
    public static String stampToDate(Long s) throws Exception {
        //设置时间格式，将该时间戳转换为时间格式的时间
        SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sd = sdf.format(new Date(Long.parseLong(String.valueOf(s))));
        return sd;
    }

    public static String dateToStamp(Date date){
        String res;
        long time = date.getTime()/1000;
        res = String.valueOf(time);
        return res;
    }

    public static Date getYesterday(){

        Date today = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.add(Calendar.DATE, -1);
        return calendar.getTime();

    }

    public static Date getOnehundredYearsLater(Date date){
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.YEAR, 100);
        return c.getTime();
    }

    public static String getFormat(Date date){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return simpleDateFormat.format(date);
    }
}
