package models;

import java.util.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import play.Logger;

public class Message extends Thread {
    //static final String YTX_BASE_URL = "https://sandboxapp.cloopen.com:8883/2013-12-26/Accounts/";
    static final String YTX_BASE_URL = "https://app.cloopen.com:8883/2013-12-26/Accounts/";
    static final String YTX_ACCOUNT_SID = "aaf98f894830369d0148305381400010";
    static final String YTX_AUTH_TOKEN = "9d7b86d85e0b4568a83f4cceb72f1e9d";
    public static final String APP_ID = "8a48b5514e236232014e2cac65b30918";
    //public static final String TEMPLATE_ID = "77722";

    public Message(String mobile, String content, String template_id) {
        this.mobile = mobile;
        this.content = content;
        this.template_id = template_id;
    }

    public void run() {
        //sendSmsBySms9();
        sendSmsByYuntongxun();
    }

    private void sendSmsByYuntongxun()
    {
        Calendar calendar = Calendar.getInstance();
        Date now = calendar.getTime();
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(now);
        String sig = getMd5(YTX_ACCOUNT_SID + YTX_AUTH_TOKEN + timestamp);
        String authSrc = YTX_ACCOUNT_SID + ":" + timestamp;
        byte[] authBytes = Base64.encodeBase64(authSrc.getBytes());
        String authorization = new String(authBytes);
        ArrayNode jdatas = new ArrayNode(JsonNodeFactory.instance);
        jdatas.add(content);
        jdatas.add("10");
        ObjectNode jbody = new ObjectNode(JsonNodeFactory.instance);
        jbody.put("to", mobile)
            .put("appId", Message.APP_ID)
            .put("templateId", template_id)
            .put("datas", jdatas);
        try {
            Executor executor = Executor.newInstance();
            String rst = executor.execute(Request.Post(YTX_BASE_URL + YTX_ACCOUNT_SID + "/SMS/TemplateSMS?sig=" + sig)
               .addHeader("Accept", "application/json")
               //.addHeader("Content-Type", "application/json;charset=utf-8;")
               .addHeader("Authorization", authorization)
               .bodyString(jbody.toString(), ContentType.APPLICATION_JSON)
               ).returnContent().asString();
            Logger.info("response: " + rst);
        } catch (Exception e) {
            Logger.error("response: " + e.getMessage());
        }
    }

    static final String YSMS_BASE_URL = "http://http.yunsms.cn/tx/?";
    static final String YSMS_UID = "130675";
    static final String YSMS_SECRET = "379143";
    /* content = "&mobile=13910079037&content=URLEncoder.encode(content)" */
    private void sendSmsByYunsms()
    {
        StringBuilder smsUrl = new StringBuilder();
        smsUrl.append(YSMS_BASE_URL);
        smsUrl.append("uid=" + YSMS_UID);
        smsUrl.append("&pwd=" + getMd5(YSMS_SECRET));
        smsUrl.append("&mobile=" + mobile);
        smsUrl.append("&encode=utf8");

        try {
            smsUrl.append("&content=" + URLEncoder.encode(content, "utf-8"));
            Logger.info(smsUrl.toString());
            Executor executor = Executor.newInstance();
            String rst = executor.execute(Request.Post(smsUrl.toString())).returnContent().asString();
            Logger.info("response: " + rst);
        } catch (Exception e) {
        }
    }

    static final String SMS9_BASE_URL = "http://admin.sms9.net/houtai/sms.php?";
    static final String SMS9_CPID = "10462";
    static final String SMS9_SECRET = "^Bei1Jing0$_%d_topsky";
    static final String SMS9_CHANNEL_ID = "13425";
    private void sendSmsBySms9()
    {
        StringBuilder smsUrl = new StringBuilder();
        smsUrl.append(SMS9_BASE_URL);
        smsUrl.append("cpid=" + SMS9_CPID);
        Calendar calendar = Calendar.getInstance();
        long timestamp = calendar.getTimeInMillis() / 1000;
        smsUrl.append("&password=" + getMd5(String.format(SMS9_SECRET, timestamp)));
        smsUrl.append("&timestamp=" + String.format("%d", timestamp));
        smsUrl.append("&channelid=" + SMS9_CHANNEL_ID);
        smsUrl.append("&tele=" + mobile);
        try {
            smsUrl.append("&msg=" + URLEncoder.encode(content, "gbk"));
            Logger.info(smsUrl.toString());
            Executor executor = Executor.newInstance();
            String rst = executor.execute(Request.Post(smsUrl.toString())).returnContent().asString();
            Logger.info("response: " + rst);
        } catch (Exception e) {
        }
    }

    private String getMd5(String source) {
        String s = null;
        char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',  'e', 'f'};
        try {
            MessageDigest md = java.security.MessageDigest.getInstance( "MD5" );
            md.update( source.getBytes() );
            byte tmp[] = md.digest();
            char str[] = new char[16 * 2];
            int k = 0;
            for (int i = 0; i < 16; i++) {
                byte byte0 = tmp[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            s = new String(str);
        } catch ( Exception e ) {
        }
        return s;
    }

    private String mobile;
    private String content;
    private String template_id;
}

