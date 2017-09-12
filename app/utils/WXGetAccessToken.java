package utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import models.AccessToken;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import play.Logger;
import play.db.jpa.JPA;

import javax.persistence.Query;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static play.libs.Json.toJson;

public class WXGetAccessToken {
  private static synchronized AccessToken  requestAccessToken(String appId,String appSecret) throws ClientProtocolException,IOException{
      StringBuilder requestUrl = new StringBuilder();
      requestUrl.append(WXConfig.URL+"/cgi-bin/token?grant_type=client_credential&appid=")
              .append(appId)
              .append("&secret=")
              .append(appSecret);
      CloseableHttpClient client = HttpClients.createDefault();
      HttpGet httpget = new HttpGet(requestUrl.toString());
      CloseableHttpResponse response= client.execute(httpget);
      try{
          Logger.info("get access_token status"+response.getStatusLine());
        HttpEntity entity =  response.getEntity();
         AccessToken accessToken = new ObjectMapper().readValue(EntityUtils.toString(entity),AccessToken.class);
      if(accessToken.access_token.length() > 0){
          accessToken.createTime = System.currentTimeMillis();
          Logger.info("access_token=  "+accessToken.access_token);
          return accessToken;
      }else {
          Logger.info("not get access token ");
          return null;
      }
      }finally {
          response.close();
      }
  }

  public static Map<String,Object> getAccessToken() throws Exception{
      String appId = WXConfig.APPID;
      String appSecret = WXConfig.APPSECRET;
      Map<String,Object> returnMap = new HashMap<>();
      Query query = JPA.em().createNamedQuery("queryAccessToken");
      List<AccessToken> accessTokens = query.getResultList();
      Logger.info("===acT size=="+accessTokens.size());
      if(accessTokens.isEmpty()){
          AccessToken accessToken = requestAccessToken(appId,appSecret);
          if(accessToken!=null){
              JPA.em().persist(accessToken);
              returnMap.put("access_token",accessToken.access_token);
              returnMap.put("refresh",true);
              return returnMap;
          }else {
              return null;
          }
      }else {
          AccessToken token = accessTokens.get(0);
          if((System.currentTimeMillis() - token.createTime.longValue())/1000 < token.expires_in.longValue()-1000){
              returnMap.put("access_token",token.access_token);
              returnMap.put("refresh",false);
              return returnMap;
          }else {
              AccessToken accessToken = requestAccessToken(appId,appSecret);
              if(accessToken!=null){
                  token.access_token = accessToken.access_token;
                  token.expires_in = accessToken.expires_in;
                  token.createTime = accessToken.createTime;
                  JPA.em().persist(token);
                  returnMap.put("access_token",token.access_token);
                  returnMap.put("refresh",true);
                  return returnMap;
              }else {
                  return null;
              }
          }
      }
  }
}
