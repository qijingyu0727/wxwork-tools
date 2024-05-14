package com.util;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;

public class IDUtils {
    private static SnowFlake snowFlake = new SnowFlake(1L, 1L);

    public IDUtils() {
    }

    public static String randomID(Integer num) {
        num = ObjectUtils.isEmpty(num) ? 16 : num;
        return RandomStringUtils.randomAlphanumeric(num);
    }

    public static Long snowID() {
        return snowFlake.nextId();
    }
}
