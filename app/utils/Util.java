package utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import org.apache.commons.mail.*;

public class Util {
    static Date dayEnd(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    //generated six random password
    public String GenerateRandomNumber(int length)
    {
        String pwd = "";
        Random random = new Random();
        for(int i = 0; i < length; i++){
            String charOrNum = random.nextInt(2) % 2 == 0 ? "char" : "num";
            if("char".equalsIgnoreCase(charOrNum)){
                int choice = random.nextInt(2) % 2 == 0 ? 65 : 97;
                pwd += (char)(random.nextInt(26) + choice);
            }else if("num".equalsIgnoreCase(charOrNum)){
                pwd += String.valueOf(random.nextInt(10));
            }
        }
        return pwd;
    }

    public static InputStream getStringStream(String sInputString) throws Exception{
        ByteArrayInputStream tInputStringStream = null;
        if (sInputString != null && !sInputString.trim().equals("")) {
            tInputStringStream = new ByteArrayInputStream(sInputString.getBytes("UTF-8"));
        }
        return tInputStringStream;
    }
}
