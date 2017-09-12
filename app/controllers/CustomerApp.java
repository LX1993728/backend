package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import models.*;
import utils.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.mindrot.jbcrypt.BCrypt;

import play.mvc.*;
import play.db.jpa.*;
import play.Logger;
import static play.libs.Json.toJson;

import javax.persistence.*;

import java.util.*;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;

public class CustomerApp extends Controller {
    private static final String PLATFORM_VERSION = "4.0";
    public static final String TEMPLATE_ID = "77722"; //forget pwd or register

    private static ObjectNode commentLogin(Customer customer) throws Exception{
        Calendar calendar = Calendar.getInstance();
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.update((UUID.randomUUID().toString().toUpperCase() + customer.name + calendar.getTimeInMillis()).getBytes());
        customer.token = Base64.encodeBase64String(messageDigest.digest());
        calendar.add(Calendar.DAY_OF_MONTH, 3650);
        customer.expiredWhen = calendar.getTime();
        if (customer.smsEnabled) {  // first time login
            customer.smsEnabled = false;
            customer.dynamicConfirmCode = true;
        }
        EntityManager em = JPA.em();
        em.persist(customer);
        JsonNode jcustomer = toJson(customer);
        ObjectNode o = (ObjectNode)jcustomer;
        o.put("token", customer.token);
        o.put("confirmCode", customer.confirmCode);
        o.put("dynamicConfirmCode", customer.dynamicConfirmCode);
        return o;
    }


    @Transactional
    public static Result confirmWeChat(String signature,String timestamp,String nonce,String echostr) throws Exception
    {
       if(WXSignature.checkSignature(signature,timestamp,nonce,WXConfig.WECHAT_TOKEN)){
           return ok(echostr);
       }else {
           return notFound();
       }
    }

    @Transactional
    public  static Result getWeChatInfo() throws Exception{

        Map<String,Object> map = new HashMap<>();
        map.put("appId", WXConfig.APPID);
        map.put("appSecret",WXConfig.APPSECRET);
        return ok(toJson(map));
    }

    @Transactional
    public  static  Result getWeChatUserInfo(String code, String state) throws  Exception{
        if(code!=null && state != null){
            Map<String,String> map = WXGetUserInfo.requestAccessTokenForAuth(WXConfig.APPID,WXConfig.APPSECRET,code);
            if(map != null){
             Map userInfo =   WXGetUserInfo.requestUserInfoBycode(map.get("openid"),map.get("access_token"));
//               WeChatUser weChatUser = WeChatUser.instance(toJson(userInfo).toString());
                Query query = JPA.em().createNamedQuery("findCustomersByOpenId");
                query.setParameter("openid",(String)userInfo.get("openid"));
                List<Customer> customers = query.getResultList();
                Map<String,Object> mapInfo = new HashMap<>();
                mapInfo.put("weChatUser",userInfo);
                if(customers.isEmpty()){
                    mapInfo.put("customer",null);
                }else {
                    Customer customer = customers.get(0);
                    customer.weChatUser.copy(userInfo);  //每次如果登录成功，则更新用户的微信信息
                    JPA.em().persist(customer.weChatUser);
                    JPA.em().persist(customer);
                    mapInfo.put("customer",commentLogin(customer));
                }
                return ok(toJson(mapInfo));
            }else{
             return   notFound();
            }
        }else {
            return badRequest();
        }
    }

    @Transactional
    public static Result getIsFollowWeChatInfo(String code,String name,String posterId) throws Exception{
        if(code == null || name==null || posterId==null){
            return badRequest("code name or posterId can not null");
        }
        Poster poster = JPA.em().find(Poster.class,Long.parseLong(posterId));
        if(poster == null){
            return badRequest("error parameter posterId");
        }
       Map<String,String> openIdMap = WXGetUserInfo.requestAccessTokenForAuth(WXConfig.APPID,WXConfig.APPSECRET,code);
        Map<String,Object> tokenMap = WXGetAccessToken.getAccessToken();
        Logger.info("openId:"+openIdMap+" tokenMap:"+tokenMap);
        if(openIdMap!=null && tokenMap!=null){
            Map<String,String> followMap = WXGetUserInfo.requestIsFollowWeChat(openIdMap.get("openid"),(String) tokenMap.get("access_token"));
            if(followMap!=null){
                String subscribeStr = followMap.get("subscribe");
                Map<String,Object> returnData = new HashMap<>();
                if("1".equals(subscribeStr)){
                    returnData.put("subscribe",true);
                }else {
                    returnData.put("subscribe",false);
                }
                Query query = JPA.em().createNamedQuery("findCustomersByName");
                query.setParameter("customerName", name);
                List<Customer> customers = query.getResultList();
                Customer customer = null;
                if (!customers.isEmpty()) {
                    customer = customers.get(0);
                } else {
                    String password = String.format("%06d", new Random().nextInt(999999));
//                    new Message(name, password, "90982").start();
                    customer = new Customer(name, password);
                    JPA.em().persist(customer);
                }
                Boolean flag1 = true;
                Boolean flag2 = true;
                Query queryPreCustomers = JPA.em().createNamedQuery("findCustomersByOpenId");
                queryPreCustomers.setParameter("openid",followMap.get("openid"));
                List<Customer> customerpre = queryPreCustomers.getResultList();
                if(!customerpre.isEmpty()){
                    for (Customer cus:customerpre) {
                        if(cus.weChatUser.isEnabled){
                           flag1 = false;
                        }
                    }
                }
                if(customer.weChatUser!=null && customer.weChatUser.isEnabled){
                   flag2 = false;
                }
                if(flag1 && flag2){
                    WeChatUser weChatUser = new WeChatUser();
                    if("1".equals(subscribeStr)){
                        weChatUser.copy(followMap);
                    }else {
                       weChatUser.openid = followMap.get("openid");
                    }
                    customer.weChatUser = weChatUser;
                    JPA.em().persist(customer.weChatUser);
                    JPA.em().persist(customer);
                    JPA.em().persist(new WeChatBindLog(customer,customer.weChatUser));
                }


                if (poster instanceof CompanyTicket) {
                    CompanyTicket companyTicket = (CompanyTicket)poster;
                    TicketBatch batch = companyTicket.batch;

                    if (batch.current >= batch.maxNumber) {
                        Logger.info("can not request, batch limit");
                        returnData.put("status",-1);
                        return ok(toJson(returnData));
                    } else {
                        batch.current += 1;
                        batch.leftNumber -= 1;
                        JPA.em().persist(batch);
                    }

                    query = JPA.em().createNamedQuery("findCustomerTicketsByCompanyTicket");
                    query.setParameter("customer", customer);
                    query.setParameter("companyTicket", companyTicket);
                    List<Ticket> tickets = query.getResultList();
                    if (tickets.size() > 0) {
                        Logger.info("ticket already requested");
                        returnData.put("status",0);
                        return ok(toJson(returnData));
                    }

                    Calendar calendar = Calendar.getInstance();
                    query = JPA.em().createNamedQuery("queryStaticsTicketRequest");
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    query.setParameter("date", calendar.getTime());
                    query.setParameter("companyTicket", companyTicket);
                    StaticsTicketRequest staticsRequest = null;
                    List<StaticsTicketRequest> staticsRequests = query.getResultList();
                    if (staticsRequests.isEmpty()) {
                        staticsRequest = new StaticsTicketRequest(calendar.getTime(), companyTicket);
                    } else {
                        staticsRequest = staticsRequests.get(0);
                    }

                    query = JPA.em().createNamedQuery("queryCompanyCustomer");
                    query.setParameter("company", companyTicket.company);
                    query.setParameter("customer", customer);
                    List<CompanyCustomer> companyCustomers = query.getResultList();
                    if (companyCustomers.isEmpty()) {
                        JPA.em().persist(new CompanyCustomer(companyTicket.company, customer, staticsRequest));
                        staticsRequest.regs += 1L;
                    }

                    if (batch instanceof DeductionBatch) {
                        DeductionTicket ticket = new DeductionTicket(customer, companyTicket, staticsRequest);
                        staticsRequest.deductionAmount += ticket.deduction;
                        staticsRequest.deductionCount += 1;
                        JPA.em().persist(staticsRequest);
                        JPA.em().persist(ticket);
                    } else if (batch instanceof DiscountBatch) {
                        DiscountTicket ticket = new DiscountTicket(customer, companyTicket, staticsRequest);
                        staticsRequest.discountCount += 1;
                        JPA.em().persist(staticsRequest);
                        JPA.em().persist(ticket);
                    } else if (batch instanceof GrouponBatch) {
                        GrouponTicket ticket = new GrouponTicket(customer, companyTicket, staticsRequest);
                        staticsRequest.grouponCost += ticket.cost;
                        staticsRequest.grouponAmount += ticket.deduction;
                        staticsRequest.grouponCount += 1;
                        JPA.em().persist(staticsRequest);
                        JPA.em().persist(ticket);
                    } else if (batch instanceof CompanyProductBatch){
                        CompanyProductTicket ticket = new CompanyProductTicket(customer, companyTicket, staticsRequest);
                        JPA.em().persist(ticket);
                        JPA.em().persist(staticsRequest);
                    } else {
                        Logger.error("unknow ticket batch!");
                        returnData.put("status",-1);
                        return ok(toJson(returnData));
                    }
                } else {
                    Logger.error("not a CompanyTicket");
                    returnData.put("status",-1);
                    return ok(toJson(returnData));
                }
                returnData.put("status",1);
               return ok(toJson(returnData));
            }else {
                return badRequest("request follow info failure");
            }
        }else {
           return badRequest("openId or token canot be null");
        }
    }


    @Transactional
    public static Result getSMSConfirmCode(String telephoneNumber)throws Exception{
        if(!telephoneNumber.matches("^1\\d{10}$")){
           return badRequest("invalid telephone");
        }else {
            WeChatSMS weChatSMS = null;
            Query query = JPA.em().createNamedQuery("findSMSCodeByTelephoneNumber");
            query.setParameter("telephoneNumber",telephoneNumber);
            List<WeChatSMS> weChatSMSes = query.getResultList();
            if(!weChatSMSes.isEmpty()){
             weChatSMS = weChatSMSes.get(0);
             weChatSMS.smsCode = String.format("%04d", new Random().nextInt(9999));
            }else {
                weChatSMS = new WeChatSMS(telephoneNumber,String.format("%04d", new Random().nextInt(9999)));
            }
            JPA.em().persist(weChatSMS);
            new Message(telephoneNumber,weChatSMS.smsCode,TEMPLATE_ID).start();
           return ok("success");
        }
    }

    @Transactional
    public static  Result confirmSMSCodeAndBindWeChat() throws Exception{
        WeChatSMS weChatSMS = null;
        JsonNode jsonNode = request().body().asJson();
        ObjectNode wxUserNode = (ObjectNode) jsonNode.findPath("weChatUser");
        String openId = wxUserNode.get("openid").asText();
        String telephoneNumber = jsonNode.findPath("telephoneNumber").asText();
        String smsCode  = jsonNode.findPath("smsCode").asText();
        if(openId==null || telephoneNumber == null || smsCode ==null){
            return badRequest("parameter incorrect");
        }
        Query query = JPA.em().createNamedQuery("findSMSCodeByTelephoneNumber");
        query.setParameter("telephoneNumber",telephoneNumber);
        List<WeChatSMS> weChatSMSes = query.getResultList();
        if(!weChatSMSes.isEmpty()){
            weChatSMS = weChatSMSes.get(0);
            if(weChatSMS.smsCode.equals(smsCode)){
                Query queryPreCustomers = JPA.em().createNamedQuery("findCustomersByOpenId");
                queryPreCustomers.setParameter("openid",openId);
                List<Customer> customerpre = queryPreCustomers.getResultList();
                if(!customerpre.isEmpty()){
                    for (Customer customer:customerpre) {
                       if(customer.weChatUser.isEnabled){
                           customer.weChatUser.isEnabled = false;
                           JPA.em().persist(customer.weChatUser);
                       }
                    }
                }
                Query query1 = JPA.em().createNamedQuery("findCustomersByName");
                query1.setParameter("customerName",telephoneNumber);
                List<Customer> customers = query1.getResultList();
                if(!customers.isEmpty()){
                    Customer customer = customers.get(0);
                    WeChatUser weChatUser = customer.weChatUser;
                    if(weChatUser!=null){
                        if(!weChatUser.openid.equals(openId)){
                            weChatUser.isEnabled = false;
                            JPA.em().persist(weChatUser);
                            WeChatUser newWXU = WeChatUser.instance(wxUserNode.toString());
                            customer.weChatUser = newWXU;
                            WeChatBindLog weChatDisbandLog = new WeChatBindLog(customer,weChatUser); //将以前的解绑 记录
                            WeChatBindLog weChatBindLog = new WeChatBindLog(customer,newWXU);        // 与新的微信号信息 绑定记录
                            JPA.em().persist(newWXU);
                            JPA.em().persist(customer);
                            JPA.em().persist(weChatBindLog);
                            JPA.em().persist(weChatDisbandLog);
                        }else { //
                           Map mapWXU = new ObjectMapper().convertValue(wxUserNode,Map.class);
                           weChatUser.copy(mapWXU);
                           weChatUser.isEnabled = true;
                           JPA.em().persist(weChatUser);
                        }

                    }else {
                        customer.weChatUser = WeChatUser.instance(wxUserNode.toString());
                        WeChatBindLog bindLog = new WeChatBindLog(customer,customer.weChatUser);
                        JPA.em().persist(customer.weChatUser);
                        JPA.em().persist(customer);
                        JPA.em().persist(bindLog);
                    }
                    return ok(toJson(commentLogin(customer)));
                }else {

                    String password = String.format("%06d", new Random().nextInt(999999));
                    Customer customer = new Customer(telephoneNumber, password);
                    customer.passwordChangeCode = String.format("%04d", new Random().nextInt(9999));
                    customer.weChatUser =  WeChatUser.instance(wxUserNode.toString());
                    JPA.em().persist(customer.weChatUser);
                    JPA.em().persist(customer);
                    JPA.em().persist(new WeChatBindLog(customer,customer.weChatUser));
                    new Message(customer.name, password, "90982").start();
                    return ok(toJson(commentLogin(customer)));
                }
            }else {
                return badRequest("smsCode incorrect");
            }
        }else {
            return badRequest("telephoneNumber incorrect");
        }
    }
  @Transactional
  public static Result getWXJSConfigInfo() throws Exception{
      JsonNode json = request().body().asJson();
      JsonNode urlNode = json.get("url");
      String url = null;
      if(urlNode!=null){
          url = urlNode.asText();
      }else {
          return badRequest("invalid url");
      }
      Map<String,Object> mapInfo = WXJSAPIConfigInfo.getAPIConfigInfo(url);
      if(mapInfo != null){
          return ok(toJson(mapInfo));
      }else {
          return badRequest();
      }
  }
   @Transactional
   public static Result getSharePosterByPosterId(long posterId){
       Poster poster = JPA.em().find(Poster.class,posterId);
       if(poster!=null){
           return ok(toJson(poster));
       }else {
           return notFound();
       }
   }


//----------------------------------------------------------------------
    /**
     * app recharge for companycustomer
     * */
   @Transactional
   public static Result getOrderInfo(Long customerId, Long companyId, int type) throws Exception{
       if(customerId == null || companyId == null){
           return badRequest(" customerId and companyId can not be null");
       }
       if (!isVersionMatch("1")) {
           return status(412, "version mismatch");
       }

       Customer customer = JPA.em().find(Customer.class, customerId);
       if(customer == null){
           return badRequest("invalid customerId");
       }
       if (!isTokenValid(customer.token, customer.expiredWhen)) {
           return unauthorized();
       }
       Company company = JPA.em().find(Company.class,companyId);
       if(company == null){
           return badRequest("invalid companyId");
       }
       JsonNode json = request().body().asJson();
       Long totalFee = json.findPath("total_fee").asLong();
       String ip = json.findPath("ip").asText();
       if(ip == null || totalFee == null){
           return badRequest("ip and total_fee can not be null");
       }
       Query queryCustomer = JPA.em().createNamedQuery("queryCompanyCustomer");
       queryCustomer.setParameter("company", company);
       queryCustomer.setParameter("customer",customer);
       List<CompanyCustomer> companyCustomers = queryCustomer.getResultList();
       if(!companyCustomers.isEmpty()){
           RechargeStatus rechargeStatus = new RechargeStatus(companyCustomers.get(0),0);
           JPA.em().persist(rechargeStatus);
           Map<String,Object> orderResultMap = WXPayCore.postOrder(totalFee,rechargeStatus.id,ip,companyCustomers.get(0),type);
           if(orderResultMap!=null){
              CustomerTrasactionLog customerTrasactionLog = new CustomerTrasactionLog(rechargeStatus,orderResultMap.toString(),type);
               JPA.em().persist(customerTrasactionLog);
               Logger.info("---return data= "+orderResultMap.toString());
               return ok(toJson(orderResultMap));
           }else {
               Logger.info("---app order failure ");
               return badRequest("app order failure");
           }

       }else {
           return badRequest("customer is not of companyCustomer of this company");
       }

   }

public static  String commonNotifyInfo(int type) throws Exception{

    String xmlStr = null;
    String resXml="";//反馈给微信服务器
    Pattern p = Pattern.compile(".*(<xml>.*</xml>).*");
    Matcher m = p.matcher(request().body().toString().replaceAll("\\s",""));
    if(m.find()){
        Logger.info(m.group(1));
        xmlStr = m.group(1);
    }else {
        return null;
    }
    // 验证签名
    if(WXPayUtil.checkIsSignValidFromResponseString(xmlStr,type)){
        if("SUCCESS".equals(WXPayXMLParser.getMapFromXML(xmlStr).get("result_code"))){
            Logger.info("支付成功"+"----"+"交易单号:"+WXPayXMLParser.getMapFromXML(xmlStr).toString());
            Map<String,Object> mapSuccess = WXPayXMLParser.getMapFromXML(xmlStr);
            String out_trade_no =  (String) mapSuccess.get("out_trade_no");
            Map<String,Object> resultMap =WXPayQueryOrder.queryOrderOfCustomer(out_trade_no,type);
            if(type == 1){
                Logger.info("Android WX pay: "+resultMap.toString());
            }else if(type == 2) {
                Logger.info("ios WX pay: "+resultMap.toString());
            }
            if(resultMap!=null && "SUCCESS".equals(resultMap.get("result_code"))){
                // resultmap  如果不为空 与mapsuccess 是相同的
                RechargeStatus rechargeStatus = JPA.em().find(RechargeStatus.class,Long.parseLong(out_trade_no));
                if(rechargeStatus != null && rechargeStatus.customerStatus != 1){
                    Recharge recharge = new Recharge(rechargeStatus.companyCustomer, Long.parseLong((String) resultMap.get("total_fee")));
                    rechargeStatus.customerStatus = 1;
                    rechargeStatus.recharge = recharge;
                    RechargeInfo rechargeInfo = new RechargeInfo(rechargeStatus.companyCustomer.customer, rechargeStatus.companyCustomer.company, recharge);
                    JPA.em().persist(recharge);
                    JPA.em().persist(rechargeInfo);
                    JPA.em().persist(rechargeStatus);
                    Query queryCusLog = JPA.em().createNamedQuery("queryCusRechargeLogByStatus");
                    queryCusLog.setParameter("rechargeStatus",rechargeStatus);
                    List<CustomerTrasactionLog> customerTrasactionLogs = queryCusLog.getResultList();
                    if(!customerTrasactionLogs.isEmpty()){
                        CustomerTrasactionLog customerTrasactionLog = customerTrasactionLogs.get(0);
                        customerTrasactionLog.setResponse(resultMap.toString(),"SUCCESS");
                        JPA.em().persist(customerTrasactionLog);

                        Calendar calendar = Calendar.getInstance();
                        Query query = JPA.em().createNamedQuery("queryStaticsConsume");
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                        calendar.set(Calendar.MINUTE, 0);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 0);
                        query.setParameter("date", calendar.getTime());
                        query.setParameter("company", rechargeStatus.companyCustomer.company);
                        StaticsConsume staticsConsume = null;
                        List<StaticsConsume> staticsConsumes = query.getResultList();
                        if (staticsConsumes.isEmpty()) {
                            staticsConsume = new StaticsConsume(rechargeStatus.companyCustomer.company, calendar.getTime(), 0L, 0L, 0L, 0L, 0L, Long.parseLong((String) resultMap.get("total_fee")), 0L, 0L);
                        } else {
                            staticsConsume = staticsConsumes.get(0);
                            staticsConsume.recharge += Long.parseLong((String) resultMap.get("total_fee"));
                        }
                        JPA.em().persist(staticsConsume);
                        Logger.info("充值成功");
                    }

                }
            }

            // 通知微信.异步确认成功.必写.不然会一直通知后台.八次之后就认为交易失败了.
            resXml = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"
                    + "<return_msg><![CDATA[OK]]></return_msg>" + "</xml> ";
        }else {
            Logger.info("支付失败,错误信息：" + WXPayXMLParser.getMapFromXML(xmlStr).get("err_code"));
            Map<String,Object> mapFailure = WXPayXMLParser.getMapFromXML(xmlStr);
            if(mapFailure.get("out_trade_no") != null){
                RechargeStatus rechargeStatus = JPA.em().find(RechargeStatus.class,Long.parseLong((String) mapFailure.get("out_trade_no")));
                if(rechargeStatus != null && rechargeStatus.customerStatus!=1){
                    rechargeStatus.customerStatus = -1;
                    Query queryCusLog = JPA.em().createNamedQuery("queryCusRechargeLogByStatus");
                    queryCusLog.setParameter("rechargeStatus",rechargeStatus);
                    List<CustomerTrasactionLog> customerTrasactionLogs = queryCusLog.getResultList();
                    if(!customerTrasactionLogs.isEmpty()){
                        CustomerTrasactionLog errorLog = customerTrasactionLogs.get(0);
                        errorLog.setResponse(mapFailure.toString(),(String)mapFailure.get("err_code"));
                        JPA.em().persist(rechargeStatus);
                        JPA.em().persist(errorLog);
                    }

                }
            }
            Logger.info("充值失败");
            resXml = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
                    + "<return_msg><![CDATA[签名验证错误]]></return_msg>" + "</xml> ";
        }
    }else {
        Logger.info("签名验证错误");
        resXml = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
                + "<return_msg><![CDATA[签名验证错误]]></return_msg>" + "</xml> ";
    }
    return resXml;
}

   @Transactional
   public static Result getWXPayNotifyInfo1() throws Exception{
       Logger.info("=============================");
       String result = commonNotifyInfo(1);
       if(result != null){
           return ok(result);
       }else {
           return badRequest();
       }
   }
    @Transactional
    public static Result getWXPayNotifyInfo2() throws Exception{
        String result = commonNotifyInfo(2);
        if(result != null){
            return ok(result);
        }else {
            return badRequest();
        }
    }

//-----------------------------------------------------------------
    @Transactional
    public static Result customerLogin() throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        String customerName = json.findPath("name").asText();
        String password = json.findPath("password").asText();

        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", customerName);
        List<Customer> customers = query.getResultList();
        if (customers.isEmpty()) {
            return unauthorized("unauthorized");
        } else {
            Customer customer = customers.get(0);
            if (BCrypt.checkpw(password, customer.password)) {
                Calendar calendar = Calendar.getInstance();
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.update((UUID.randomUUID().toString().toUpperCase() + customer.name + calendar.getTimeInMillis()).getBytes());
                customer.token = Base64.encodeBase64String(messageDigest.digest());
                calendar.add(Calendar.DAY_OF_MONTH, 3650);
                customer.expiredWhen = calendar.getTime();
                if (customer.smsEnabled) {  // first time login
                    customer.smsEnabled = false;
                    customer.dynamicConfirmCode = true;
                }
                EntityManager em = JPA.em();
                em.persist(customer);

                JsonNode jcustomer = toJson(customer);
                ObjectNode o = (ObjectNode)jcustomer;
                o.put("token", customer.token);
                o.put("confirmCode", customer.confirmCode);
                o.put("dynamicConfirmCode", customer.dynamicConfirmCode);
                return ok(o);
            } else {
                return unauthorized("unauthorized");
            }
        }
    }

    @Transactional
    public static Result register() throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        String customerName = json.findPath("name").asText();
        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", customerName);
        List<Customer> customers = query.getResultList();
        Customer customer = null;
        if (!customers.isEmpty()) {
            customer = customers.get(0);
        } else {
            String password = String.format("%06d", new Random().nextInt(999999));
            customer = new Customer(customerName, password);
        }
        customer.passwordChangeCode = String.format("%04d", new Random().nextInt(9999));
        JPA.em().persist(customer);
        //String content = String.format(Customer.PASSWORD_CHANGE_CODE_CONTENT, customer.passwordChangeCode);
        new Message(customer.name, customer.passwordChangeCode, TEMPLATE_ID).start();
        return ok();
    }


    @Transactional
    public static Result customerConfirmCodeForChangePassword() throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        String customerName = json.findPath("name").asText();
        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", customerName);
        List<Customer> customers = query.getResultList();
        if (customers.isEmpty()) {
            return notFound();
        } else {
            Customer customer = customers.get(0);
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            Date d = calendar.getTime();
            if (d.equals(customer.passwordChangeRequestDate)) {
                if (customer.passwordChangeLeft <= 0) {
                    return forbidden();
                } else {
                    customer.passwordChangeLeft -= 1;
                    customer.passwordChangeCode = String.format("%04d", new Random().nextInt(9999));
                    EntityManager em = JPA.em();
                    em.persist(customer);
                    //String content = String.format(Customer.PASSWORD_CHANGE_CODE_CONTENT, customer.passwordChangeCode);
                    new Message(customer.name, customer.passwordChangeCode, TEMPLATE_ID).start();
                    return ok();
                }
            } else {
                customer.passwordChangeRequestDate = d;
                customer.passwordChangeLeft = 2;
                customer.passwordChangeCode = String.format("%04d", new Random().nextInt(9999));
                EntityManager em = JPA.em();
                em.persist(customer);
                //String content = String.format(Customer.PASSWORD_CHANGE_CODE_CONTENT, customer.passwordChangeCode);
                new Message(customer.name, customer.passwordChangeCode, TEMPLATE_ID).start();
                return ok();
            }
        }
    }

    @Transactional
    public static Result changePassword() throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        String customerName = json.findPath("name").asText();
        String password = json.findPath("password").asText();
        String passwordChangeCode = json.findPath("passwordChangeCode").asText();
        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", customerName);
        List<Customer> customers = query.getResultList();
        if (customers.isEmpty()) {
            return notFound();
        } else {
            Customer customer = customers.get(0);
            if (customer.passwordChangeCode != null && customer.passwordChangeCode.equals(passwordChangeCode)) {
                JPA.em().persist(customer.resetPassword(password));
                return ok();
            } else {
                return forbidden();
            }
        }
    }

    @Transactional
    public static Result staticsHits()
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        String type = json.findPath("type").asText(); //0:homepage, 1:product, 2:companyTicket, 3:advertisement, 4:batch
        Long id = json.findPath("id").asLong();
        boolean isMember = json.findPath("isMember").asBoolean();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (type.equals("homepage")) {
            Company company = JPA.em().find(Company.class, id);

            Query query = JPA.em().createNamedQuery("queryStaticsHomePageHits");
            query.setParameter("date", calendar.getTime());
            query.setParameter("company", company);
            StaticsHomePageHits staticsHomePageHits = null;
            List<StaticsHomePageHits> staticsHomePageHitsess = query.getResultList();
            if (staticsHomePageHitsess.isEmpty()) {
                if (isMember == true) {
                    staticsHomePageHits = new StaticsHomePageHits(calendar.getTime(), 1L, 1L, 0L, company);
                } else {
                    staticsHomePageHits = new StaticsHomePageHits(calendar.getTime(), 1L, 0L, 1L, company);
                }
            } else {
                staticsHomePageHits = staticsHomePageHitsess.get(0);
                staticsHomePageHits.hits += 1;
                if (isMember == true) {
                    staticsHomePageHits.membersHits += 1;
                } else {
                    staticsHomePageHits.nonmemberHits += 1;
                }
            }
            JPA.em().persist(staticsHomePageHits);
            return ok();
        } else if (type.equals("product")) {
            Product product = JPA.em().find(Product.class, id);

            Query query = JPA.em().createNamedQuery("queryStaticsProductHits");
            query.setParameter("date", calendar.getTime());
            query.setParameter("product", product);
            StaticsProductHits staticsProductHits = null;
            List<StaticsProductHits> staticsProductHitsess = query.getResultList();
            if (staticsProductHitsess.isEmpty()) {
                if (isMember == true) {
                    staticsProductHits = new StaticsProductHits(calendar.getTime(), 1L, 1L, 0L, product);
                } else {
                    staticsProductHits = new StaticsProductHits(calendar.getTime(), 1L, 0L, 1L, product);
                }
            } else {
                staticsProductHits = staticsProductHitsess.get(0);
                staticsProductHits.hits += 1;
                if (isMember == true) {
                    staticsProductHits.membersHits += 1;
                } else {
                    staticsProductHits.nonmemberHits += 1;
                }
            }
            JPA.em().persist(staticsProductHits);
            return ok();
        } else if (type.equals("companyTicket")) {
            CompanyTicket companyTicket = JPA.em().find(CompanyTicket.class, id);

            Query query = JPA.em().createNamedQuery("queryStaticsCompanyTicketHits");
            query.setParameter("date", calendar.getTime());
            query.setParameter("companyTicket", companyTicket);
            StaticsCompanyTicketHits staticsCompanyTicketHits = null;
            List<StaticsCompanyTicketHits> staticsCompanyTicketHitses = query.getResultList();
            if (staticsCompanyTicketHitses.isEmpty()) {
                if (isMember == true) {
                    staticsCompanyTicketHits = new StaticsCompanyTicketHits(calendar.getTime(), 1L, 1L, 0L, companyTicket);
                } else {
                    staticsCompanyTicketHits = new StaticsCompanyTicketHits(calendar.getTime(), 1L, 0L, 1L, companyTicket);
                }
            } else {
                staticsCompanyTicketHits = staticsCompanyTicketHitses.get(0);
                staticsCompanyTicketHits.hits += 1;
                if (isMember == true) {
                    staticsCompanyTicketHits.membersHits += 1;
                } else {
                    staticsCompanyTicketHits.nonmemberHits += 1;
                }
            }
            JPA.em().persist(staticsCompanyTicketHits);
            return ok();
        } else if (type.equals("advertisement")) {
            Advertisement advertisement = JPA.em().find(Advertisement.class, id);

            Query query = JPA.em().createNamedQuery("queryStaticsAdvertisementHits");
            query.setParameter("date", calendar.getTime());
            query.setParameter("advertisement", advertisement);
            StaticsAdvertisementHits staticsAdvertisementHits = null;
            List<StaticsAdvertisementHits> staticsAdvertisementHitss = query.getResultList();
            if (staticsAdvertisementHitss.isEmpty()) {
                if (isMember == true) {
                    staticsAdvertisementHits = new StaticsAdvertisementHits(calendar.getTime(), 1L, 1L, 0L, advertisement);
                } else {
                    staticsAdvertisementHits = new StaticsAdvertisementHits(calendar.getTime(), 1L, 0L, 1L, advertisement);
                }
            } else {
                staticsAdvertisementHits = staticsAdvertisementHitss.get(0);
                staticsAdvertisementHits.hits += 1;
                if (isMember == true) {
                    staticsAdvertisementHits.membersHits += 1;
                } else {
                    staticsAdvertisementHits.nonmemberHits += 1;
                }
            }
            JPA.em().persist(staticsAdvertisementHits);
            return ok();
        } else if (type.equals("batch")){
            Batch batch = JPA.em().find(Batch.class, id);

            Query query = JPA.em().createNamedQuery("queryStaticsBatchHits");
            query.setParameter("date", calendar.getTime());
            query.setParameter("batch", batch);
            StaticsBatchHits staticsBatchHits = null;
            List<StaticsBatchHits> staticsBatchHitses = query.getResultList();
            if (staticsBatchHitses.isEmpty()) {
                if (isMember == true) {
                    staticsBatchHits = new StaticsBatchHits(calendar.getTime(), 1L, 1L, 0L, batch);
                } else {
                    staticsBatchHits = new StaticsBatchHits(calendar.getTime(), 1L, 0L, 1L, batch);
                }
            } else {
                staticsBatchHits = staticsBatchHitses.get(0);
                staticsBatchHits.hits += 1;
                if (isMember == true) {
                    staticsBatchHits.membersHits += 1;
                } else {
                    staticsBatchHits.nonmemberHits += 1;
                }
            }
            JPA.em().persist(staticsBatchHits);
            return ok();
        } else {
            return badRequest();
        }
    }

    @Transactional
    public static Result customerLogout(Long id) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, id);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        customer.expiredWhen = calendar.getTime();
        EntityManager em = JPA.em();
        em.persist(customer);
        return ok();
    }
    @Transactional
    public static Result getCompanyCustomerInfo(Long customerId,Long companyId){
      if (!isVersionMatch("1")) {
          return status(412, "version mismatch");
       }

        Customer customer = JPA.em().find(Customer.class, customerId);
       if (!isTokenValid(customer.token, customer.expiredWhen)) {
           return unauthorized();
       }
        Company company = JPA.em().find(Company.class,companyId);
        if(company == null){
            return badRequest("invalid companyId");
        }
        List<CompanyCustomer> companyCustomers = JPA.em().createNamedQuery("queryCompanyCustomer").setParameter("company",company)
                .setParameter("customer",customer).getResultList();
        if(!companyCustomers.isEmpty()){
            return ok(toJson(companyCustomers.get(0)));
        }else {
            return notFound();
        }
    }

    @Transactional
    public static Result getTicketsByCompanyTicket(Long customerId, Long companyTicketId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        CompanyTicket companyTicket = JPA.em().find(CompanyTicket.class, companyTicketId);
        Query query = JPA.em().createNamedQuery("findCustomerTicketsByCompanyTicket");
        query.setParameter("customer", customer);
        query.setParameter("companyTicket", companyTicket);
        List<Ticket> tickets = query.getResultList();
        ArrayNode jtickets = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = tickets.iterator();
        while (iterator.hasNext()) {
            Ticket ticket = (Ticket)iterator.next();
            ObjectNode jticket = (ObjectNode)toJson(ticket);
            jticket.put("batchId", ticket.batch.id);
            jtickets.add(jticket);
        }
        return ok(jtickets);
    }

    @Transactional
    public static Result removeTicket(Long customerId, Long ticketId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }

        Ticket ticket = JPA.em().find(Ticket.class, ticketId);
        boolean isRemoved = json.get("isRemoved").asBoolean();
        ticket.isRemoved = isRemoved;
        JPA.em().persist(ticket);
        return ok(toJson(ticket));
    }

    @Transactional
    public static Result getUseRulesForTicket(Long customerId, Long ticketId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);
        Ticket ticket = JPA.em().find(Ticket.class, ticketId);
        Query query = JPA.em().createNamedQuery("findEnabledUseRulesOfBatch");
        query.setParameter("company", company);
        query.setParameter("batch", ticket.batch);
        List<UseRule> rules = query.getResultList();
        return ok(toJson(rules));
    }

    @Transactional
    public static Result getCompaniesAcceptTicket(Long customerId, Long ticketId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }

        Ticket ticket = JPA.em().find(Ticket.class, ticketId);
        if (ticket instanceof DiscountTicket) {
            Query query = JPA.em().createNamedQuery("findCompaniesAcceptDiscountTicket");
            query.setParameter("ticket", ticket);
            List<Company> companies = query.getResultList();
            return ok(toJson(companies));
        } else if (ticket instanceof DeductionTicket) {
            Query query = JPA.em().createNamedQuery("findCompaniesAcceptDeductionTicket");
            query.setParameter("ticket", ticket);
            List<Company> companies = query.getResultList();
            return ok(toJson(companies));
        } else {
            return ok(toJson(new ArrayList<Company>()));
        }
    }

    @Transactional
    public static Result postTransfer(Long customerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Logger.info("json: " + json);
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }
        Long ticketId = json.get("ticketId").asLong();
        String to = json.get("to").asText();
        if (to.equals(customer.name)) {
            Logger.info("can not transfer to myself");
            return badRequest("can not transfer to myself");
        }
        JPA.em().clear();

        Ticket srcTicket = JPA.em().find(Ticket.class, ticketId);
        if (srcTicket.state != 0 || srcTicket.transferLeft <= 0) {
            Logger.info("ticket is invalid");
            return badRequest("ticket is invalid");
        }

        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", to);
        List<Customer> tos = query.getResultList();
        if(srcTicket != null && (srcTicket.batch instanceof  CompanyProductBatch)){
            if(((CompanyProductBatch)srcTicket.batch).companyProduct != null){
                query = JPA.em().createNamedQuery("queryCompanyCustomer");
                query.setParameter("company", ((CompanyProductBatch)srcTicket.batch).companyProduct.company);
                query.setParameter("customer", customer);
                List<CompanyCustomer> companyCustomerList = query.getResultList();
                if (tos.isEmpty() || !srcTicket.batch.company.companyCustomers.contains(companyCustomerList.get(0))) {
                    return badRequest("this customer is not manufacturer's member");
                }
            }
        }

        srcTicket.state = 2;

        Calendar calendar = Calendar.getInstance();
        query = JPA.em().createNamedQuery("queryStaticsTransfer");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", srcTicket.company);
        StaticsTransfer staticsTransfer = null;
        List<StaticsTransfer> staticsTransfers = query.getResultList();
        if (staticsTransfers.isEmpty()) {
            staticsTransfer = new StaticsTransfer(calendar.getTime(), srcTicket.company);
        } else {
            staticsTransfer = staticsTransfers.get(0);
        }

        Customer toCustomer = null;
        boolean customerExist = false;
        if (tos.isEmpty()) {
            String password = String.format("%06d", new Random().nextInt(999999));
            new Message(to, password, "90982").start();
            toCustomer = new Customer(to, password);
            JPA.em().persist(toCustomer);
            JPA.em().persist(new CompanyCustomer(srcTicket.company, toCustomer, staticsTransfer));
            staticsTransfer.regs += 1L;
        } else {
            customerExist = true;
            toCustomer = tos.get(0);
            query = JPA.em().createNamedQuery("queryCompanyCustomer");
            query.setParameter("company", srcTicket.company);
            query.setParameter("customer", toCustomer);
            List<CompanyCustomer> companyCustomers = query.getResultList();
            if (companyCustomers.isEmpty()) {
                JPA.em().persist(new CompanyCustomer(srcTicket.company, toCustomer, staticsTransfer));
                staticsTransfer.regs += 1L;
            }
        }
        staticsTransfer.count += 1L;
        JPA.em().persist(staticsTransfer);

        Ticket dstTicket = null;
        if (srcTicket instanceof DiscountTicket) {
            dstTicket = new DiscountTicket(toCustomer, (DiscountTicket)srcTicket);
        } else if (srcTicket instanceof DeductionTicket) {
            dstTicket = new DeductionTicket(toCustomer, (DeductionTicket)srcTicket);
        } else if (srcTicket instanceof CompanyProductTicket){
            dstTicket = new CompanyProductTicket(toCustomer, (CompanyProductTicket)srcTicket);
        } else {
            dstTicket = new GrouponTicket(toCustomer, (GrouponTicket)srcTicket);
        }
        JPA.em().persist(srcTicket);
        JPA.em().persist(dstTicket);
        Transfer transfer1 = new Transfer(srcTicket, null, null, to);
        Transfer transfer2 = new Transfer(null, dstTicket, customer.name, null);
        JPA.em().persist(transfer1);
        JPA.em().persist(transfer2);
        JPA.em().persist(new TransferInfo(customer, null, transfer1));
        JPA.em().persist(new TransferInfo(toCustomer, null, transfer2));
        HashMap rstMap = new HashMap();
        rstMap.put("customerExist", customerExist);
        rstMap.put("to", to);
        return ok(toJson(rstMap));
    }

    @Transactional
    public static Result getConsumes(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryCustomerConsumes");
        query.setParameter("customerId", customerId);
        List<Consume> consumes = query.getResultList();
        return ok(toJson(consumes));
    }

    @Transactional
    public static Result getTransfers(Long customerId, String type)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }

        List<Transfer> transfers = null;
        if (type.equals("in")) {
            Query query = JPA.em().createNamedQuery("queryCustomerTransfersIn");
            query.setParameter("customerId", customerId);
            transfers = query.getResultList();
        } else if (type.equals("out")) {
            Query query = JPA.em().createNamedQuery("queryCustomerTransfersOut");
            query.setParameter("customerId", customerId);
            transfers = query.getResultList();
        }

        List<HashMap> rst = new ArrayList<HashMap>();
        Iterator iterator = transfers.iterator();
        while (iterator.hasNext()) {
            Transfer transfer = (Transfer)iterator.next();
            HashMap t = new HashMap();
            if (type.equals("in")) {
                t.put("peer", transfer.srcTicket.customer.name);
                t.put("ticketId", transfer.dstTicket.id);
            } else if (type.equals("out")) {
                t.put("peer", transfer.dstTicket.customer.name);
                t.put("ticketId", transfer.srcTicket.id);
            }
            t.put("transferWhen", transfer.transferWhen);
            rst.add(t);
        }
        return ok(toJson(rst));
    }

    @Transactional
    public static Result postConfirmCode(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }
        customer = customer.changeConfirmCode();
        EntityManager em = JPA.em();
        em.merge(customer);
        
        HashMap confirmCodeMap = new HashMap();
        confirmCodeMap.put("confirmCode", customer.confirmCode);
        return ok(toJson(confirmCodeMap));
    }

    @Transactional
    public static Result postDynamicConfirmCode(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }

        JsonNode json = request().body().asJson();
        boolean dynamicConfirmCode = json.findPath("dynamicConfirmCode").asBoolean();
        customer.dynamicConfirmCode = dynamicConfirmCode;
        EntityManager em = JPA.em();
        em.merge(customer);

        HashMap confirmCodeMap = new HashMap();
        confirmCodeMap.put("dynamicConfirmCode", customer.dynamicConfirmCode);
        return ok(toJson(confirmCodeMap));
    }

    @Transactional
    public static Result getConfirmCode(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            Logger.info("unauthorized");
            return unauthorized();
        }
        HashMap confirmCodeMap = new HashMap();
        confirmCodeMap.put("confirmCode", customer.confirmCode);
        confirmCodeMap.put("dynamicConfirmCode", customer.dynamicConfirmCode);
        return ok(toJson(confirmCodeMap));
    }

    @Transactional
    public static Result getInfos(Long customerId, Integer startIndex)
    {
        if (!isVersionMatch("2")) {
           return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryCompaniesOfInfo");
        query.setParameter("customer", customer);
        List<Company> companies =new ArrayList<>(query.getResultList());
        HashSet h = new HashSet(companies);
        companies.clear();
        companies.addAll(h);

        Collections.sort(companies, new Comparator<Company>() {
            public int compare(Company company1, Company company2) {
                if (company1.id > company2.id) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        List<Company> companiess = null;
        if (companies.size() > startIndex) {
            if (companies.size() - startIndex >= 15) {
                 companiess = companies.subList(startIndex, startIndex + 15);
            } else {
                companiess = companies.subList(startIndex, companies.size());
            }

            ArrayNode jinfos = new ArrayNode(JsonNodeFactory.instance);
            for (Company company: companiess) {
                query = JPA.em().createNamedQuery("queryNotBeenReadInfos");
                query.setParameter("customer", customer);
                query.setParameter("company", company);
                List<Info> infos = query.getResultList();

                query = JPA.em().createNamedQuery("queryBeenReadInfos");
                query.setParameter("customer", customer);
                query.setParameter("company", company);
                List<Info> emptyInfos = query.getResultList();

                Map rst = new HashMap();
                rst.put("company", company);
                rst.put("beenRead", infos.size());
                if (!infos.isEmpty()) {
                    rst.put("latestInfo", infos.get(0));
                } else {
                    rst.put("latestInfo", emptyInfos.get(0));
                }
                jinfos.add(toJson(rst));
            }
            return ok(jinfos);
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getSystemInfos(Long customerId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
           return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("querySystemInfos");
        query.setParameter("customer", customer);
        List<Info> infos = query.getResultList();
        int number = 0;
        if (!infos.isEmpty() && infos.size() > startIndex) {
            List list = new ArrayList<>();
            List list1 = new ArrayList<>();
            List list2 = new ArrayList<>();
            Iterator iterator = infos.iterator();
            while (iterator.hasNext()) {
                Info info = (Info) iterator.next();
                if (info  instanceof TransferInfo) {
                    if (info.beenRead == false) {
                        list.add(info);
                        number += 1;
                    } else {
                        list1.add(info);
                    }
                }
            }
            for (int i = 0; i < list1.size(); i++) {
                list.add(list1.get(i));
            }
            Map hashMap = new HashMap();
            hashMap.put("beenRead", number);
            list2.add(hashMap);
            HashMap hashMap1 = new HashMap();
            if (list.size() - startIndex >= 15) {
                hashMap1.put("systemInfo", list.subList(startIndex, startIndex + 15));
            } else {
                hashMap1.put("systemInfo", list.subList(startIndex, list.size()));
            }
            list2.add(hashMap1);
            return ok(toJson(list2));
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getCompanyInfos(Long customerId, Long companyId, Integer startIndex)
    {
        if (!isVersionMatch("2")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("queryCustomerInfosByCompany");
        query.setParameter("customer", customer);
        query.setParameter("company", company);
        List<Info> infos = query.setFirstResult(startIndex).setMaxResults(15).getResultList();
        if (!infos.isEmpty()) {
            return ok(toJson(infos));
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result setInfoBeenRead(Long customerId, Long infoId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Info info = JPA.em().find(Info.class, infoId);
        boolean beenRead = json.get("beenRead").asBoolean();
        info.beenRead = beenRead;
        JPA.em().persist(info);
        return ok(toJson(info));
    }

    @Transactional
    public static Result setInfoIsTagged(Long customerId, Long infoId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Info info = JPA.em().find(Info.class, infoId);
        boolean isTagged = json.get("isTagged").asBoolean();
        info.isTagged = isTagged;
        JPA.em().persist(info);
        return ok(toJson(info));
    }

    @Transactional
    public static Result removeInfo(Long customerId, Long infoId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Info info = JPA.em().find(Info.class, infoId);
        boolean isRemoved = json.get("isRemoved").asBoolean();
        info.isRemoved = isRemoved;
        JPA.em().persist(info);
        return ok(toJson(info));
    }

    // get free ticket or groupon ticket
    @Transactional
    public static Result requestTicketByInfo(Long customerId, Long infoId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Logger.info("requestTicketByInfo: customerId = " + customerId + ", infoId = " + infoId);
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Info info = JPA.em().find(Info.class, infoId);
        if (info instanceof CompanyTicketInfo) {
            CompanyTicket companyTicket = ((CompanyTicketInfo)info).companyTicket;
            TicketBatch batch = companyTicket.batch;

            if (batch.current >= batch.maxNumber) {
                Logger.info("can not request, batch limit");
                return badRequest();
            } else {
                batch.current += 1;
                batch.leftNumber -= 1;
                JPA.em().persist(batch);
            }

            Query query = JPA.em().createNamedQuery("findCustomerTicketsByCompanyTicket");
            query.setParameter("customer", customer);
            query.setParameter("companyTicket", companyTicket);
            List<Ticket> tickets = query.getResultList();
            if (tickets.size() > 0) {
                Logger.info("ticket already requested");
                return badRequest();
            }

            Calendar calendar = Calendar.getInstance();
            query = JPA.em().createNamedQuery("queryStaticsTicketRequest");
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            query.setParameter("date", calendar.getTime());
            query.setParameter("companyTicket", companyTicket);
            StaticsTicketRequest staticsRequest = null;
            List<StaticsTicketRequest> staticsRequests = query.getResultList();
            if (staticsRequests.isEmpty()) {
                staticsRequest = new StaticsTicketRequest(calendar.getTime(), companyTicket);
            } else {
                staticsRequest = staticsRequests.get(0);
            }

            query = JPA.em().createNamedQuery("queryCompanyCustomer");
            query.setParameter("company", companyTicket.company);
            query.setParameter("customer", customer);
            List<CompanyCustomer> companyCustomers = query.getResultList();
            if (companyCustomers.isEmpty()) {
                JPA.em().persist(new CompanyCustomer(companyTicket.company, customer, staticsRequest));
                staticsRequest.regs += 1L;
            }

            if (batch instanceof DeductionBatch) {
                DeductionTicket ticket = new DeductionTicket(customer, companyTicket, staticsRequest);
                staticsRequest.deductionAmount += ticket.deduction;
                staticsRequest.deductionCount += 1;
                JPA.em().persist(staticsRequest);
                JPA.em().persist(ticket);
                return ok(toJson(ticket));
            } else if (batch instanceof DiscountBatch) {
                DiscountTicket ticket = new DiscountTicket(customer, companyTicket, staticsRequest);
                staticsRequest.discountCount += 1;
                JPA.em().persist(staticsRequest);
                JPA.em().persist(ticket);
                return ok(toJson(ticket));
            } else if (batch instanceof GrouponBatch) {
                GrouponTicket ticket = new GrouponTicket(customer, companyTicket, staticsRequest);
                staticsRequest.grouponCost += ticket.cost;
                staticsRequest.grouponAmount += ticket.deduction;
                staticsRequest.grouponCount += 1;
                JPA.em().persist(staticsRequest);
                JPA.em().persist(ticket);
                return ok(toJson(ticket));
            } else if (batch instanceof CompanyProductBatch){
                CompanyProductTicket ticket = new CompanyProductTicket(customer, companyTicket, staticsRequest);
                JPA.em().persist(staticsRequest);
                JPA.em().persist(ticket);
                return ok(toJson(ticket));
            } else {
                Logger.error("unknow ticket batch!");
                return badRequest();
            }
        } else {
            Logger.error("not a CompanyTicketInfo or ManufacturerTicketInfo");
            return badRequest();
        }
    }

    @Transactional
    public static Result sendInfo(Long customerId, Long infoId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Info info = JPA.em().find(Info.class, infoId);
        String to = json.get("to").asText();
        if (to.equals(customer.name)) {
            Logger.info("can not send to myself");
            return badRequest("can not send to myself");
        }
        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", to);
        List<Customer> tos = query.getResultList();
        Customer toCustomer = null;
        boolean customerExist = false;
        if (tos.isEmpty()) {
            String password = String.format("%06d", new Random().nextInt(999999));
            new Message(customer.name, password, "90982").start();
            toCustomer = new Customer(to, password);
            JPA.em().persist(toCustomer);
        } else {
            customerExist = true;
            toCustomer = tos.get(0);
        }
        if (info instanceof AdvertisementInfo) {
            AdvertisementInfo adsInfo = (AdvertisementInfo)info;
            AdvertisementInfo toInfo = new AdvertisementInfo(toCustomer, info.company, adsInfo.advertisement);
            JPA.em().persist(toInfo);
        } else if (info instanceof CompanyTicketInfo) {
            CompanyTicketInfo companyTicketInfo = (CompanyTicketInfo)info;
            CompanyTicketInfo toInfo = new CompanyTicketInfo(toCustomer, info.company, companyTicketInfo.companyTicket);
            JPA.em().persist(toInfo);
        } else {
            return badRequest("can not share infos except AdvertisementInfo and CompanyTicketInfo");
        }
        HashMap rstMap = new HashMap();
        rstMap.put("customerExist", customerExist);
        rstMap.put("to", to);
        return ok(toJson(rstMap));
    }

    // get free ticket or groupon ticket
    @Transactional
    public static Result requestTicketByPoster(Long customerId, Long posterId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Logger.info("requestTicketByPoster: customerId = " + customerId + ", posterId = " + posterId);
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Poster poster = JPA.em().find(Poster.class, posterId);
        if (poster instanceof CompanyTicket) {
            CompanyTicket companyTicket = (CompanyTicket)poster;
            TicketBatch batch = companyTicket.batch;

            if (batch.current >= batch.maxNumber) {
                Logger.info("can not request, batch limit");
                return badRequest();
            } else {
                batch.current += 1;
                batch.leftNumber -= 1;
                JPA.em().persist(batch);
            }

            Query query = JPA.em().createNamedQuery("findCustomerTicketsByCompanyTicket");
            query.setParameter("customer", customer);
            query.setParameter("companyTicket", companyTicket);
            List<Ticket> tickets = query.getResultList();
            if (tickets.size() > 0) {
                Logger.info("ticket already requested");
                return badRequest();
            }

            Calendar calendar = Calendar.getInstance();
            query = JPA.em().createNamedQuery("queryStaticsTicketRequest");
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            query.setParameter("date", calendar.getTime());
            query.setParameter("companyTicket", companyTicket);
            StaticsTicketRequest staticsRequest = null;
            List<StaticsTicketRequest> staticsRequests = query.getResultList();
            if (staticsRequests.isEmpty()) {
                staticsRequest = new StaticsTicketRequest(calendar.getTime(), companyTicket);
            } else {
                staticsRequest = staticsRequests.get(0);
            }

            query = JPA.em().createNamedQuery("queryCompanyCustomer");
            query.setParameter("company", companyTicket.company);
            query.setParameter("customer", customer);
            List<CompanyCustomer> companyCustomers = query.getResultList();
            if (companyCustomers.isEmpty()) {
                JPA.em().persist(new CompanyCustomer(companyTicket.company, customer, staticsRequest));
                staticsRequest.regs += 1L;
            }

            if (batch instanceof DeductionBatch) {
                DeductionTicket ticket = new DeductionTicket(customer, companyTicket, staticsRequest);
                staticsRequest.deductionAmount += ticket.deduction;
                staticsRequest.deductionCount += 1;
                JPA.em().persist(staticsRequest);
                JPA.em().persist(ticket);
                return ok(toJson(ticket));
            } else if (batch instanceof DiscountBatch) {
                DiscountTicket ticket = new DiscountTicket(customer, companyTicket, staticsRequest);
                staticsRequest.discountCount += 1;
                JPA.em().persist(staticsRequest);
                JPA.em().persist(ticket);
                return ok(toJson(ticket));
            } else if (batch instanceof GrouponBatch) {
                GrouponTicket ticket = new GrouponTicket(customer, companyTicket, staticsRequest);
                staticsRequest.grouponCost += ticket.cost;
                staticsRequest.grouponAmount += ticket.deduction;
                staticsRequest.grouponCount += 1;
                JPA.em().persist(staticsRequest);
                JPA.em().persist(ticket);
                return ok(toJson(ticket));
            } else if (batch instanceof CompanyProductBatch){
                CompanyProductTicket ticket = new CompanyProductTicket(customer, companyTicket, staticsRequest);
                JPA.em().persist(ticket);
                JPA.em().persist(staticsRequest);
                return ok(toJson(ticket));
            } else {
                Logger.error("unknow ticket batch!");
                return badRequest();
            }
        } else {
            Logger.error("not a CompanyTicket");
            return badRequest();
        }
    }

    @Transactional
    public static Result getCompanyCustomers(Long customerId, Integer startIndex) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryCompanyCustomerOfCustomer");
        query.setParameter("customer", customer);
        List<CompanyCustomer> companyCustomerss = query.setFirstResult(startIndex).setMaxResults(15).getResultList();
        if (!companyCustomerss.isEmpty()) {
            ArrayNode companyCustomers = new ArrayNode(JsonNodeFactory.instance);
            Iterator iterator = companyCustomerss.iterator();
            while (iterator.hasNext()) {
                CompanyCustomer companyCustomer = (CompanyCustomer) iterator.next();
                JsonNode j = toJson(companyCustomer);
                ObjectNode o = (ObjectNode) j;

//                query = JPA.em().createNamedQuery("findCustomerAllTickets");
//                query.setParameter("customerId", customerId);
//                List<Ticket> tickets = query.getResultList();
//                o.put("ticketNumber", tickets.size());
                companyCustomers.add(o);
            }
            return ok(companyCustomers);
        } else {
            return notFound();
        }

    }

    @Transactional
    public static Result getTicketNumbers(Long customerId, Long companyId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("findCustomerValidTicketsIncompany");
        query.setParameter("customerId", customerId);
        query.setParameter("company", company);
        List<Ticket> tickets = query.getResultList();
        Logger.info(String.valueOf(tickets.size()));
        Logger.info(String.valueOf(toJson(tickets)));
        Map rst = new HashMap();
        rst.put("ticketNumber", tickets.size());
        return ok(toJson(rst));

    }

    @Transactional
    public static Result isCompanyCustomer(Long customerId, Long companyId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if(customer == null){
            return badRequest("invalid customerId");
        }
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("queryCompanyCustomerByName");
        query.setParameter("customerName", customer.name);
        query.setParameter("company", company);
        List<CompanyCustomer> companyCustomers = query.getResultList();
        if (!companyCustomers.isEmpty()) {
            return ok();
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getTicketsOfCompanyCustomer(Long companyCustomerId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!isTokenValid(companyCustomer.customer.token, companyCustomer.customer.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findCustomerValidTicketsIssuedByCompany");
        query.setParameter("customerId", companyCustomer.customer.id);
        query.setParameter("company", companyCustomer.company);
        List<Ticket> tickets = query.setFirstResult(startIndex).setMaxResults(15).getResultList();
        if (!tickets.isEmpty()) {
            ArrayNode jtickets = new ArrayNode(JsonNodeFactory.instance);
            Iterator iterator = tickets.iterator();
            while (iterator.hasNext()) {
                Ticket ticket = (Ticket)iterator.next();
                ObjectNode jticket = (ObjectNode)toJson(ticket);
                jticket.put("batchId", ticket.batch.id);
                jticket.put("batchName", ticket.batch.name);
                jticket.put("batchNote", ticket.batch.note);
                jtickets.add(jticket);
            }
            return ok(jtickets);
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getConsumeAndRechargeInfosOfCompanyCustomer(Long companyCustomerId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!isTokenValid(companyCustomer.customer.token, companyCustomer.customer.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryConsumeAndRechargeInfo");
        query.setParameter("companyCustomer", companyCustomer);
        List<Info> consumeInfos = query.getResultList();
        query = JPA.em().createNamedQuery("queryRechargeInfo");
        query.setParameter("companyCustomer", companyCustomer);
        List<Info> rechargeInfos = query.getResultList();
        consumeInfos.addAll(rechargeInfos);
        Collections.sort(consumeInfos, new Comparator<Info>() {
            public int compare(Info info1, Info info2) {
                if (info1.infoWhen.before(info2.infoWhen)) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        if (consumeInfos.size() > startIndex) {
            if (consumeInfos.size() - startIndex >= 15) {
                return ok(toJson(consumeInfos.subList(startIndex, startIndex + 15)));
            } else {
                return ok(toJson(consumeInfos.subList(startIndex, consumeInfos.size())));
            }
        } else {
            return notFound();
        }

    }

    @Transactional
    public static Result getConsumeAndIntegralInfosOfCompanyCustomer(Long companyCustomerId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!isTokenValid(companyCustomer.customer.token, companyCustomer.customer.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryConsumeInfo");
        query.setParameter("companyCustomer", companyCustomer);
        List<Info> consumeInfoss = query.getResultList();
        List<Info> consumeInfos = new ArrayList<>();
        Iterator iterator = consumeInfoss.iterator();
        while (iterator.hasNext()) {
            ConsumeInfo consumeInfo = (ConsumeInfo) iterator.next();
            if (consumeInfo.consume.integralIncrease > 0) {
                consumeInfos.add(consumeInfo);
            }
        }
        query = JPA.em().createNamedQuery("queryIntegralInfo");
        query.setParameter("companyCustomer", companyCustomer);
        List<Info> integralInfos = query.getResultList();
        consumeInfos.addAll(integralInfos);
        Collections.sort(consumeInfos, new Comparator<Info>() {
            public int compare(Info info1, Info info2) {
                if (info1.infoWhen.before(info2.infoWhen)) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        if (consumeInfos.size() > startIndex) {
            if (consumeInfos.size() - startIndex >= 15) {
                return ok(toJson(consumeInfos.subList(startIndex, startIndex + 15)));
            } else {
                return ok(toJson(consumeInfos.subList(startIndex, consumeInfos.size())));
            }
        } else {
            return notFound();
        }

    }

    @Transactional
    public static Result getFavorates(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        return ok(toJson(customer.favorates));
    }

    @Transactional
    public static Result getFavorateCompanies(Long customerId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if(customer == null){
            return badRequest("invalid customerId");
        }
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("queryFavorateCompanyByCustomerAndCompany");
        query.setParameter("customer", customer);
        query.setParameter("company", company);
        List<FavorateCompany> favorateCompanies = query.getResultList();
        return ok(toJson(favorateCompanies));
    }

    @Transactional
    public static Result postFavorateCompany(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Long companyId = json.get("companyId").asLong();
        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("queryFavorateCompanyByCustomerAndCompany");
        query.setParameter("customer", customer);
        query.setParameter("company", company);
        List<FavorateCompany> favorateCompanies = query.getResultList();
        if (favorateCompanies.size() == 0) {
            FavorateCompany favorateCompany = new FavorateCompany(customer, company);
            JPA.em().persist(favorateCompany);
            return ok(toJson(favorateCompany));
        } else {
            return badRequest(); 
        }
    }

    @Transactional
    public static Result getFavorateProducts(Long customerId, Long companyProductId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        Query query = JPA.em().createNamedQuery("queryFavorateProductByCustomerAndCompanyProduct");
        query.setParameter("customer", customer);
        query.setParameter("companyProduct", companyProduct);
        List<FavorateProduct> favorateProducts = query.getResultList();
        return ok(toJson(favorateProducts));
    }

    @Transactional
    public static Result postFavorateProduct(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Long companyProductId = json.get("companyProductId").asLong();
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        Query query = JPA.em().createNamedQuery("queryFavorateProductByCustomerAndCompanyProduct");
        query.setParameter("customer", customer);
        query.setParameter("companyProduct", companyProduct);
        List<FavorateProduct> favorateProducts = query.getResultList();
        if (favorateProducts.size() == 0) {
            FavorateProduct favorateProduct = new FavorateProduct(customer, companyProduct);
            JPA.em().persist(favorateProduct);
            return ok(toJson(favorateProduct));
        } else {
            return badRequest();
        }
    }

    @Transactional
    public static Result getFavoratePosters(Long customerId, Long posterId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Poster poster = JPA.em().find(Poster.class, posterId);
        Query query = JPA.em().createNamedQuery("queryFavoratePosterByCustomerAndPoster");
        query.setParameter("customer", customer);
        query.setParameter("poster", poster);
        List<FavoratePoster> favoratePosters = query.getResultList();
        return ok(toJson(favoratePosters));
    }

    @Transactional
    public static Result postFavoratePoster(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Long posterId = json.get("posterId").asLong();
        Poster poster = JPA.em().find(Poster.class, posterId);
        Query query = JPA.em().createNamedQuery("queryFavoratePosterByCustomerAndPoster");
        query.setParameter("customer", customer);
        query.setParameter("poster", poster);
        List<FavoratePoster> favoratePosters = query.getResultList();
        if (favoratePosters.size() == 0) {
            FavoratePoster favoratePoster = new FavoratePoster(customer, poster);
            JPA.em().persist(favoratePoster);
            return ok(toJson(favoratePoster));
        } else {
            return badRequest();
        }
    }

    @Transactional
    public static Result deleteFavorate(Long customerId, Long favorateId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Favorate favorate = JPA.em().find(Favorate.class, favorateId);
        if (favorate == null) return notFound();
        else {
            JPA.em().remove(favorate);
            return ok();
        }
    }

    @Transactional
    public static Result getBookingsByCustomer(Long customerId, Integer status, Long after)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        //once newbooking or acceptdbooking has expired, its status is changed to cancel.
        Query query = JPA.em().createNamedQuery("queryNewOrAcceptedBooking");
        List<Booking>  newOrAcceptedBookings = query.getResultList();
        Iterator iterator = newOrAcceptedBookings.iterator();
        while (iterator.hasNext()) {
            Booking newOrAcceptedBooking = (Booking) iterator.next();
            Calendar calendar = Calendar.getInstance();
            long diff = calendar.getTime().getTime() - newOrAcceptedBooking.bookingTime.getTime();
            long days = diff / (1000 * 60 * 60 * 24);
            if (days >= 1) {
                newOrAcceptedBooking.status = 3;
                JPA.em().persist(newOrAcceptedBooking);
            }
        }

        query = JPA.em().createNamedQuery("queryBookingForCustomer");
        query.setParameter("customer", customer);
        query.setParameter("status", status);
        query.setParameter("after", new Date(after));
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result getBookingsByCompanyCustomer(Long companyCustomerId, Integer status, Long after)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!isTokenValid(companyCustomer.customer.token, companyCustomer.customer.expiredWhen)) {
            return unauthorized();
        }

        //once newbooking or acceptdbooking has expired, its status is changed to cancel.
        Query query = JPA.em().createNamedQuery("queryNewOrAcceptedBooking");
        List<Booking>  newOrAcceptedBookings = query.getResultList();
        Iterator iterator = newOrAcceptedBookings.iterator();
        while (iterator.hasNext()) {
            Booking newOrAcceptedBooking = (Booking) iterator.next();
            Calendar calendar = Calendar.getInstance();
            long diff = calendar.getTime().getTime() - newOrAcceptedBooking.bookingTime.getTime();
            long days = diff / (1000 * 60 * 60 * 24);
            if (days >= 1) {
                newOrAcceptedBooking.status = 3;
                JPA.em().persist(newOrAcceptedBooking);
            }
        }

        query = JPA.em().createNamedQuery("queryBookingForCompanyCustomer");
        query.setParameter("companyCustomer", companyCustomer);
        query.setParameter("status", status);
        query.setParameter("after", new Date(after));
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result postBooking(Long customerId)
    {
        if (!isVersionMatch("2")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Long companyId = json.get("companyId").asLong();
        String customerNote = json.get("customerNote").asText();
        int type = json.get("type").asInt();
        Company company = JPA.em().find(Company.class, companyId);

        // if no companyCustomer, then create it
        Query query = JPA.em().createNamedQuery("queryCompanyCustomer");
        query.setParameter("company", company);
        query.setParameter("customer", customer);
        query.setMaxResults(1);
        List<CompanyCustomer> companyCustomers = query.getResultList();
        CompanyCustomer companyCustomer = null;
        if (companyCustomers.isEmpty()) {
            companyCustomer = new CompanyCustomer(company, customer);
        } else {
            companyCustomer = companyCustomers.get(0);
        }

        Date bookingTime = new Date(json.get("bookingTime").asLong());
        Booking booking = new Booking(companyCustomer, bookingTime);
        booking.customer_note = customerNote;
        JsonNode jbookingProducts = json.get("bookingProducts");
        Iterator iterator = jbookingProducts.iterator();
        while (iterator.hasNext()) {
            JsonNode jbookingProduct = (JsonNode)iterator.next();
            CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, jbookingProduct.get("companyProductId").asLong());
            if (!companyProduct.company.id.equals(company.id)) {
                return badRequest("product not belong the company");
            }
            int quantity = jbookingProduct.get("quantity").asInt();
            booking.addBookingProduct(companyProduct, quantity);
        }

        JPA.em().persist(booking);
        if(type == 1){
            long bookingAmount = 0;
            for (BookingProduct cp: booking.bookingProducts) {
                bookingAmount += cp.quantity *cp.companyProduct.currentPrice.price.longValue();
            }
            long netPaid = bookingAmount * companyCustomer.discount / 100;
            if (netPaid > companyCustomer.balance.longValue()) {
                return forbidden("balance not enough");
            }
            booking.companyCustomer.balance-= netPaid;
            booking.payStatus = 1;
        }
        return ok(toJson(booking));
    }


    @Transactional
    public static Result cancelBooking(Long customerId, Long bookingId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }
        Booking booking = JPA.em().find(Booking.class, bookingId);
        if (booking == null) {
            return notFound();
        } else {
            booking.status = 3;
            if(booking.payStatus == 1){
              long refund = 0;
                for (BookingProduct bookingProduct: booking.bookingProducts) {
                    refund += bookingProduct.companyProduct.currentPrice.price * bookingProduct.quantity;
                }
                if(refund > 0){
                    CompanyCustomer companycustomer = booking.companyCustomer;
                    companycustomer.balance += refund;
                    JPA.em().persist(companycustomer);
                }
            }
            JPA.em().persist(booking);
        }
        return ok(toJson(booking));
    }

    @Transactional
    public static Result getDistributors(Long customerId, Long companyProductId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);

        if (companyProduct.source != null) {
            List<Company> all = companyProduct.source.company.exclusiveCompanies;
            List<Company> need = new ArrayList<Company>();
            Iterator<Company> iterator = all.iterator();
            while (iterator.hasNext()){
                Company company = (Company)iterator.next();
                Query  query = JPA.em().createNamedQuery("findSourceProductsOfCompany");
                List<CompanyProduct> liscenseProducts = query.setParameter("company", company).getResultList();
                if (!liscenseProducts.isEmpty() && liscenseProducts.contains(companyProduct.source)) {
                    ((ArrayList)need).add(company);
                }
            }
            if(!need.isEmpty()){
//                return ok(toJson(companyProduct.source.company.exclusiveCompanies));
                return  ok(toJson(need));
            }else {
                return notFound();
            }

        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getCompanyProductConsumeServices(Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findCustomerCompanyProductConsume");
        query.setParameter("customer", customer);
        List<CompanyProductConsume> companyProductConsumes = query.getResultList();

        if (!companyProductConsumes.isEmpty()) {

            ArrayNode jcompanyProductConsumes = new ArrayNode(JsonNodeFactory.instance);
            Iterator iterator = companyProductConsumes.iterator();
            while (iterator.hasNext()) {
                CompanyProductConsume companyProductConsume = (CompanyProductConsume) iterator.next();
                ArrayNode jservices = new ArrayNode(JsonNodeFactory.instance);
                ObjectNode jcompanyProductConsume = (ObjectNode) toJson(companyProductConsume);
                List<CompanyProductConsumeService> companyProductConsumeServices = companyProductConsume.companyProductConsumeServices;
                Iterator iterator1 = companyProductConsumeServices.iterator();
                while (iterator1.hasNext()) {

                    CompanyProductConsumeService companyProductConsumeService = (CompanyProductConsumeService) iterator1.next();
                    ObjectNode jservice = (ObjectNode) toJson(companyProductConsumeService);
                    boolean isExpired = false;
                    if (companyProductConsumeService.serviceType == 1 && companyProductConsumeService.validDays != -1) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(companyProductConsumeService.companyProductConsume.consumeWhen);
                        calendar.add(Calendar.DAY_OF_MONTH, companyProductConsumeService.validDays + 1);
                        calendar.set(Calendar.HOUR_OF_DAY, 0);
                        calendar.set(Calendar.MINUTE, 0);
                        calendar.set(Calendar.SECOND, 0);
                        calendar.set(Calendar.MILLISECOND, 0);
                        if (calendar.before(Calendar.getInstance())) {
                            isExpired = true;
                        }
                    }
                    jservice.put("isExpired", isExpired);
                    jservices.add(jservice);
                    jcompanyProductConsume.put("companyProductConsumeServices", jservices);
                }
                jcompanyProductConsumes.add(jcompanyProductConsume);
            }
            Logger.info("+++++||||++"+jcompanyProductConsumes);
            return ok(jcompanyProductConsumes);
        } else {
            return  notFound();
        }
    }

    private static boolean isTokenValid(String token, Date expiredWhen) {
        if (token == null) {
            return false;
        }

        Calendar calendarExpired = Calendar.getInstance();
        calendarExpired.setTime(expiredWhen);
        if (calendarExpired.before(Calendar.getInstance())) {
            return false;
        }

        String[] authTokenHeaderValues = request().headers().get("X-AUTH-TOKEN");
        if ((authTokenHeaderValues != null) && (authTokenHeaderValues.length == 1) && (authTokenHeaderValues[0] != null)) {
            if (token.equals(authTokenHeaderValues[0])) {
                return true;
            }
        }
        return false;
    }


    @Transactional
    public static Result getLogoOfCompany(Long companyId)
    {
        Company company = JPA.em().find(Company.class, companyId);
        if (company == null || company.logo == null) {
            return notFound();
        }
        return ok(company.logo).as(company.contentType);
    }

    @Transactional
    public static Result getAntiFakePicture(Long customerId, Long pictureId) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (!isTokenValid(customer.token, customer.expiredWhen)) {
            return unauthorized();
        }

        AntiFakePicture antiFakePicture = JPA.em().find(AntiFakePicture.class, pictureId);
        if (antiFakePicture != null) {
            return ok(antiFakePicture.antiFakePicture).as(antiFakePicture.contentType);
        } else {
            return notFound();
        }
    }
    @Transactional
    public static Result getAntiFakePictureNoVersion(Long customerId, Long pictureId) throws Exception
    {

        AntiFakePicture antiFakePicture = JPA.em().find(AntiFakePicture.class, pictureId);
        if (antiFakePicture != null) {
            return ok(antiFakePicture.antiFakePicture).as(antiFakePicture.contentType);
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getLogoOfProduct(Long productId)
    {
        Product product = JPA.em().find(Product.class, productId);
        if (product == null || product.logo == null) {
            return notFound();
        }
        return ok(product.logo).as(product.contentType);
    }

    @Transactional
    public static Result getCompaniesByName(String companyName)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("findCompanyByKey");
        query.setParameter("key", "%" + companyName + "%");
        List<Company> companies = query.getResultList();
        return ok(toJson(companies));
    }

    @Transactional
    public static Result getCompaniesByLocation(Double longitude, Double latitude, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("findCompanyByLocation");
        query.setParameter("longitude", longitude);
        query.setParameter("latitude", latitude);
        List<Company> companies = query.setFirstResult(startIndex).setMaxResults(15).getResultList();
        if (!companies.isEmpty()) {
            return ok(toJson(companies));
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getBroadcastsByLocation(Double longitude, Double latitude, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("queryBroadcastByLocation");
        query.setParameter("longitude", longitude);
        query.setParameter("latitude", latitude);
        List<Poster> posters = query.setFirstResult(startIndex).setMaxResults(15).getResultList();
        if (!posters.isEmpty()) {
            return ok(toJson(posters));
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getCompanyBroadcast(Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("queryBroadcastOfCompany");
        query.setParameter("company", company);
        List<Poster> posters = query.getResultList();
        if (posters.isEmpty()) {
            return notFound();
        } else {
            return ok(toJson(posters.get(0)));
        }
    }

    @Transactional
    public static Result queryCompanyProducts(String productName)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("findCompanyProductsByProductName");
        query.setParameter("productName", "%" + productName + "%");
        List<CompanyProduct> companyProducts = query.getResultList();
        return ok(toJson(companyProducts));
    }

    @Transactional
    public static Result getCompanyPosters(Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("findLatestPosterOfCompany");
        query.setParameter("companyId", companyId);
        List<Poster> posters = query.getResultList();
        return ok(toJson(posters));
    }

    @Transactional
    public static Result getLatestPosterOfCompany(Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("findLatestPosterOfCompany");
        query.setParameter("companyId", companyId);
        query.setMaxResults(1);
        List<Poster> posters = query.getResultList();
        if (posters.isEmpty()) {
            return notFound();
        } else {
            return ok(toJson(posters.get(0)));
        }
    }

    @Transactional
    public static Result getLatestTwoPosterOfCompany(Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("findLatestPosterOfCompany");
        query.setParameter("companyId", companyId);
        query.setMaxResults(2);
        List<Poster> posters = query.getResultList();
        return ok(toJson(posters));
    }

    @Transactional
    public static Result getCompanyProducts(Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("findPublishedProductsOfCompany");
        query.setParameter("company", company);
        List<CompanyProduct> companyProducts = query.getResultList();
        return ok(toJson(companyProducts));
    }

    @Transactional
    public static Result getTwoCompanyProducts(Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("findPublishedProductsOfCompany");
        query.setParameter("company", company);
        query.setMaxResults(2);
        List<CompanyProduct> companyProducts = query.getResultList();
        return ok(toJson(companyProducts));
    }
    private static boolean isVersionMatch(String apiVersion) {
        String[] platformVersions = request().headers().get("X-PLATFORM-VERSION");
        String[] apiVersions = request().headers().get("X-API-VERSION");
        if ((platformVersions != null) && (platformVersions.length == 1) && (platformVersions[0] != null)
                && (apiVersions != null) && (apiVersions.length == 1) && (apiVersions[0] != null)) {
            if (PLATFORM_VERSION.equals(platformVersions[0]) && apiVersion.equals(apiVersions[0])) {
                return true;
            }
        }
        return false;
    }
}



