package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import play.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WXGetUserInfo {
    private static String URL = "https://api.weixin.qq.com";

    // 获取用户信息时进行网页回调的access_token
    public static Map<String,String> requestAccessTokenForAuth(String appId, String appSecret, String code) throws ClientProtocolException, IOException {
        StringBuilder requestUrl = new StringBuilder();
        requestUrl.append(URL + "/sns/oauth2/access_token?appid=")
                .append(appId.trim())
                .append("&secret=")
                .append(appSecret.trim())
                .append("&code=")
                .append(code.trim())
                .append("&grant_type=authorization_code");
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet httpget = new HttpGet(requestUrl.toString());
        Logger.info("URL= "+requestUrl.toString());
        CloseableHttpResponse response = client.execute(httpget);
        try {
            Logger.info("get access_token status" + response.getStatusLine());
            Map<String,String> mapToken = null;
            ObjectMapper mapper = new ObjectMapper();
            String str = EntityUtils.toString(response.getEntity());
            Logger.info("author access_token str= "+str);
            JsonNode jsonNode =mapper.readTree(str) ;
            JsonNode accessToken = jsonNode.get("access_token");
            if(accessToken != null){
                mapToken = new HashMap<>();
                mapToken.put("access_token",deleteSYH(accessToken.toString()));
                mapToken.put("expires_in",deleteSYH(jsonNode.get("expires_in").toString()));
                mapToken.put("openid",deleteSYH(jsonNode.get("openid").toString()));
                mapToken.put("refresh_token",deleteSYH(jsonNode.get("refresh_token").toString()));
                mapToken.put("scope",deleteSYH(jsonNode.get("scope").toString()));
            }
            return mapToken;
        } finally {
            response.close();
        }
    }

    // 根据access_token和openId用户的基本信息
    public static Map<String, String> requestUserInfoBycode(String openId,String access_token) throws ClientProtocolException,IOException{
       StringBuilder builderURL = new StringBuilder();
       builderURL.append(URL+"/sns/userinfo?access_token=")
               .append(access_token.trim())
               .append("&openid=")
               .append(openId.trim())
               .append("&lang=zh_CN");
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet(builderURL.toString());
        CloseableHttpResponse response = client.execute(get);

        try{
            Logger.info("get weChat user info status" + response.getStatusLine());
            Map<String,String> infoMap = null;
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(EntityUtils.toString(response.getEntity(),"utf-8"));
            JsonNode openid = node.get("openid");
            if(openid != null){
                infoMap = new HashMap<>();
                infoMap.put("openid",deleteSYH(node.get("openid").toString()));
                infoMap.put("nickname",deleteSYH(node.get("nickname").toString()));
                infoMap.put("sex",deleteSYH(node.get("sex").toString()));
                infoMap.put("province",deleteSYH(node.get("province").toString()));
                infoMap.put("city",deleteSYH(node.get("city").toString()));
                infoMap.put("country",deleteSYH(node.get("country").toString()));
                infoMap.put("headimgurl",deleteSYH(node.get("headimgurl").toString()));
                infoMap.put("privilege",deleteSYH(node.get("privilege").toString()));
            }
            return  infoMap;
        }finally {
            response.close();
        }
    }
    // 判断该用户是否关注公众号
    public static Map<String, String> requestIsFollowWeChat(String openId,String access_token) throws ClientProtocolException,IOException{
        StringBuilder builderURL = new StringBuilder();
        builderURL.append(URL+"/cgi-bin/user/info?access_token=")
                .append(access_token.trim())
                .append("&openid=")
                .append(openId.trim())
                .append("&lang=zh_CN");
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet(builderURL.toString());
        CloseableHttpResponse response = client.execute(get);

        try{
            Logger.info("get  is subscribed info status" + response.getStatusLine());
            Map<String,String> infoMap = null;
            ObjectMapper mapper = new ObjectMapper();
            String str = EntityUtils.toString(response.getEntity(),"utf-8");
            JsonNode node = mapper.readTree(str);
            Logger.info("========="+str);
            JsonNode subscribeInfo = node.get("subscribe");
            JsonNode openid = node.get("openid");
            if(subscribeInfo != null && openid!=null){
                infoMap = new HashMap<>();
                infoMap.put("subscribe",deleteSYH(subscribeInfo.toString()));
                infoMap.put("openid",deleteSYH(node.get("openid").toString()));
                if("1".equals(deleteSYH(node.get("openid").toString()))){
                    infoMap.put("nickname",deleteSYH(node.get("nickname").toString()));
                    infoMap.put("sex",deleteSYH(node.get("sex").toString()));
                    infoMap.put("province",deleteSYH(node.get("province").toString()));
                    infoMap.put("city",deleteSYH(node.get("city").toString()));
                    infoMap.put("country",deleteSYH(node.get("country").toString()));
                    infoMap.put("headimgurl",deleteSYH(node.get("headimgurl").toString()));
                    infoMap.put("privilege","");
                }

            }
            return  infoMap;
        }finally {
            response.close();
        }
    }



    // 去除字符串中的双引号
    public static String  deleteSYH(String sourceStr){
        return sourceStr.replaceAll("\"","").trim();
    }
}
