package com.google.zxing.client.android.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Utils {

    public static boolean isEmpty(String str) {
        if (str != null && str.length() > 0) {
            return false;
        } else {
            return true;
        }
    }
    
    public static boolean isEmpty(List<?> list) {
        if (list != null && list.size() > 0) {
            return false;
        } else {
            return true;
        }
    }
    
    public static boolean isEmptyOrZero(String digitString) {
        if (digitString != null && digitString.length() > 0) {
            try {
                long value = Long.parseLong(digitString); 
                return value == 0;
            } catch (NumberFormatException e) {
                //do nothing
            }
            
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * 格式化时间 yyyy.MM.dd hh:mm
     * 
     * @param date
     * @return
     */
    public static String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(date);
    }

    public static String formatDateSecond(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }
    public static String formatMounth(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd");
        return sdf.format(date);
    }
    public static String formatDay(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        return sdf.format(date);
    }
    
}
