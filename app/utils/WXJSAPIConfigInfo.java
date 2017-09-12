package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import models.AccessToken;
import models.WeChatTicket;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import play.Logger;
import play.db.jpa.JPA;
import javax.persistence.Query;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WXJSAPIConfigInfo {
    private static synchronized WeChatTicket requestJsAPI_Ticket (String access_token) throws Exception{
        StringBuilder requestUrl = new StringBuilder();
        requestUrl.append(WXConfig.URL+"/cgi-bin/ticket/getticket?access_token="+access_token)
                .append("&type=jsapi");
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpget = new HttpGet(requestUrl.toString());
        CloseableHttpResponse response= client.execute(httpget);
        try{
            Logger.info("get jsapi_ticket status"+response.getStatusLine());
            HttpEntity entity =  response.getEntity();
            WeChatTicket wcTic = new ObjectMapper().readValue(EntityUtils.toString(entity),WeChatTicket.class);
            if(wcTic.errcode == 0 && wcTic.ticket.length() > 0){
                wcTic.createTime = System.currentTimeMillis();
                Logger.info("jsAPI_ticket=  "+wcTic.ticket);
                return wcTic;
            }else {
                Logger.info("not get jsAPT ticket ");
                return null;
            }
        }finally {
            response.close();
        }
    }

    private static String  getTicket() throws Exception{
         Map<String,Object> tokenMap = WXGetAccessToken.getAccessToken();
        if(tokenMap == null){
            return null;
        }
        String access_token = (String) tokenMap.get("access_token");
        Boolean refresh = (Boolean) tokenMap.get("refresh");
        Query query = JPA.em().createNamedQuery("queryTicket");
        List<WeChatTicket> tickets = query.getResultList();

        if(refresh){
            WeChatTicket ticket = requestJsAPI_Ticket(access_token);
            if(ticket!=null){
                if(!tickets.isEmpty()){
                    for (WeChatTicket tic: tickets) {
                        JPA.em().remove(tic);
                    }
                }
                JPA.em().persist(ticket);
                return ticket.ticket;
            }else {
                return null;
            }
        }else {
            if(tickets.isEmpty()){
                WeChatTicket ticket = requestJsAPI_Ticket(access_token);
                if(ticket!=null){
                    JPA.em().persist(ticket);
                    return ticket.ticket;
                }else {
                    return null;
                }
            }else {
                WeChatTicket ticket = tickets.get(0);
                if((System.currentTimeMillis() - ticket.createTime.longValue())/1000 < ticket.expires_in.longValue()-1000){
                    return ticket.ticket;
                }else {
                    WeChatTicket  ticketNew = requestJsAPI_Ticket(access_token);
                    if(ticketNew!=null){
                        ticket.expires_in = ticketNew.expires_in;
                        ticket.createTime = ticketNew.createTime;
                        ticket.ticket = ticketNew.ticket;
                        ticket.errcode = ticketNew.errcode;
                        ticket.errmsg = ticketNew.errmsg;
                        JPA.em().persist(ticket);
                        return ticket.ticket;
                    }else {
                        return null;
                    }
                }
            }
        }

    }
    public static String SHA1(String decript) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            digest.update(decript.getBytes());
            byte messageDigest[] = digest.digest();
            // Create Hex String
            StringBuffer hexString = new StringBuffer();
            // 字节数组转换为 十六进制 数
            for (int i = 0; i < messageDigest.length; i++) {
                String shaHex = Integer.toHexString(messageDigest[i] & 0xFF);
                if (shaHex.length() < 2) {
                    hexString.append(0);
                }
                hexString.append(shaHex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static Map<String,Object> getAPIConfigInfo(String URL) throws Exception{
        String ticket = getTicket();
        String url = URL;
        if(ticket == null|| url==null||("").equals(url)){
            return null;
        }
        String noncestr = UUID.randomUUID().toString().replace("-", "").substring(0, 16); // 16位随机字符串
        String  timestamp = String.valueOf(System.currentTimeMillis()/1000);  // 时间戳
        SortedMap<String,String> sortedMap = new TreeMap<>();
        sortedMap.put("jsapi_ticket",ticket);
        sortedMap.put("timestamp",timestamp);
        sortedMap.put("noncestr",noncestr);
        sortedMap.put("url",url);
        String connectStr = "";
        for(Map.Entry<String,String> item: sortedMap.entrySet()){
         if(!item.getKey().equals(sortedMap.lastKey())){
            connectStr += item.getKey()+"="+item.getValue() +"&";
         }else {
            connectStr += item.getKey()+"="+item.getValue();
         }
        }
        Logger.info("before JS API sig= "+connectStr);
        String signature = SHA1(connectStr);
        Logger.info("after sig1 = "+signature);
        Map<String,Object> returnMap = new HashMap<>();
        returnMap.put("appId",WXConfig.APPID);
        returnMap.put("timestamp",timestamp);
        returnMap.put("nonceStr",noncestr);
        returnMap.put("signature",signature);
        return returnMap;
    }
}
