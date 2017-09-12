package utils;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import play.Logger;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class WXPayQueryOrder {
   /*
   * 查询用户微信充值的订单是否成功
   * */
 public static Map<String,Object> queryOrderOfCustomer(String tradeNO,int type) throws Exception{
     Map<String,Object> resultMap = null;
     String out_trade_no = tradeNO;
     SortedMap<String,Object> sortedMap = new TreeMap<>();
     String appid = "";
     String mchid = "";
     if(type == 0){
         appid = WXPayConfig.appID;
         mchid = WXPayConfig.mchID;
     }else if(type == 1){
         appid = WXPayConfig.appID1;
         mchid = WXPayConfig.mchID1;
     }else if(type == 2){
         appid = WXPayConfig.appID2;
         mchid = WXPayConfig.mchID2;
     }
     sortedMap.put("appid",appid);
     sortedMap.put("mch_id",mchid);
     sortedMap.put("out_trade_no",out_trade_no);
     sortedMap.put("nonce_str",WXPayUtil.getRandomStringByLength(32));
     String order_sign = WXPayUtil.getSign(sortedMap,type);
     sortedMap.put("sign", order_sign);

     String requestXML = WXPayXMLParser.getXMLStrFromMap(sortedMap);
     HttpPost httpPost = new HttpPost(WXPayConfig.PAY_QUERY_API);
     StringEntity postEntity = new StringEntity(requestXML,"UTF-8");
     httpPost.addHeader("Content-Type","text/xml");
     httpPost.setEntity(postEntity);
     httpPost.setConfig(RequestConfig.custom().setSocketTimeout(10000).setConnectionRequestTimeout(30000).build());
     CloseableHttpResponse response = HttpClients.createDefault().execute(httpPost);
     Logger.info("executing query customer order request "+httpPost.getRequestLine());
     Logger.info("query status: "+response.getStatusLine());
     try {
         HttpEntity httpEntity = response.getEntity();
         String queryResult = EntityUtils.toString(httpEntity,"UTF-8");
         Logger.info("query order string: "+queryResult);
        resultMap =  WXPayXMLParser.getMapFromXML(queryResult);
       }finally {
         response.close();
     }
     return resultMap;
 }

    /**
     * 查询向商户转账的订单结果
     *
     */
    public static Map<String,Object> queryOrderOfCompany(String partner_trade_no) throws Exception{
       // request data
       Map<String,Object> resultMap = null;
       SortedMap<String,Object> sortedMap = new TreeMap<>();
       sortedMap.put("partner_trade_no",partner_trade_no);
       sortedMap.put("appid",WXPayConfig.appID);
       sortedMap.put("mch_id",WXPayConfig.mchID);
       sortedMap.put("nonce_str",WXPayUtil.getRandomStringByLength(32));
       String transfer_sign = WXPayUtil.getSign(sortedMap,0);
       sortedMap.put("sign",transfer_sign);
       String requestXML = WXPayXMLParser.getXMLStrFromMap(sortedMap);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        FileInputStream inputStream = new FileInputStream(play.Play.application().getFile("app/apiclient_cert.p12"));
        keyStore.load(inputStream,"1402323202".toCharArray());
        inputStream.close();
        SSLContext sslContext = SSLContexts.custom().loadKeyMaterial(keyStore,"1402323202".toCharArray()).build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,new String[]{"TLSv1"},null,SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
        HttpPost httpost = new HttpPost(WXPayConfig.TRANSFER_QUERY_API);
        httpost.addHeader("Connection", "keep-alive");
        httpost.addHeader("Accept", "*/*");
        httpost.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        httpost.addHeader("Host", "api.mch.weixin.qq.com");
        httpost.addHeader("X-Requested-With", "XMLHttpRequest");
        httpost.addHeader("Cache-Control", "max-age=0");
        httpost.addHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.0) ");
        httpost.setEntity(new StringEntity(requestXML,"UTF-8"));
        CloseableHttpResponse response = httpClient.execute(httpost);
        Logger.info("executing request" + httpost.getRequestLine());
        Logger.info("query transfer order status"+response.getStatusLine());
        try {
            HttpEntity httpEntity = response.getEntity();
            String queryResult = EntityUtils.toString(httpEntity,"UTF-8");
            Logger.info("query transfer order string: "+queryResult);
            resultMap =  WXPayXMLParser.getMapFromXML(queryResult);
        }finally {
            response.close();
        }
       return resultMap;
    }
}
