package utils;


import models.CompanyCustomer;
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

public class WXPayCore {

     /*
     * 统一下单接口
     * */
     public static String postOrder(String xmlParams) throws Exception{
         String result = null;
         HttpPost httpPost = new HttpPost(WXPayConfig.ORDER_API);
         StringEntity postEntity = new StringEntity(xmlParams,"UTF-8");
         httpPost.addHeader("Content-Type","text/xml");
         httpPost.setEntity(postEntity);
         //连接超时时间，默认10秒--- 传输超时时间，默认30秒
         httpPost.setConfig(RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(30000).build());
         CloseableHttpResponse response = HttpClients.createDefault().execute(httpPost);
         Logger.info("executing request" + httpPost.getRequestLine());
         Logger.info("post  order status"+response.getStatusLine());
         try{
             HttpEntity httpEntity = response.getEntity();
             result = EntityUtils.toString(httpEntity,"UTF-8");
             Logger.info("order success: "+result);
             return result;
         }finally {
             response.close();
         }
     }

     /*
     * app 下单
     * **/
    public static Map<String,Object> postOrder(Long total_fee,Long tradeNO,String devIp, CompanyCustomer companyCustomer,int type) throws Exception{
        if((type!=1 && type!= 2) || devIp == null || companyCustomer == null){
            Logger.info("invalid app post order params");
           return null;
        }
        String appId = "";
        String mchId = "";
        String notifyURL = "";
        Map<String,Object> resultMap = null;
        if(type == 1){
            appId = WXPayConfig.appID1;
            mchId = WXPayConfig.mchID1;
            notifyURL = WXPayConfig.NOTIFY_URL1;
        }else if(type == 2){
            appId = WXPayConfig.appID2;
            mchId = WXPayConfig.mchID2;
            notifyURL = WXPayConfig.NOTIFY_URL2;
        }
        SortedMap<String,Object> sortedMapParams = new TreeMap<>();
        sortedMapParams.put("appid",appId);
        sortedMapParams.put("mch_id",mchId);
        sortedMapParams.put("body",companyCustomer.company.name +"为"+companyCustomer.customer.name+"充值：");
        sortedMapParams.put("nonce_str",WXPayUtil.getRandomStringByLength(32));
        sortedMapParams.put("notify_url",notifyURL);
        sortedMapParams.put("out_trade_no",String.valueOf(tradeNO.longValue()));
        sortedMapParams.put("spbill_create_ip",devIp);
        sortedMapParams.put("total_fee",String.valueOf(total_fee.longValue()));
        sortedMapParams.put("trade_type","APP");
        String sign = WXPayUtil.getSign(sortedMapParams,type);
        sortedMapParams.put("sign",sign);
        String requestXML = WXPayXMLParser.getXMLStrFromMap(sortedMapParams);

        HttpPost httpPost = new HttpPost(WXPayConfig.APP_ORDER_API);
        StringEntity postEntity = new StringEntity(requestXML,"UTF-8");
        httpPost.addHeader("Content-Type","text/xml");
        httpPost.setEntity(postEntity);
        //连接超时时间，默认10秒--- 传输超时时间，默认30秒
        httpPost.setConfig(RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(30000).build());
        CloseableHttpResponse response = HttpClients.createDefault().execute(httpPost);
        Logger.info("executing app order request" + httpPost.getRequestLine());
        Logger.info("app post  order status"+response.getStatusLine());
        try{
            String resultXMLStr = null;
            HttpEntity httpEntity = response.getEntity();
            resultXMLStr = EntityUtils.toString(httpEntity,"UTF-8");
            Logger.info("app order success: "+resultXMLStr);
            if(resultXMLStr == null){
                return null;
            }else {
                if(WXPayUtil.checkIsSignValidFromResponseString(resultXMLStr,type)){
                   Logger.info("APP 下单请求验证签名success");
                   resultMap = WXPayXMLParser.getMapFromXML(resultXMLStr);
                }else {
                    Logger.info("APP 下单请求验证签名失败");
                    return null;
                }
            }
            return resultMap;
        }finally {
            response.close();
        }
    }

     /*
     * 微信商户转账接口
     * */
     public static String postTransfer(String xmlParams) throws Exception{
         String transferStr = null;
         KeyStore keyStore = KeyStore.getInstance("PKCS12");
         FileInputStream inputStream = new FileInputStream(play.Play.application().getFile("app/apiclient_cert.p12"));
         keyStore.load(inputStream,"1402323202".toCharArray());
         inputStream.close();
         SSLContext sslContext = SSLContexts.custom().loadKeyMaterial(keyStore,"1402323202".toCharArray()).build();
         SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,new String[]{"TLSv1"},null,SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
         CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
         HttpPost httpost = new HttpPost(WXPayConfig.TRANSFER_API);
         httpost.addHeader("Connection", "keep-alive");
         httpost.addHeader("Accept", "*/*");
         httpost.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
         httpost.addHeader("Host", "api.mch.weixin.qq.com");
         httpost.addHeader("X-Requested-With", "XMLHttpRequest");
         httpost.addHeader("Cache-Control", "max-age=0");
         httpost.addHeader("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.0) ");
         httpost.setEntity(new StringEntity(xmlParams,"UTF-8"));
         CloseableHttpResponse response = httpClient.execute(httpost);
         Logger.info("executing request" + httpost.getRequestLine());
         Logger.info("post  order status"+response.getStatusLine());
         try{
             HttpEntity httpEntity = response.getEntity();
             transferStr = EntityUtils.toString(httpEntity,"UTF-8");
             Logger.info("transfer success: "+transferStr);
             return transferStr;
         }finally {
             response.close();
         }
     }

}
