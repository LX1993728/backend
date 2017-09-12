package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.mindrot.jbcrypt.BCrypt;
import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.Transactional;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Result;
import utils.*;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static play.libs.Json.toJson;

public class ManagerApp extends Controller {
    private static final String PLATFORM_VERSION = "4.0";    // major.minor, same as build.sbt
    private static final String ALIPAY_GATEWAY_NEW = "https://mapi.alipay.com/gateway.do?";

    private static final int MAX_LOGO_LENGTH = 100 * 1024;
    private static final int MAX_ANTIFAKEPICTURE_LENGTH = 600 * 1024;
//    private static final int BROADCAST_INTERVAL = 24 * 3600;
//    private static final int NOTIFICATION_INTERVAL = 24 * 3600;
    private static final int BROADCAST_INTERVAL = 0;
    private static final int NOTIFICATION_INTERVAL = 0;
//====================================================================================================================
    @Transactional
    public static Result testTransfer() throws Exception{  // 测试支付宝商家转账
        Map<String,String> params = new HashMap<>();
        params.put("service",AlipayConfig.serviceForTransfer);
        params.put("partner",AlipayConfig.partner);
        params.put("_input_charset",AlipayConfig.input_charset);
        params.put("sign_type",AlipayConfig.sign_type);
        params.put("account_name","北京原宝科技发展有限公司");
        params.put("detail_data", URLEncoder.encode("123456^13693151725^1.00^调试转账"));
        params.put("batch_no",UtilDate.getOrderNum());
        params.put("batch_num","1");
        params.put("batch_fee","1.01");
        params.put("email","liuzheng@elable.cn");
        params.put("pay_date",UtilDate.getDate());
        String result = AlipaySubmit.aliTransferMoney(params);
       return ok(result);
    }

    @Transactional
    public static Result  testWXTransfer() throws Exception{
        Map<String,Object> params = new HashMap<>();
        params.put("mch_appid",WXPayConfig.appID);
        params.put("mchid",WXPayConfig.mchID);
        params.put("nonce_str",WXPayUtil.getRandomStringByLength(32));
        params.put("partner_trade_no","1111256454");
        params.put("openid","optovxPJtRiTG_5Tgk3bAtoKTszM");
        params.put("check_name","NO_CHECK");
        params.put("amount","100");
        params.put("desc","微信转账");
        params.put("spbill_create_ip","192.168.1.113");
        String sign0 = WXPayUtil.getSign(params,0);
        params.put("sign",sign0);
        String requestXML =WXPayXMLParser.getXMLStrFromMap(params);
        Logger.info("requestXML= "+requestXML);
        if(requestXML == null){
            return badRequest("requestxmlStr error");
        }
        String result = WXPayCore.postTransfer(requestXML);
        Logger.info("result xml= "+result);
        return ok(result);
    }


    @Transactional
    public static  Result getQueryOrder(String tradeNo,Long type) throws Exception{
        Map<String,Object>  map = WXPayQueryOrder.queryOrderOfCustomer(tradeNo,type.intValue());
        if(map != null){
            return ok(toJson(map));
        }else {
            return badRequest("query order failure");
        }

    }

//====================================================================================================================

    @Transactional
    public static Result getBizCode(long managerId) throws Exception{
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (manager.level != 0) {
            return badRequest("only support super manager");
        }
        JsonNode json = request().body().asJson();
        String totalFee = json.get("charge").asText();
        CompanyRecharge recharge = new CompanyWXRecharge(manager.company, totalFee);
        JPA.em().persist(recharge);
        CompanyRechargeLog record = new CompanyRechargeLog(manager, recharge);
        JPA.em().persist(record);


        String out_trade_no = recharge.id.toString();; // 订单号
        long total_free = (long)(Double.parseDouble(totalFee)*100);
        String order_price = String.valueOf(total_free); //价格：价格的单位是分
        String body = "原宝科技有限公司"; // 商品名称
        String attach = "COMPANY"; //附加数据
        String nonce_str0 = WXPayUtil.getRandomStringByLength(32);

        // 获取发起电脑的IP
        String ip = WXPayConfig.ip;
        String trade_type = "NATIVE";
        SortedMap<String,Object> unfiedParams = new TreeMap<>();
        unfiedParams.put("appid",WXPayConfig.appID); //必须
        unfiedParams.put("mch_id",WXPayConfig.mchID); //必须
        unfiedParams.put("out_trade_no",out_trade_no);//必须
        unfiedParams.put("product_id","1");
        unfiedParams.put("body",body); //必须
        unfiedParams.put("attach",attach);
        unfiedParams.put("total_fee",order_price); //必须
        unfiedParams.put("nonce_str",nonce_str0);  //必须
        unfiedParams.put("spbill_create_ip",ip);  //必须
        unfiedParams.put("trade_type",trade_type); //必须


        unfiedParams.put("notify_url",WXPayConfig.NOTIFY_URL); //异步通知url
        String sign0 = WXPayUtil.getSign(unfiedParams,0);
        unfiedParams.put("sign",sign0);

        String requestXML = WXPayXMLParser.getXMLStrFromMap(unfiedParams);

        // 统一下单接口
        Logger.info("requestXML= "+requestXML);
        String rXml = WXPayCore.postOrder(requestXML);
        Map<String,Object> reParams = WXPayXMLParser.getMapFromXML(rXml);
        if(reParams == null){
            return badRequest("under order error");
        }
        Logger.info("reParams= "+reParams);
        // 验证签名
        if(WXPayUtil.checkIsSignValidFromResponseString(rXml,0)){
            // 统一下单返回的参数
            Logger.info("管理员编号:"+managerId+"---充值金额："+totalFee+"---trade_no= "+out_trade_no);
            String code_url = (String) reParams.get("code_url");
            return ok(code_url);
        }else {
            return badRequest("Native下单验证签名失败");
        }
    }




    @Transactional
    public static Result getWXPayNotifyInfo() throws Exception{
        String xmlStr = null;
        String resXml="";//反馈给微信服务器
        Pattern p = Pattern.compile(".*(<xml>.*</xml>).*");
        Matcher m = p.matcher(request().body().toString().replaceAll("\\s",""));
        if(m.find()){
            Logger.info(m.group(1));
            xmlStr = m.group(1);
        }else {
            return notFound();
        }
        // 验证签名
        if(WXPayUtil.checkIsSignValidFromResponseString(xmlStr,0)){
         if("SUCCESS".equals(WXPayXMLParser.getMapFromXML(xmlStr).get("result_code"))){
             Logger.info("支付成功"+"----"+"交易单号:"+WXPayXMLParser.getMapFromXML(xmlStr).toString());
             Map<String,Object> mapSuccess = WXPayXMLParser.getMapFromXML(xmlStr);
             CompanyRecharge companyRecharge = JPA.em().find(CompanyWXRecharge.class, Long.valueOf((String) mapSuccess.get("out_trade_no")));
             if (companyRecharge.status != 1 ) {
                 companyRecharge.tradeNo = (String) mapSuccess.get("out_trade_no");
                 companyRecharge.tradeStatus = (String)mapSuccess.get("return_code");
                 companyRecharge.status = 1;
                 JPA.em().persist(companyRecharge);
                 Company company = companyRecharge.company;
                 Double rechargeAmount = Double.valueOf((String) mapSuccess.get("total_fee"));
//                 company.balance += rechargeAmount.longValue();
                 Logger.info("balance=" +company.balance+"----add "+rechargeAmount.longValue());
                 JPA.em().persist(company);
                 String note = "微信openId为："+mapSuccess.get("openid")+"的用户为商户"+company.name+"充值"+rechargeAmount+"分";
                 CompanyAccount companyAccount = new CompanyAccount(company, rechargeAmount.longValue(), note);
                 JPA.em().persist(companyAccount);
             }
             // 通知微信.异步确认成功.必写.不然会一直通知后台.八次之后就认为交易失败了.
             resXml = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"
                     + "<return_msg><![CDATA[OK]]></return_msg>" + "</xml> ";
         }else {
             Logger.info("支付失败,错误信息：" + WXPayXMLParser.getMapFromXML(xmlStr).get("err_code"));
             resXml = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
                     + "<return_msg><![CDATA[签名验证错误]]></return_msg>" + "</xml> ";
         }
        }else {
            Logger.info("签名验证错误");
            resXml = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
                    + "<return_msg><![CDATA[签名验证错误]]></return_msg>" + "</xml> ";
        }
        return ok(resXml);
    }

    @Transactional
    public  static Result registerCompanyCustomers(Long managerId) throws Exception{

        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();
        JsonNode namesNode = json.get("names");
        if(namesNode==null){
            return badRequest("parameter can not be null");
        }
        Company company = manager.company;
        if(company == null){
            return badRequest("invalid companyId");
        }
        Iterator<JsonNode> namesIterator = namesNode.iterator();
        while (namesIterator.hasNext()){
            String customerName = namesIterator.next().asText();
            Query query = JPA.em().createNamedQuery("findCustomersByName");
            query.setParameter("customerName", customerName);
            List<Customer> customers = query.getResultList();
            Customer customer = null;
            if (!customers.isEmpty()) {
                customer = customers.get(0);
            } else {
                String password = String.format("%06d", new Random().nextInt(999999));
//                new Message(customerName, password, "90982").start();
                customer = new Customer(customerName, password);
                JPA.em().persist(customer);
            }
           Query queryComCus = JPA.em().createNamedQuery("queryCompanyCustomer");
            queryComCus.setParameter("company",company);
            queryComCus.setParameter("customer",customer);
            List<CompanyCustomer> companyCustomers = queryComCus.getResultList();
            if(companyCustomers.isEmpty()){
                JPA.em().persist(new CompanyCustomer(company,customer));
            }
        }
        return ok();
    }
  //==========================================================================
    @Transactional
    public static Result getCompany(Long cId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Query query = JPA.em().createNamedQuery("findCompanyByCId");
        query.setParameter("cId", cId);
        List<Company> companies = query.getResultList();
        if(companies.isEmpty()){
            return notFound();
        }
        Long companyId = companies.get(0).id;
        Company company = JPA.em().find(Company.class, companyId);
        if (company != null) {
            return ok(toJson(company));
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getManager(Long cId, Long mId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }
        Query query = JPA.em().createNamedQuery("findCompanyByCId");
        query.setParameter("cId", cId);
        List<Company> companies = query.getResultList();
        if(companies.isEmpty()){
            return notFound();
        }
        Long companyId = companies.get(0).id;
        query = JPA.em().createNamedQuery("findManagerByMId");
        query.setParameter("mId", mId);
        List<Manager> managers = query.getResultList();
        if(managers.isEmpty()){
            return notFound();
        }
        Long managerId = managers.get(0).id;
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (manager.company.id == companyId.longValue() && manager.isActive) {
            return ok(toJson(manager));
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result managerLogin() throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Long cId = json.findPath("cId").asLong();
        Long mId = json.findPath("mId").asLong();
        String password = json.findPath("password").asText();
        Query query = JPA.em().createNamedQuery("findCompanyByCId");
        query.setParameter("cId", cId);
        Long companyId = null;
        List<Company> companies = query.getResultList();
        if (!companies.isEmpty()) {
            companyId = companies.get(0).id;
        }

        query = JPA.em().createNamedQuery("findManagerByMId");
        query.setParameter("mId", mId);
        Long managerId = null;
        List<Manager> managers = query.getResultList();
        if (!managers.isEmpty()) {
            managerId = managers.get(0).id;
        }
        Logger.info("===managerID= "+managerId);

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (manager.company.id.longValue() == companyId.longValue() && manager.isActive && BCrypt.checkpw(password, manager.password)) {
            Calendar calendar = Calendar.getInstance();
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update((UUID.randomUUID().toString().toUpperCase() + manager.name + calendar.getTimeInMillis()).getBytes());
            manager.token = Base64.encodeBase64String(messageDigest.digest());
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            manager.expiredWhen = calendar.getTime();
            EntityManager em = JPA.em();
            em.persist(manager);

            JsonNode jmanager = toJson(manager);
            ObjectNode o = (ObjectNode)jmanager;
            o.put("token", manager.token);
            return ok(o);
        }

        return unauthorized("unauthorized");
    }

    @Transactional
    public static Result managerLogout(Long id) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, id);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        manager.expiredWhen = calendar.getTime();
        EntityManager em = JPA.em();
        em.persist(manager);
        return ok();
    }

    @Transactional
    public static Result resetPasswordForManager(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode json = request().body().asJson();
        String password = json.get("password").asText();
        manager.resetPassword(password);
        Calendar calendar = Calendar.getInstance();
        manager.expiredWhen = calendar.getTime();
        EntityManager em = JPA.em();
        em.persist(manager);
        return ok(toJson(manager));
    }

    @Transactional
    public static Result getBalance(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Map rst = new HashMap<String, Integer>();
        rst.put("balance", manager.company.balance);
        return ok(toJson(rst));
    }

    //ke.zhang 2016-05-05 for password retake
    @Transactional
    public static Result getLostPassword(Long managerId, String email) throws EmailException {
        Manager manager = JPA.em().find(Manager.class, managerId);
        Util util = new Util();
        if(manager != null && email.equals(manager.company.email)){
            String password = util.GenerateRandomNumber(6);
            Long companyId = manager.company.id;
            manager.resetPassword(password);
            SimpleEmail postEmail = new SimpleEmail();
            postEmail.setHostName("domino.elable.cn");
            postEmail.setSmtpPort(30);
            postEmail.setAuthentication("support", "yuanbaoSp100085");
            postEmail.addTo(email, "尊敬的用户");
            postEmail.setFrom("support@elable.cn", "原宝科技");
            postEmail.setSubject("You new password");
            postEmail.setMsg("你已成功找回密码!公司ID：" + companyId + "管理员ID：" + managerId + "密码：" + password + "\n" +"https://company.elable.cn");
            postEmail.send();
            return ok(toJson(manager));
        }else{
            return notFound();
        }
    }
    @Transactional
    public static Result updateInfo(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();
        Company company = manager.company;
        company.name = json.get("name").asText();
        company.address = json.get("address").asText();
        company.email = json.get("email").asText();
        company.telephone = json.get("telephone").asText();
        company.description = json.get("description").asText();
        company.latitude = Double.parseDouble(json.get("latitude").asText());
        company.longitude = Double.parseDouble(json.get("longitude").asText());
        JPA.em().persist(company);
        return ok();
    }

    @Transactional
    @BodyParser.Of(value = BodyParser.AnyContent.class, maxLength = MAX_LOGO_LENGTH)
    public static Result postLogoOfCompany(Long managerId) throws Exception
    {

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        MultipartFormData body = request().body().asMultipartFormData();
        List<MultipartFormData.FilePart> files = body.getFiles();
        
        MultipartFormData.FilePart logoFilePart = body.getFile("logo");
        if (logoFilePart != null) {
            String logoFileName = logoFilePart.getFilename();
            String logoContentType = logoFilePart.getContentType();
            File logoFile = logoFilePart.getFile();
            manager.company.contentType = logoContentType;
            FileInputStream logoStream = new FileInputStream(logoFile);
            int totalLen = (int)logoFile.length();
            byte[] logo = new byte[totalLen];
            int offset = 0;
            while (offset < totalLen) {
                int readLen = logoStream.read(logo, offset, totalLen - offset);
                offset += readLen;
            }
            manager.company.logo = logo;
            JPA.em().persist(manager.company);
            return ok();
        } else {
            return badRequest("upload failed!");
        }
    }

    @Transactional
    @BodyParser.Of(value = BodyParser.AnyContent.class, maxLength = MAX_ANTIFAKEPICTURE_LENGTH)
    public static Result postAntiFakePicture(Long managerId) throws Exception
    {

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        MultipartFormData body = request().body().asMultipartFormData();
        List<MultipartFormData.FilePart> files = body.getFiles();

        Logger.info(String.valueOf("-----"));
        AntiFakePicture antiFakePicture = new AntiFakePicture();
        MultipartFormData.FilePart logoFilePart = body.getFile("logo");
        if (logoFilePart != null) {
            String logoFileName = logoFilePart.getFilename();
            String logoContentType = logoFilePart.getContentType();
            File logoFile = logoFilePart.getFile();
            antiFakePicture.contentType = logoContentType;
            FileInputStream logoStream = new FileInputStream(logoFile);
            int totalLen = (int)logoFile.length();
            byte[] logo = new byte[totalLen];
            int offset = 0;
            while (offset < totalLen) {
                int readLen = logoStream.read(logo, offset, totalLen - offset);
                offset += readLen;
            }
            antiFakePicture.antiFakePicture = logo;
            JPA.em().persist(antiFakePicture);
            return ok(toJson(antiFakePicture.id));
        } else {
            return badRequest("upload failed!");
        }
    }

    @Transactional
    public static Result removeAntiFakePicture(Long managerId, Long picture)
    {

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        AntiFakePicture antiFakePicture = JPA.em().find(AntiFakePicture.class, picture);
        JPA.em().remove(antiFakePicture);
        return ok();
    }

    @Transactional
    public static Result getBatches(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
		//the group disbanded, group batch will not return.
        List<Batch> allBatches = new ArrayList<>();
        Iterator iterator = manager.company.allBatches.iterator();
        while (iterator.hasNext()) {
            Batch batch = (Batch) iterator.next();
            if (batch instanceof FreeBatch) {
                FreeBatch freeBatch = (FreeBatch)batch;
                if (freeBatch.belongGroup != null && freeBatch.belongGroup.isEnabled == false || (freeBatch.belongGroup != null && freeBatch.isEnabled == false)) {
                    continue;
                }
            }
            allBatches.add(batch);
        }
        return ok(toJson(allBatches));
    }

    @Transactional
    public static Result getFreeBatches(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryFreeBatches");
        query.setParameter("company", manager.company);
        return ok(toJson(query.getResultList()));
    }

    @Transactional
    public static Result getGrouponBatches(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryEnabledGrouponBatches");
        query.setParameter("company", manager.company);
        return ok(toJson(query.getResultList()));
    }

    @Transactional
    public static Result updateBatch(Long managerId, Long batchId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Batch batch = JPA.em().find(Batch.class, batchId);

        // can not disable batch if batch is already be used in an active issue rule
        boolean isEnabled = json.get("isEnabled").asBoolean();
        if (!isEnabled) {
            Query query = JPA.em().createNamedQuery("findEnabledIssueRulesForBatch");
            query.setParameter("batch", batch);
            List<IssueRule> rules = query.getResultList();
            if (!rules.isEmpty()) {
                return badRequest();
            }
        }
        batch.isEnabled = json.get("isEnabled").asBoolean();
        JPA.em().persist(batch);
        return ok(toJson(batch));
    }

    @Transactional
    public static Result postBatch(Long managerId) throws Exception
    {
        if (!isVersionMatch("2")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();
        String batchName = json.get("name").asText();
        Long maxNumber = json.get("maxNumber").asLong();
        int maxTransfer = json.get("maxTransfer").asInt();
        String note = json.get("note").asText();
        List<Company> validCompanies = new ArrayList<Company>();
        JsonNode jvalidCompanies = json.get("validCompanies");
        Iterator iterator = jvalidCompanies.iterator();
        while (iterator.hasNext()) {
            JsonNode jvalidCompany = (JsonNode)iterator.next();
            Company validCompany = JPA.em().find(Company.class, jvalidCompany.get("id").asLong());
            validCompanies.add(validCompany);
        }
        JsonNode jexpired = json.get("expired");
        JsonNode jexpiredWhen = jexpired.get("expiredWhen");
        Expired expired = null;
        if (jexpiredWhen != null) {
            expired = new ExpiredDate(new Date(jexpiredWhen.asLong()));
            JPA.em().persist(expired);
        } else {
            JsonNode jdays = jexpired.get("days");
            if (jdays != null) {
                int days = jdays.asInt();
                Query query = JPA.em().createNamedQuery("queryExpiredDays");
                query.setParameter("days", days);
                List<Expired> expires = query.getResultList();
                if (expires.isEmpty()) {
                    expired = new ExpiredDays(days);
                    JPA.em().persist(expired);
                } else {
                    expired = expires.get(0);
                }
            } else {
                JsonNode jmonths = jexpired.get("months");
                if (jmonths != null) {
                    int months = jmonths.asInt();
                    Query query = JPA.em().createNamedQuery("queryExpiredMonths");
                    query.setParameter("months", months);
                    List<Expired> expires = query.getResultList();
                    if (expires.isEmpty()) {
                        expired = new ExpiredMonths(months);
                        JPA.em().persist(expired);
                    } else {
                        expired = expires.get(0);
                    }
                }
            }
        }
        if (expired == null) throw new Exception();

        Batch batch = null;
        JsonNode jfaceValue = json.get("faceValue");
        if (jfaceValue != null) {
            Long faceValue = jfaceValue.asLong();
            batch = new DeductionBatch(batchName, note, manager.company, manager, validCompanies, maxNumber, maxTransfer, expired, faceValue, null);
        } else {
            JsonNode jdiscount = json.get("discount");
            if (jdiscount != null) {
                int discount = jdiscount.asInt();
                boolean once = json.get("once").asBoolean();
                batch = new DiscountBatch(batchName, note, manager.company, manager, validCompanies, maxNumber, maxTransfer, expired, discount, once, null);
            } else {
                JsonNode jcost = json.get("cost");
                if (jcost != null) {
                    Long cost = jcost.asLong();
                    Long deduction = json.get("deduction").asLong();
                    batch = new GrouponBatch(batchName, note, manager.company, manager, validCompanies, maxNumber, maxTransfer, expired, cost, deduction);
                }
            }
        }
        if (batch == null) throw new Exception();
        JPA.em().persist(batch);
        return ok(toJson(batch));
    }

    @Transactional
    public static Result postCompanyProductBatches(Long managerId, Long companyProductId) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        if (manager.company.companyType != 0) {
            return badRequest("only manufacturer could issue");
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        JsonNode json = request().body().asJson();
        String batchName = json.get("name").asText();
        Long maxNumber = json.get("maxNumber").asLong();
        int maxTransfer = json.get("maxTransfer").asInt();
        String note = json.get("note").asText();
        List<Company> validCompanies = new ArrayList<>();
        //determine the scope of this companyproductbatch
        if (!manager.company.exclusiveCompanies.isEmpty()) {
            Iterator itExclusive = manager.company.exclusiveCompanies.iterator();
            while (itExclusive.hasNext()) {
                Company exclusive = (Company)itExclusive.next();
                Query query = JPA.em().createNamedQuery("findCompanyProductsOfSource");
                query.setParameter("company", exclusive);
                query.setParameter("source", companyProduct);
                List<CompanyProduct> companyProducts = query.getResultList();
                if (!companyProducts.isEmpty()) {
                    validCompanies.add(exclusive);
                }
            }
        }

        JsonNode jexpired = json.get("expired");
        JsonNode jexpiredWhen = jexpired.get("expiredWhen");
        Expired expired = null;
        if (jexpiredWhen != null) {
            expired = new ExpiredDate(new Date(jexpiredWhen.asLong()));
            JPA.em().persist(expired);
        } else {
            JsonNode jdays = jexpired.get("days");
            if (jdays != null) {
                int days = jdays.asInt();
                Query query = JPA.em().createNamedQuery("queryExpiredDays");
                query.setParameter("days", days);
                List<Expired> expires = query.getResultList();
                if (expires.isEmpty()) {
                    expired = new ExpiredDays(days);
                    JPA.em().persist(expired);
                } else {
                    expired = expires.get(0);
                }
            } else {
                JsonNode jmonths = jexpired.get("months");
                if (jmonths != null) {
                    int months = jmonths.asInt();
                    Query query = JPA.em().createNamedQuery("queryExpiredMonths");
                    query.setParameter("months", months);
                    List<Expired> expires = query.getResultList();
                    if (expires.isEmpty()) {
                        expired = new ExpiredMonths(months);
                        JPA.em().persist(expired);
                    } else {
                        expired = expires.get(0);
                    }
                }
            }
        }
        if (expired == null) throw new Exception();

        Batch batch = null;
        if (companyProduct != null) {
            batch = new CompanyProductBatch(batchName, note, manager.company, manager, validCompanies, maxNumber, maxTransfer, expired, companyProduct);
        }
        if (batch == null) throw new Exception();
        JPA.em().persist(batch);
        return ok(toJson(batch));
    }

    @Transactional
    public static Result getCompanyProductBatches(Long managerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryEnabledCompanyProductBatches");
        query.setParameter("company", manager.company);
        List<CompanyProductBatch> batches = query.getResultList();
        return ok(toJson(batches));
    }

    @Transactional
    public static Result getCompanyProductBatchesAvaiableToMe(Long managerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryEnabledCompanyProductBatchesAvaiableInCompany");
        query.setParameter("company", manager.company);
        List<CompanyProductBatch> batches = query.getResultList();
        return ok(toJson(batches));
    }

    @Transactional
    public static Result getCompanyProductTickets(Long managerId, Long customerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (noBalance(manager.company)) {
            return forbidden();
        }

        Query query = JPA.em().createNamedQuery("findCustomerCompanyProductTicketsValidInCompany");
        query.setParameter("customerId", customerId);
        query.setParameter("company", manager.company);
        ArrayNode jtickets = new ArrayNode(JsonNodeFactory.instance);
        List<CompanyProductTicket> tickets = query.getResultList();
        Iterator iterator = tickets.iterator();
        while (iterator.hasNext()) {
            CompanyProductTicket ticket = (CompanyProductTicket) iterator.next();
            JsonNode jticket = toJson(ticket);
            ObjectNode o = (ObjectNode)jticket;
            HashMap batchMap = new HashMap();
            batchMap.put("id", ticket.batch.id);
            batchMap.put("name", ticket.batch.name);
            HashMap companyMap = new HashMap();
            companyMap.put("id", ticket.batch.company.id);
            companyMap.put("name", ticket.batch.company.name);
            batchMap.put("company", companyMap);
            o.put("batch", toJson(batchMap));
            jtickets.add(jticket);
        }
        return ok(jtickets);
    }

    @Transactional
    public static Result postCompanyProductTickets(Long managerId) throws Exception
    {

        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();

        String name = json.get("name").asText();
        Long batchId = json.get("batch").get("id").asLong();
        CompanyProductBatch productBatch= JPA.em().find(CompanyProductBatch.class, batchId);
        if (!productBatch.company.id.equals(manager.company.id)) {
            return badRequest();
        }
        int scope = json.get("scope").asInt();
        if (scope == 0) {
            /*
            for manufactureres's broadcast...
             */
            return ok();
        } else if (scope == 1) {
            Query query = JPA.em().createNamedQuery("queryNotificationOfCompany");
            query.setParameter("company", manager.company);
            List<Poster> posters = query.setMaxResults(1).getResultList();
            if (!posters.isEmpty()) {
                Poster poster = posters.get(0);
                // check interval
                int interval = NOTIFICATION_INTERVAL;
                query = JPA.em().createNamedQuery("queryConfig");
                query.setParameter("configName", "NOTIFICATION_INTERVAL");
                List<TicketConfig> ticketConfigs = query.getResultList();
                if (!ticketConfigs.isEmpty()) {
                    TicketConfig tc = ticketConfigs.get(0);
                    interval = Integer.parseInt(tc.configValue);
                }
                Calendar calendarExpired = Calendar.getInstance();
                calendarExpired.setTime(poster.posterWhen);
                calendarExpired.add(Calendar.SECOND, interval);
                if (calendarExpired.after(Calendar.getInstance())) {
                    return badRequest("time interval error");
                }
            }
            CompanyTicket productTicket = new CompanyTicket(manager.company, name, productBatch, scope);
            JPA.em().persist(productTicket);
            List<CompanyCustomer> companyCustomers = manager.company.companyCustomers;
            Iterator<CompanyCustomer> iter = companyCustomers.iterator();
            while (iter.hasNext()) {
                CompanyCustomer companyCustomer = (CompanyCustomer)iter.next();
                CompanyTicketInfo companyTicketInfo = new CompanyTicketInfo(companyCustomer.customer, manager.company, productTicket);
                JPA.em().persist(companyTicketInfo);
            }
            return ok(toJson(productTicket));
        } else {
            return badRequest("parameter error");
        }
    }

    @Transactional
    public static Result getIssueRules(Long managerId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findEnabledIssueRulesOfCompany");
        query.setParameter("company", manager.company);
        List<IssueRule> rules = query.getResultList();
        return ok(toJson(rules));
    }

    @Transactional
    public static Result updateIssueRule(Long managerId, Long ruleId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        IssueRule rule = JPA.em().find(IssueRule.class, ruleId);

        JsonNode isEnabledNode = json.get("isEnabled");
        rule.isEnabled = isEnabledNode.asBoolean();
        return ok(toJson(rule));
    }

    @Transactional
    public static Result postIssueRule(Long managerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        String ruleName = json.get("name").asText();
        Long startingAmount = json.get("startingAmount").asLong();
        Long endAmount = json.get("endAmount").asLong();
        Long batchId = json.get("batch").get("id").asLong();
        Batch batch = JPA.em().find(Batch.class, batchId);
        if (batch.company.id != manager.company.id) {
            return badRequest();
        }
        IssueRule newRule = new IssueRule(ruleName, manager.company, startingAmount, endAmount, batch);

        JPA.em().persist(newRule);
        return ok(toJson(newRule));
    }

    @Transactional
    public static Result getUseRules(Long managerId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findEnabledUseRulesOfCompany");
        query.setParameter("company", manager.company);
        List<UseRule> rules = query.getResultList();
        return ok(toJson(rules));
    }

    @Transactional
    public static Result updateUseRule(Long managerId, Long ruleId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        UseRule oldRule = JPA.em().find(UseRule.class, ruleId);

        JsonNode isEnabledNode = json.get("isEnabled");
        JsonNode discountNode = json.get("discount");
        JsonNode rateNode = json.get("rate");
        if (isEnabledNode != null && oldRule != null) {   // enable or disable
            oldRule.isEnabled = isEnabledNode.asBoolean();
            JPA.em().persist(oldRule);
            return ok(toJson(oldRule));
        } else if (discountNode != null && oldRule != null) {
            int discount = discountNode.asInt();
            ((UseDiscountRule) oldRule).discount = discount;
            ((UseDiscountRule) oldRule).discountType = 1;
        }else if (rateNode != null && oldRule != null) {
            int rate = json.get("rate").asInt();
            ((UseDeductionRule)oldRule).rate = rate;
        } else {
            return badRequest();
        }
        JPA.em().persist(oldRule);
        return ok(toJson(oldRule));
    }


    @Transactional
    public static Result postUseRule(Long managerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        UseRule newRule = null;

        String ruleName = json.get("name").asText();
        Long leastConsume = json.get("leastConsume").asLong();
        Long maxDeduction = json.get("maxDeduction").asLong();
        Long maxConsume=json.get("maxConsume").asLong();
        String note = json.get("note").asText();
        List<TicketBatch> batches = new ArrayList<TicketBatch>();
        JsonNode jBatches = json.get("batches");
        if (jBatches != null) {
            Iterator iterator = jBatches.iterator();
            while (iterator.hasNext()) {
                JsonNode jBatch = (JsonNode)iterator.next();
                TicketBatch batch = JPA.em().find(TicketBatch.class, jBatch.get("id").asLong());
                batches.add(batch);
            }
        }

        // check batch.issuedBy is member of company.associateCompanies
        Iterator iterator = batches.iterator();
        while (iterator.hasNext()) {
            TicketBatch batch = (TicketBatch)iterator.next();
            if (batch.company.id != manager.company.id) {
                Query query = JPA.em().createNamedQuery("checkAssociateCompany");
                query.setParameter("company", manager.company);
                query.setParameter("issuedBy", batch.company);
                List<Company> companies = query.getResultList();
                if (companies.isEmpty()) {
                    return badRequest();
                }
            }
        }

        JsonNode discountNode = json.get("discountType");
        if (discountNode != null) {     // discount
            int discountType = discountNode.asInt();
            if (discountType == 0) {
                double convertRate = json.get("convertRate").asDouble();
                newRule = new UseDiscountRule(ruleName, manager.company, maxConsume,leastConsume, maxDeduction, batches, convertRate, note);
            } else {
                int discount = json.get("discount").asInt();
                newRule = new UseDiscountRule(ruleName, manager.company, maxConsume,leastConsume, maxDeduction, batches, discount, note);
            }
        } else {
            int rate = json.get("rate").asInt();
            newRule = new UseDeductionRule(ruleName, manager.company, maxConsume,leastConsume, maxDeduction, batches, rate, note);
        }

        JPA.em().persist(newRule);
        return ok(toJson(newRule));
    }

    @Transactional
    public static Result getFreeBatchesAvailableToMe(Long managerId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryFreeBatchesAvaiableInCompany");
        query.setParameter("company", manager.company);
        List<TicketBatch> batches = query.getResultList();
        return ok(toJson(batches));
    }

    @Transactional
    public static Result getGrouponBatchesAvailableToMe(Long managerId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryGrouponBatchesAvaiableInCompany");
        query.setParameter("company", manager.company);
        List<TicketBatch> batches = query.getResultList();
        return ok(toJson(batches));
    }

    @Transactional
    public static Result getCompanies(Long managerId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findAllActiveCompany");
        List<Company> companies = query.getResultList();
        return ok(toJson(companies));
    }

    @Transactional
    public static Result getCommonCompany(Long managerId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findCompanyByCId");
        query.setParameter("cId", companyId);
        List<Company> companies = query.getResultList();
        Long id = companies.get(0).id;

        Company company = JPA.em().find(Company.class, id);
        ArrayNode jcompany = new ArrayNode(JsonNodeFactory.instance);
        if (company != null && company.companyType > 0) {
            boolean isExclusive = false;
            if (company.manufacturers != null && company.manufacturers.contains(manager.company)) {
                isExclusive = true;
            }
            JsonNode j = toJson(company);
            ObjectNode o = (ObjectNode) j;
            o.put("isExclusive", isExclusive);
            jcompany.add(o);
            return ok(jcompany);
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getManufacturers(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        ArrayNode jmanufacturers = new ArrayNode(JsonNodeFactory.instance);
        Query query = JPA.em().createNamedQuery("findAllManufacturers");
        query.setParameter("exclusive", manager.company);
        List<ManufacturerAndCompany> manufacturers = query.getResultList();
        return ok(toJson(manufacturers));
    }

    @Transactional
    public static Result getManufacturersByName(Long managerId, String companyName)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findManufacturerByKey");
        query.setParameter("key", "%" + companyName + "%");
        List<Company> manufacturers = query.getResultList();
        ArrayNode jmanufacturers = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = manufacturers.iterator();
        while (iterator.hasNext()) {
            Company manufacturer = (Company) iterator.next();
            if (manufacturer != null) {
                boolean isManufacturer = false;
                if (!manufacturer.exclusiveCompanies.isEmpty() && manufacturer.exclusiveCompanies.contains(manager.company)) {
                    isManufacturer = true;
                }
                JsonNode j = toJson(manufacturer);
                ObjectNode o = (ObjectNode) j;
                o.put("isManufacturer", isManufacturer);
                jmanufacturers.add(o);
            }
        }
        return ok(jmanufacturers);
    }

    @Transactional
    public static Result getExclusiveCompanies(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findAllExclusiveCompanies");
        query.setParameter("manufacturer", manager.company);
        List<ManufacturerAndCompany> exclusiveCompanies = query.getResultList();
        return ok(toJson(exclusiveCompanies));
    }

    @Transactional
    public static Result getManufacturer(Long managerId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findCompanyByCId");
        query.setParameter("cId", companyId);
        List<Company> companies = query.getResultList();
        Long id = companies.get(0).id;

        query = JPA.em().createNamedQuery("findManufacturerById");
        Company company = JPA.em().find(Company.class, id);
        query.setParameter("company", company);
        List<Company> mcs = query.getResultList();
        ArrayNode jmanufacturers = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = mcs.iterator();
        while (iterator.hasNext()) {
            Company manufacturer = (Company) iterator.next();
            if (manufacturer != null) {
                boolean isManufacturer = false;
                if (!manufacturer.exclusiveCompanies.isEmpty() && manufacturer.exclusiveCompanies.contains(manager.company)) {
                    isManufacturer = true;
                }
                JsonNode j = toJson(manufacturer);
                ObjectNode o = (ObjectNode) j;
                o.put("isManufacturer", isManufacturer);
                jmanufacturers.add(o);
            }
        }
        return ok(jmanufacturers);
    }
	
    @Transactional
    public static Result postManufacturers(Long managerId)
    {
        if (!isVersionMatch("1")) {
           return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode jsonNode = request().body().asJson();
        Long companyId = jsonNode.get("id").asLong();
        if (companyId.longValue() == manager.company.id) {
            return badRequest("can not add myself!");
        }
        Query query = JPA.em().createNamedQuery("findManufacturerAndCompany");
        Company company = JPA.em().find(Company.class, companyId);
        //生产商邀请
        if (manager.company.companyType == 0) {

            query.setParameter("manufacturer", manager.company);
            query.setParameter("exclusive", company);
            List<ManufacturerAndCompany> mcs = query.getResultList();
            if (mcs.isEmpty()) {
                ManufacturerAndCompany mc = new ManufacturerAndCompany(manager.company, company, 3);
                JPA.em().persist(mc);
                return ok(toJson(mc));
            } else {
                if (mcs.get(0).status == 1 && mcs.get(0).isRemoved != true) {
                    return forbidden();
                }
                mcs.get(0).status = 3;
                mcs.get(0).isRemoved = false;
                JPA.em().persist(mcs.get(0));
                return ok(toJson(mcs.get(0)));
            }
        } else{//二级商家申请

            query.setParameter("manufacturer", company);
            query.setParameter("exclusive", manager.company);
            List<ManufacturerAndCompany> mcs = query.getResultList();
            if (mcs.isEmpty()) {
                ManufacturerAndCompany mc = new ManufacturerAndCompany(company, manager.company, 2);
                JPA.em().persist(mc);
                return ok(toJson(mc));
            } else {
                if (mcs.get(0).status == 1 && mcs.get(0).isRemoved != true) {
                    return forbidden();
                }
                mcs.get(0).status = 2;
                mcs.get(0).isRemoved = false;
                JPA.em().persist(mcs.get(0));
                return ok(toJson(mcs.get(0)));
            }
        }
    }

    @Transactional
    public static Result deleteManufacturers(Long managerId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Company company = JPA.em().find(Company.class, companyId);
        manager.company.manufacturers.remove(company);
        JPA.em().merge(manager.company);
        return ok(toJson(company));
    }

    @Transactional
    public static Result deleteExclusiveCompanies(Long managerId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Company company = JPA.em().find(Company.class, companyId);
        if (!manager.company.exclusiveCompanies.isEmpty()) {
            manager.company.exclusiveCompanies.remove(company);
        }
        if (!company.manufacturers.isEmpty()) {
            company.manufacturers.remove(manager.company);
        }
        Query query =JPA.em().createNamedQuery("findVaildManufacturerAndCompany");
        query.setParameter("manufacturer", manager.company);
        query.setParameter("exclusive", company);
        List<ManufacturerAndCompany> mcs = query.getResultList();
        if (!mcs.isEmpty()) {
            mcs.get(0).isRemoved = true;
            JPA.em().persist(mcs.get(0));
        }

        JPA.em().merge(company);
        JPA.em().merge(manager.company);
        //解除生产商和经销商的产品继承关系...
        query = JPA.em().createNamedQuery("findVehicleProductsOfCompany");
        query.setParameter("company", company);
        List<CompanyProduct> companyProducts = query.getResultList();
        Iterator iterator = companyProducts.iterator();
        while (iterator.hasNext()) {
            CompanyProduct companyProduct = (CompanyProduct) iterator.next();
            if (companyProduct.source.company.equals(manager.company)) {
                companyProduct.isRemoved = true;
                query = JPA.em().createNamedQuery("queryEnabledCompanyProductBatchesByCompanyProduct");
                query.setParameter("companyProduct", companyProduct.source);
                List<CompanyProductBatch> cpbs = query.getResultList();
                if (!cpbs.isEmpty()) {
                    Iterator iterator1 = cpbs.iterator();
                    while (iterator1.hasNext()) {
                        TicketBatch ticketBatch = (TicketBatch) iterator1.next();
                        ticketBatch.validCompanies.remove(company);
                        JPA.em().persist(ticketBatch);
                    }
                }
                JPA.em().persist(companyProduct);
            }
        }
        return ok(toJson(company));
    }

    //二级商户同意。
    @Transactional
    public static Result putManufacturers(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode jsonNode = request().body().asJson();
        int type = jsonNode.get("type").asInt();
        Long companyId = jsonNode.get("id").asLong();

        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("findManufacturerAndCompany");
        query.setParameter("manufacturer", company);
        query.setParameter("exclusive", manager.company);
        List<ManufacturerAndCompany> mcs = query.getResultList();
        if (mcs.isEmpty()) {
            return badRequest();
        }
        if (type == 0) {
            mcs.get(0).status = 0;
            JPA.em().persist(mcs.get(0));
            return ok(toJson(mcs.get(0)));
        } else if (type == 1){
            mcs.get(0).status = 2;
            JPA.em().persist(mcs.get(0));
            return ok(toJson(mcs.get(0)));
        } else {
            return badRequest();
        }
    }

    //生产商审核通过。
    @Transactional
    public static Result putExclusiveCompanies(Long managerId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode jsonNode = request().body().asJson();
        int type = jsonNode.get("type").asInt();

        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("findManufacturerAndCompany");
        query.setParameter("manufacturer", manager.company);
        query.setParameter("exclusive", company);
        List<ManufacturerAndCompany> mcs = query.getResultList();

        if (type == 0) {
            if (mcs.isEmpty()) {
                return badRequest();
            } else {
                mcs.get(0).status = 0;
                JPA.em().persist(mcs.get(0));
                return ok(toJson(mcs.get(0)));
            }
        } else if (type == 1){

            if (mcs.isEmpty()) {
                return badRequest();
            } else {
                mcs.get(0).status = 1;
                JPA.em().persist(mcs.get(0));
            }
            if (!company.manufacturers.contains(manager.company)) {
                company.manufacturers.add(manager.company);
            }
            if (!manager.company.exclusiveCompanies.contains(company)) {
                manager.company.exclusiveCompanies.add(company);
            }
            JPA.em().merge(manager.company);
            JPA.em().persist(company);
            return ok(toJson(company));

        } else {
            return badRequest();
        }
    }

    @Transactional
    public static Result getAssociateCompanies(Long managerId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        List<Company> associates = manager.company.associateCompanies;
        return ok(toJson(associates));
    }

    @Transactional
    public static Result getCompanyGroups(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findEnabledCompanyGroupsOfCompany");
        query.setParameter("master", manager.company.id);
        query.setParameter("company", manager.company);
        List<CompanyGroup> companyGroups = query.getResultList();

        ArrayNode jgroups = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = companyGroups.iterator();
        while (iterator.hasNext()) {
            CompanyGroup jGroup = (CompanyGroup) iterator.next();

            Logger.info("-----------group Name-----------"+jGroup.name);
            for (Company com :
                    jGroup.members) {
              Logger.info("agree: "+com.name);
            }
            for (Company com :
                    jGroup.disAgreeMembers) {
                Logger.info("dis: "+com.name);
            }
            Logger.info("---------------------------------");
            JsonNode jg = toJson(jGroup);
            ObjectNode og = (ObjectNode) jg;
            List<Company> companies = new ArrayList<>();
            companies.addAll(jGroup.disAgreeMembers);
            companies.addAll(jGroup.members);

            if (companies.size() > 0) {
                ArrayNode jmembers = new ArrayNode(JsonNodeFactory.instance);
                Iterator iMember = companies.iterator();
                while (iMember.hasNext()) {
                    Company member = (Company) iMember.next();
                    JsonNode j = toJson(member);
                    ObjectNode o = (ObjectNode) j;
                    if(manager.company.id == jGroup.master) {
                        if (member.groups.contains(jGroup)) {
                            o.put("isAgree", true);
                        } else {
                            o.put("isAgree", false);
                        }
                    }else {

                        if(member.groups.contains(jGroup)){
                            o.put("isMember","IS");
                        }else{
                            if(member.id == manager.company.id){
                                o.put("isMember","NOT");
                            }else {
                                continue;
                            }
                        }
                    }
                    jmembers.add(o);
                }
                og.put("members", toJson(jmembers));
            }
            jgroups.add(og);
        }
        return ok(toJson(jgroups));
    }

    @Transactional
    public static Result postCompanyGroups(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        String groupName = json.get("groupname").asText();
        CompanyGroup companyGroup = new CompanyGroup(manager.company.id, groupName);
        JPA.em().persist(companyGroup);
        return ok(toJson(companyGroup));
    }

    //remove group
    @Transactional
    public static Result deleteCompanyGroups(Long managerId, Long companyGroupId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findEnabledCompanyGroupBygId");
        query.setParameter("gId", companyGroupId);
        List<CompanyGroup> companyGroups = query.getResultList();
        CompanyGroup companyGroup = null;
        if (!companyGroups.isEmpty()) {
            companyGroup = companyGroups.get(0);
        } else {
            return badRequest();
        }

        if (companyGroup.master.longValue() != manager.company.id.longValue()) {

            return badRequest("not master of group");
        }

        List<Company> companies = new ArrayList();
        companies.addAll(companyGroup.members);
        for (Company company: companies) {
            query = JPA.em().createNamedQuery("queryFreeBatchesAvaiableInCompanyAndGroup");
            query.setParameter("company", company);
            query.setParameter("companyGroup", companyGroup);
            List<FreeBatch> freeBatches = query.getResultList();

            for (FreeBatch freeBatch: freeBatches) {
                query = JPA.em().createNamedQuery("findEnabledIssueRulesForBatch");
                query.setParameter("batch", freeBatch);
                List<IssueRule> issueRules = query.getResultList();
                if (!issueRules.isEmpty()) {
                    issueRules.get(0).isEnabled = false;
                    JPA.em().persist(issueRules.get(0));
                }
                freeBatch.isEnabled = false;
                JPA.em().persist(freeBatch);
            }
        }

        boolean flag = false;
        List<Company> allCompanies = new ArrayList<>();
        allCompanies.addAll(companyGroup.members);
        allCompanies.addAll(companyGroup.disAgreeMembers);
        List<Company> companyList = new ArrayList<>();
        companyList.addAll(allCompanies);
        for (Company member: allCompanies) {
            companyList.remove(member);
            query = JPA.em().createNamedQuery("queryOtherFreeBatchesAvaiableInCompany");
            query.setParameter("company", member);
            query.setParameter("companyGroup", companyGroup);
            List<FreeBatch> otherBatches = query.getResultList();
            for (Company company1 : companyList) {
                for (FreeBatch freeBatch : otherBatches) {
                    if (freeBatch.validCompanies.contains(company1)) {
                        flag = true;
                    }
                }
                if (flag) {
                    continue;
                }
                member.associateCompanies.remove(company1);
                JPA.em().merge(member);

            }
        }

        companyGroup.isEnabled = false;
        companyGroup.members.clear();
        companyGroup.disAgreeMembers.clear();
        JPA.em().merge(companyGroup);
        return ok();
    }

    @Transactional
    public static Result deleteGroupMembers(Long managerId, Long companyGroupId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findEnabledCompanyGroupBygId");
        query.setParameter("gId", companyGroupId);
        List<CompanyGroup> companyGroups = query.getResultList();
        CompanyGroup companyGroup = null;
        if (!companyGroups.isEmpty()) {
            companyGroup = companyGroups.get(0);
        } else {
            return badRequest();
        }

        Company company = JPA.em().find(Company.class, companyId);
        Company master = JPA.em().find(Company.class, companyGroup.master);

        query = JPA.em().createNamedQuery("queryFreeBatchesAvaiableInCompanyAndGroup");
        query.setParameter("company", company);
        query.setParameter("companyGroup", companyGroup);
        List<FreeBatch> freeBatches = query.getResultList();
        for (FreeBatch freeBatch: freeBatches) {
            query = JPA.em().createNamedQuery("findEnabledIssueRulesForBatch");
            query.setParameter("batch", freeBatch);
            List<IssueRule> issueRules = query.getResultList();
            if (!issueRules.isEmpty()) {
                issueRules.get(0).isEnabled = false;
                JPA.em().persist(issueRules.get(0));
            }
            for (UseRule useRule: freeBatch.useRules) {
                if (useRule.usedBy.equals(company)) {
                    useRule.batches.clear();
                    useRule.batches.add(freeBatch);
                    Logger.info("out of the members to change the rules");
                    JPA.em().merge(useRule);
                }
            }
            freeBatch.isEnabled = false;
            JPA.em().persist(freeBatch);
        }

        //delete relations of cooperation
        List<Company> companyList = new ArrayList<>();
        companyList.addAll(companyGroup.members);
        for (Company disMember: companyGroup.disAgreeMembers) {
            Logger.info("====disMember info===+++"+disMember.name);
        }
        for (Company member: companyGroup.members) {
            Logger.info("====Member info===+++"+member.name);
            companyList.remove(member);

            if (member.equals(company)){
                query = JPA.em().createNamedQuery("queryOtherFreeBatchesAvaiableInCompany");
                query.setParameter("company", member);
                query.setParameter("companyGroup", companyGroup);
                List<FreeBatch> otherBatches = query.getResultList();
                boolean flag = false;
                for (Company company1 : companyList) {
                    for (FreeBatch freeBatch : otherBatches) {
                        if (freeBatch.validCompanies.contains(company1)) {
                            flag = true;
                        }
                    }
                    if (flag) {//if there are other relations of cooperation, both cannot be deleted.
                        continue;
                    }

                    member.associateCompanies.remove(company1);
                    JPA.em().merge(member);
                }
            } else {
                query = JPA.em().createNamedQuery("queryOtherFreeBatchesAvaiableInCompany");
                query.setParameter("company", member);
                query.setParameter("companyGroup", companyGroup);
                List<FreeBatch> otherBatches = query.getResultList();
                boolean flag = false;
                for (Company company1 : companyList) {
                    for (FreeBatch freeBatch : otherBatches) {
                        if (freeBatch.validCompanies.contains(company)) {
                            flag = true;
                        }
                    }
                    if (flag) {
                        continue;
                    }
                    member.associateCompanies.remove(company);
                    JPA.em().persist(member);
                }
            }
            companyList.add(member);

        }

        companyGroup.members.remove(company);
        JPA.em().merge(companyGroup);

        for (Company other: companyGroup.members) {
            query = JPA.em().createNamedQuery("queryFreeBatchesAvaiableInCompanyAndGroup");
            query.setParameter("company", other);
            query.setParameter("companyGroup", companyGroup);
            List<FreeBatch> allBatches = query.getResultList();
            for (FreeBatch batch: allBatches) {
                batch.validCompanies.remove(company);
                for (UseRule useRule: batch.useRules) {
                    if (!useRule.usedBy.equals(company)) {
                        useRule.batches.removeAll(freeBatches);
                        Logger.info("there is also a members to change the rules");
                        JPA.em().merge(useRule);
                    }
                }
                JPA.em().merge(batch);
            }
        }

        return ok();
    }

   //  群主（公司）可以终止自己创建的群策略，一旦终止 其成员公司的对应批次也被终止 所有的发券规则都被终止 使用规则不变
    @Transactional
    public static Result deleteCompanyGroupBatch(Long managerId, Long batchId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryOneFreeBatchForCompanyGroupById");
        query.setParameter("company", manager.company);
        query.setParameter("batchId",batchId);
        List<FreeBatch> freebatches = query.getResultList();
        FreeBatch batch = null;
        if(!freebatches.isEmpty()){
            batch = freebatches.get(0);
            if(batch.belongGroup.master != manager.company.id){
                return badRequest("not master cannot quit group Batch");
            }
        }else {
            return badRequest("not found valid batch");
        }
        List<Company> companies = new ArrayList();
        companies.addAll(batch.belongGroup.members);
        for (Company company: companies) {
            query = JPA.em().createNamedQuery("queryOneFreeBatchForCompanyGroupByGroupAndSourceBatch");
            query.setParameter("company", company);
            query.setParameter("sourceBatchId", batch.id);
            List<FreeBatch> freeBatches = query.getResultList();
            Logger.info("==groupInfoBatch==="+company.name);
            if(!freeBatches.isEmpty()){
                FreeBatch freeBatch = freeBatches.get(0);
                freeBatch.isEnabled = false;
                query = JPA.em().createNamedQuery("findEnabledIssueRulesForBatch");
                query.setParameter("batch", freeBatch);
                List<IssueRule> issueRules = query.getResultList();
                if (!issueRules.isEmpty()) {
                    issueRules.get(0).isEnabled = false;
                    JPA.em().persist(issueRules.get(0));
                }
                freeBatch.isEnabled = false;
                JPA.em().persist(freeBatch);
            }
        }

        return ok();
    }


    @Transactional
    public static Result postGroupMembers(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Long gId = json.get("gId").asLong();
        Query query = JPA.em().createNamedQuery("findEnabledCompanyGroupBygId");
        query.setParameter("gId", gId);
        List<CompanyGroup> companyGroups = query.getResultList();
        CompanyGroup companyGroup = null;
        if (!companyGroups.isEmpty()) {
            companyGroup = companyGroups.get(0);
        } else {
            companyGroup = new CompanyGroup(manager.company.id, manager.company.name);
        }

        if (companyGroup.master.longValue() != manager.company.id.longValue()) {
            return badRequest("not master of group");
        }

        JsonNode companyGroupNode = json.get("companygroup");
        if (companyGroupNode != null) {
            JsonNode membersNode = companyGroupNode.get("members");
            Iterator itMembers = membersNode.iterator();
            while (itMembers.hasNext()) {
                JsonNode jsonNode = (JsonNode)itMembers.next();
                Long companyId = jsonNode.get("companyId").asLong();
                Company company = JPA.em().find(Company.class, companyId);
                if (company.companyType == 0) {
                    return forbidden("cannot invite manufacturer");
                }
                if (!companyGroup.disAgreeMembers.contains(company) && !companyGroup.members.contains(company)) {
                    if(!manager.company.associateCompanies.contains(company)){
                        manager.company.associateCompanies.add(company);
                    }
                    companyGroup.disAgreeMembers.add(company);
                    if(companyGroup.members.isEmpty()) {
                        Logger.info("members_size ===="+companyGroup.members.size());
                        companyGroup.members.add(manager.company);
                    }
                    JPA.em().merge(manager.company);
                    JPA.em().merge(companyGroup);

                } else {
                    return badRequest("already registered or invited");
                }
            }
        }
        return ok(toJson(companyGroup));
    }

    @Transactional
    public static Result putGroupMembers(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Long gId = json.get("gId").asLong();
        Query query = JPA.em().createNamedQuery("findEnabledCompanyGroupBygId");
        query.setParameter("gId", gId);
        List<CompanyGroup> companyGroups = query.getResultList();
        CompanyGroup companyGroup = null;
        if (!companyGroups.isEmpty()) {
            companyGroup = companyGroups.get(0);
        } else {
            return badRequest();
        }

        companyGroup.disAgreeMembers.remove(manager.company);
        companyGroup.members.add(manager.company);
        JPA.em().merge(companyGroup);

        Company master = JPA.em().find(Company.class, companyGroup.master);
        if(!manager.company.associateCompanies.contains(master)){
            manager.company.associateCompanies.add(master);
        }
        Iterator iterator = companyGroup.members.iterator();

        while (iterator.hasNext()) {
            Company member = (Company) iterator.next();
            if(member.equals(manager.company) && member.equals(master)) {
                break;
            }
            if(!member.associateCompanies.contains(manager.company)) {
                member.associateCompanies.add(manager.company);
                JPA.em().merge(member);
            }
            if(!manager.company.associateCompanies.contains(member)) {
                manager.company.associateCompanies.add(member);
                JPA.em().merge(manager.company);
            }
        }
        if (manager.company.associateCompanies.contains(manager.company)) {
            manager.company.associateCompanies.remove(manager.company);
        }
        JPA.em().merge(manager.company);

        query = JPA.em().createNamedQuery("queryFreeBatchesAvaiableInCompanyAndGroup");
        query.setParameter("company", master);
        query.setParameter("companyGroup", companyGroup);
        List<FreeBatch> allBatches = query.getResultList();

        List<TicketBatch> batches = new ArrayList<>();
        for (Company company: companyGroup.members) {

            query = JPA.em().createNamedQuery("queryFreeBatchesAvaiableInCompanyAndGroup");
            query.setParameter("company", company);
            query.setParameter("companyGroup", companyGroup);
            List<TicketBatch> freeBatches = query.getResultList();
            batches.addAll(freeBatches);
        }

        //after the mechanical copy, copy are independent of each other.
        UseRule rule = null;
        query = JPA.em().createNamedQuery("queryFreeBatchesAvaiableInCompanyAndGroup");
        query.setParameter("company", manager.company);
        query.setParameter("companyGroup", companyGroup);
        List<FreeBatch> freeBatches = query.getResultList();
        if (freeBatches.isEmpty()) {
            for (FreeBatch newBatche: allBatches) {
                FreeBatch batch = null;
                List<Company> validCompanies = new ArrayList<>();
                if (newBatche instanceof DeductionBatch) {
                    batch = new DeductionBatch(((DeductionBatch) newBatche).name, ((DeductionBatch) newBatche).note, manager.company, manager.company.managers.get(0), validCompanies, ((DeductionBatch) newBatche).maxNumber, ((DeductionBatch) newBatche).maxTransfer, ((DeductionBatch) newBatche).expired, ((DeductionBatch) newBatche).faceValue, null,((DeductionBatch) newBatche).id);
                } else if (newBatche instanceof DiscountBatch){
                    batch = new DiscountBatch(((DiscountBatch) newBatche).name, ((DiscountBatch) newBatche).note, manager.company, manager.company.managers.get(0), validCompanies, ((DiscountBatch) newBatche).maxNumber, ((DiscountBatch) newBatche).maxTransfer, ((DiscountBatch) newBatche).expired, ((DiscountBatch) newBatche).discount, ((DiscountBatch) newBatche).once, null,((DiscountBatch) newBatche).id);
                } else {
                    return badRequest();
                }
                batch.validCompanies.addAll(companyGroup.members);
                batch.belongGroup = companyGroup;
                JPA.em().persist(batch);
                Logger.info("add batche success");

                IssueRule newRule = new IssueRule(batch.name + "发券策略", manager.company, 100L, 1000000L, batch);
                JPA.em().persist(newRule);
                Logger.info("add issuerule success");

                for (UseRule useRule: newBatche.useRules) {

                    List<TicketBatch> applicableBatches = new ArrayList<>();
                    if (useRule instanceof UseDeductionRule) {
                        rule= new UseDeductionRule(useRule.name, manager.company, useRule.maxConsume,useRule.leastConsume, useRule.maxDeduction, applicableBatches, ((UseDeductionRule) useRule).rate, useRule.note);
                    } else if (useRule instanceof UseDiscountRule) {
                        if (((UseDiscountRule) useRule).discountType == 1) {
                            rule = new UseDiscountRule(useRule.name, manager.company, useRule.maxConsume,useRule.leastConsume, useRule.maxDeduction, applicableBatches, ((UseDiscountRule) useRule).convertRate, useRule.note);
                        } else {
                            rule = new UseDiscountRule(useRule.name, manager.company, useRule.maxConsume,useRule.leastConsume, useRule.maxDeduction, applicableBatches, ((UseDiscountRule) useRule).discount, useRule.note);
                        }

                    } else {
                        return badRequest();
                    }
                }

                rule.batches.addAll(batches);
                rule.batches.add(batch);
                JPA.em().persist(rule);
                batches.add((TicketBatch)batch);
            }
        }

        query = JPA.em().createNamedQuery("queryOtherFreeBatchesAvaiableInGroup");
        query.setParameter("companyGroup", companyGroup);
        List<FreeBatch> freeBatchList = query.getResultList();
        for (FreeBatch freeBatch: freeBatchList) {
            freeBatch.validCompanies.clear();
            freeBatch.validCompanies.addAll(companyGroup.members);
            JPA.em().merge(freeBatch);
            if (freeBatch != null && freeBatch.useRules != null) {
                for (UseRule useRule: freeBatch.useRules) {
                    useRule.batches.clear();
                    if (useRule instanceof UseDeductionRule) {
                        for (TicketBatch ticketBatch: batches) {
                            if (ticketBatch instanceof DeductionBatch) {
                                useRule.batches.add(ticketBatch);
                            }
                        }
                        JPA.em().merge(useRule);
                    } else if (useRule instanceof UseDiscountRule){
                        for (TicketBatch ticketBatch: batches) {
                            if (ticketBatch instanceof DiscountBatch) {
                                useRule.batches.add(ticketBatch);
                            }
                        }
                        JPA.em().merge(useRule);
                    } else {
                        return badRequest();
                    }
                }
            }
        }

        return ok(toJson(companyGroup));
    }

    @Transactional
    public static Result postGroupBatchAndIssueRules(Long managerId) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();

        Long groupId = json.get("groupId").asLong();
        CompanyGroup companyGroup = JPA.em().find(CompanyGroup.class, groupId);

        if (manager.company.id.longValue() != companyGroup.master.longValue()) {
            return badRequest("not master of group");
        }

        List<FreeBatch> freeBatches = new ArrayList<>();
        List<Company> companies = new ArrayList<>();
        Iterator iterator = companyGroup.members.iterator();
        Long sourceBatchId = null;   // 为群主批次的id
        while (iterator.hasNext()) {
            Company company = (Company) iterator.next();
            String batchName = json.get("name").asText();
            Long maxNumber = json.get("maxNumber").asLong();
            int maxTransfer = json.get("maxTransfer").asInt();
            String note = json.get("note").asText();
            List<Company> validCompanies = new ArrayList<Company>();

            validCompanies.addAll(companyGroup.members);
            JsonNode jexpired = json.get("expired");
            JsonNode jexpiredWhen = jexpired.get("expiredWhen");
            Expired expired = null;
            if (jexpiredWhen != null) {
                expired = new ExpiredDate(new Date(jexpiredWhen.asLong()));
                JPA.em().persist(expired);
            } else {
                JsonNode jdays = jexpired.get("days");
                if (jdays != null) {
                    int days = jdays.asInt();
                    Query query = JPA.em().createNamedQuery("queryExpiredDays");
                    query.setParameter("days", days);
                    List<Expired> expires = query.getResultList();
                    if (expires.isEmpty()) {
                        expired = new ExpiredDays(days);
                        JPA.em().persist(expired);
                    } else {
                        expired = expires.get(0);
                    }
                } else {
                    JsonNode jmonths = jexpired.get("months");
                    if (jmonths != null) {
                        int months = jmonths.asInt();
                        Query query = JPA.em().createNamedQuery("queryExpiredMonths");
                        query.setParameter("months", months);
                        List<Expired> expires = query.getResultList();
                        if (expires.isEmpty()) {
                            expired = new ExpiredMonths(months);
                            JPA.em().persist(expired);
                        } else {
                            expired = expires.get(0);
                        }
                    }
                }
            }
            if (expired == null) throw new Exception();

            Batch batch = null;
            JsonNode jfaceValue = json.get("faceValue");
            if (jfaceValue != null) {
                Long faceValue = jfaceValue.asLong();
                batch = new DeductionBatch(batchName, note, company, company.managers.get(0), validCompanies, maxNumber, maxTransfer, expired, faceValue, null);
            } else {
                JsonNode jdiscount = json.get("discount");
                if (jdiscount != null) {
                    int discount = jdiscount.asInt();
                    boolean once = json.get("once").asBoolean();
                    batch = new DiscountBatch(batchName, note, company, company.managers.get(0), validCompanies, maxNumber, maxTransfer, expired, discount, once, null);
                } else {
                    JsonNode jcost = json.get("cost");
                    if (jcost != null) {
                        Long cost = jcost.asLong();
                        Long deduction = json.get("deduction").asLong();
                        batch = new GrouponBatch(batchName, note, company, company.managers.get(0), validCompanies, maxNumber, maxTransfer, expired, cost, deduction);
                    }
                }
            }
            if (batch == null) throw new Exception();
            FreeBatch freeBatch = (FreeBatch)batch;
            freeBatch.belongGroup = companyGroup;
            JPA.em().persist(freeBatch);
            freeBatches.add((FreeBatch)batch);
            IssueRule newRule = new IssueRule(batchName + "发券策略", company, 100L, 1000000L, batch);
            JPA.em().persist(newRule);
            if(freeBatch.company.id != companyGroup.master){
                continue;
            }
            if(freeBatch instanceof DeductionBatch){
                DeductionBatch deduction = (DeductionBatch) freeBatch;
                sourceBatchId = deduction.id;
            }else if(freeBatch instanceof DiscountBatch){
                DiscountBatch discount = (DiscountBatch) freeBatch;
                sourceBatchId = discount.id;
            }
        }
        for (FreeBatch batch: freeBatches) {
             if(batch instanceof  DeductionBatch){
                 DeductionBatch deduction = (DeductionBatch)batch;
                 if(deduction.company.id == companyGroup.master){
                     deduction.sourceBatchId = null;
                 }else{
                     deduction.sourceBatchId = sourceBatchId;
                 }
                 JPA.em().persist(deduction);

             }else if(batch instanceof  DiscountBatch){
                 DiscountBatch discount = (DiscountBatch)batch;
                 if(discount.company.id == companyGroup.master){
                     discount.sourceBatchId = null;
                 }else {
                    discount.sourceBatchId = sourceBatchId;
                 }
                 JPA.em().persist(discount);
             }
        }
        return ok(toJson(freeBatches));
    }

    @Transactional
    public static Result postGroupUseRules(Long managerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        UseRule newRule = null;
        Long groupId = json.get("groupId").asLong();
        CompanyGroup companyGroup = JPA.em().find(CompanyGroup.class, groupId);

        if (manager.company.id.longValue() != companyGroup.master.longValue()) {
            return badRequest("not master of group");
        }

        Iterator itMember = companyGroup.members.iterator();
        while (itMember.hasNext()) {
            Company member = (Company) itMember.next();
            String ruleName = json.get("name").asText();
            Long leastConsume = json.get("leastConsume").asLong();
            Long maxDeduction = json.get("maxDeduction").asLong();
            Long maxConsume=json.get("maxConsume").asLong();
            String note = json.get("note").asText();
            List<TicketBatch> batches = new ArrayList<TicketBatch>();
            JsonNode jBatches = json.get("batches");
            if (jBatches != null) {
                Iterator iterator = jBatches.iterator();
                while (iterator.hasNext()) {
                    JsonNode jBatch = (JsonNode)iterator.next();
                    TicketBatch batch = JPA.em().find(TicketBatch.class, jBatch.get("id").asLong());
                    batches.add(batch);
                }
            }
            // check batch.issuedBy is member of company.associateCompanies
            Iterator iterator = batches.iterator();
            while (iterator.hasNext()) {
                TicketBatch batch = (TicketBatch)iterator.next();
                if (batch.company.id != manager.company.id) {
                    Query query = JPA.em().createNamedQuery("checkAssociateCompany");
                    query.setParameter("company", manager.company);
                    query.setParameter("issuedBy", batch.company);
                    List<Company> companies = query.getResultList();
                    if (companies.isEmpty()) {
                        return badRequest();
                    }
                }
            }

            JsonNode discountNode = json.get("discountType");
            if (discountNode != null) {     // discount
                int discountType = discountNode.asInt();
                if (discountType == 0) {
                    double convertRate = json.get("convertRate").asDouble();
                    newRule = new UseDiscountRule(ruleName, member, maxConsume,leastConsume, maxDeduction, batches, convertRate, note);
                } else {
                    int discount = json.get("discount").asInt();
                    newRule = new UseDiscountRule(ruleName, member, maxConsume,leastConsume, maxDeduction, batches, discount, note);
                }
            } else {
                int rate = json.get("rate").asInt();
                newRule = new UseDeductionRule(ruleName, member, maxConsume,leastConsume, maxDeduction, batches, rate, note);
            }

            JPA.em().persist(newRule);
        }
        return ok(toJson(newRule));
    }

    @Transactional
    public static Result postAssociateCompanies(Long managerId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Long companyId = json.get("id").asLong();
        if (companyId.longValue() == manager.company.id) {
            return badRequest("can not add myself!");
        }
        Company company = JPA.em().find(Company.class, companyId);
        manager.company.associateCompanies.add(company);
        JPA.em().merge(manager.company);
        return ok(toJson(company));
    }

    @Transactional
    public static Result deleteAssociateCompanies(Long managerId, Long companyId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);
        manager.company.associateCompanies.remove(company);
        JPA.em().merge(manager.company);
        return ok(toJson(company));
    }

    /**
    * query from all customers, even customers not my customer
    * maybe holding some tickets can be used in my company
    */
    @Transactional
    public static Result getCustomers(Long managerId, String customerName)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", customerName);
        List<Customer> customers = query.getResultList();
        return ok(toJson(customers));
    }

    @Transactional
    public static Result postCustomers(Long managerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Customer customer = null;

        String name = json.get("name").asText();
        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", name);
        List<Customer> customers = query.getResultList();
        if (customers.isEmpty()) {
            String password = String.format("%06d", new Random().nextInt(999999));
            new Message(name, password, "90982").start();
            customer = new Customer(name, password);
            JPA.em().persist(customer);
            return ok(toJson(customer));
        } else {
            return ok(toJson(customers.get(0)));
        }
    }

    @Transactional
    public static Result getFreeTickets(Long managerId, Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (noBalance(manager.company)) {
            return forbidden();
        }

        Query query = JPA.em().createNamedQuery("findCustomerFreeTicketsValidInCompany");
        query.setParameter("customerId", customerId);
        query.setParameter("company", manager.company);
        ArrayNode jtickets = new ArrayNode(JsonNodeFactory.instance);
        List<FreeTicket> tickets = query.getResultList();
        Iterator iterator = tickets.iterator();
        while (iterator.hasNext()) {
            FreeTicket ticket = (FreeTicket)iterator.next();
            JsonNode jticket = toJson(ticket);
            ObjectNode o = (ObjectNode)jticket;
            HashMap batchMap = new HashMap();
            batchMap.put("id", ticket.batch.id);
            batchMap.put("name", ticket.batch.name);
            HashMap companyMap = new HashMap();
            companyMap.put("id", ticket.batch.company.id);
            companyMap.put("name", ticket.batch.company.name);
            batchMap.put("company", companyMap);
            o.put("batch", toJson(batchMap));
            jtickets.add(jticket);
        }
        return ok(jtickets);
    }

    @Transactional
    public static Result getGrouponTickets(Long managerId, Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (noBalance(manager.company)) {
            return forbidden();
        }

        Query query = JPA.em().createNamedQuery("findCustomerGrouponTicketsValidInCompany");
        query.setParameter("customerId", customerId);
        query.setParameter("company", manager.company);
        ArrayNode jtickets = new ArrayNode(JsonNodeFactory.instance);
        List<GrouponTicket> tickets = query.getResultList();
        Iterator iterator = tickets.iterator();
        while (iterator.hasNext()) {
            GrouponTicket ticket = (GrouponTicket)iterator.next();
            JsonNode jticket = toJson(ticket);
            ObjectNode o = (ObjectNode)jticket;
            HashMap batchMap = new HashMap();
            batchMap.put("id", ticket.batch.id);
            batchMap.put("name", ticket.batch.name);
            HashMap companyMap = new HashMap();
            companyMap.put("id", ticket.batch.company.id);
            companyMap.put("name", ticket.batch.company.name);
            batchMap.put("company", companyMap);
            o.put("batch", toJson(batchMap));
            jtickets.add(jticket);
        }
        return ok(jtickets);
    }
    private static Object caculateLeftTime(Calendar c){
        Date d1=new Date();
        Date d2=c.getTime();
        long between=(d2.getTime() - d1.getTime())/1000;
        long day1=between/(24*3600);
        long hour1=between%(24*3600)/3600;
        long minute1=between%3600/60;
        long second1=between%60/60;
        HashMap time=new HashMap();
        time.put("day",day1);
        time.put("hour",hour1);
        time.put("minute",minute1);
        time.put("second",minute1);
        return time;

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

    private static boolean noBalance(Company company)
    {
        if (company.balance <= 0) {
            return true;
        } else {
            return false;
        }
    }

    @Transactional
    public static Result postConsumes(Long managerId, Long customerId) throws Exception {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Customer customer = JPA.em().find(Customer.class, customerId);
        Long amount = json.get("amount").asLong();

        // use tickets
        List<Ticket> tickets = new ArrayList<Ticket>();
        JsonNode useFreeNode = json.get("usefree");
        JsonNode useGrouponNode = json.get("usegroupon");
        Query query = JPA.em().createNamedQuery("queryCompanyAccountConfig");
        query.setParameter("company", manager.company);
        List<CompanyAccountConfig> companyAccountConfigs = query.getResultList();
        CompanyAccountConfig companyAccountConfig = null;
        Long number = 0L;
        boolean flag = true;
        if (companyAccountConfigs.isEmpty()) {
            flag = false;
        } else {
            companyAccountConfig = companyAccountConfigs.get(0);
            number = manager.company.balance / Long.parseLong(companyAccountConfig.accountValue);
        }

        Long netPaid = 0L;
        int type = 0;
        if (useFreeNode != null) {
            String token = useFreeNode.get("token").asText();
            query = JPA.em().createNamedQuery("queryConfirmCodeToken");
            query.setParameter("manager", manager);
            query.setParameter("customer", customer);
            query.setParameter("token", token);
            List<ConfirmCode> ccs = query.getResultList();
            if (ccs.isEmpty()) {
                return badRequest();
            }

            Long useRuleId = useFreeNode.get("useRuleId").asLong();
            UseRule useRule = JPA.em().find(UseRule.class, useRuleId);
            if (amount.compareTo(useRule.leastConsume) < 0) {
                return badRequest();
            }
            JsonNode ticketsNode = useFreeNode.get("tickets");
            Iterator itTickets = ticketsNode.iterator();
            while (itTickets.hasNext()) {
                JsonNode ticketNode = (JsonNode)itTickets.next();
                Long ticketId = ticketNode.get("ticketId").asLong();
                Ticket ticket = JPA.em().find(Ticket.class, ticketId);
                if (ticket.state != 0) {
                    return badRequest();
                }
                if (ticket instanceof DeductionTicket) {
                    if (useRule instanceof UseDeductionRule) {
                    } else {
                        return badRequest();
                    }
                } else if (ticket instanceof DiscountTicket) {
                    if (useRule instanceof UseDiscountRule) {
                    } else {
                        return badRequest();
                    }
                } else {
                    Logger.error("only deduction and discount ticket here");
                    return badRequest();
                }

                boolean found = false;
                Iterator itBatches = useRule.batches.iterator();
                while (itBatches.hasNext()) {
                    TicketBatch batch = (TicketBatch)itBatches.next();
                    if (((FreeTicket)ticket).batch.id == batch.id) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return badRequest();
                }
            }

            if (number < useFreeNode.get("tickets").size() && flag == false) {
                return forbidden("balance not enough or no config");
            }

            // AGAIN: calculate and update ticket
            Long totalDeduction = 0L;
            itTickets = ticketsNode.iterator();
            while (itTickets.hasNext()) {
                JsonNode ticketNode = (JsonNode)itTickets.next();
                Long ticketId = ticketNode.get("ticketId").asLong();
                Ticket ticket = JPA.em().find(Ticket.class, ticketId);

                Calendar calendar = Calendar.getInstance();
                query = JPA.em().createNamedQuery("queryStaticsAcceptWithRule");
                query.setParameter("useRule", useRule);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                query.setParameter("date", calendar.getTime());
                query.setParameter("usedIn", manager.company);
                query.setParameter("issuedBy", ticket.company);
                StaticsAccept staticsAccept = null;
                List<StaticsAccept> staticsAccepts = query.getResultList();
                if (staticsAccepts.isEmpty()) {
                    staticsAccept = new StaticsAccept(calendar.getTime(), manager.company, ticket.company, useRule);
                } else {
                    staticsAccept = staticsAccepts.get(0);
                }
                staticsAccept.count += 1;
                JPA.em().persist(staticsAccept);
                ticket.staticsAccept = staticsAccept;

                if (useRule instanceof UseDeductionRule) {
                    int rate = ((UseDeductionRule)useRule).rate;
                    Long deduction = ((DeductionTicket)ticket).deduction * rate / 100;
                    if (useRule.maxDeduction.compareTo(Long.valueOf(totalDeduction + deduction)) < 0 || amount.compareTo(Long.valueOf(totalDeduction + deduction)) < 0) {
                        totalDeduction = Math.min(useRule.maxDeduction, amount);
                        ticket.state = 1;
                        break;
                    } else {
                        totalDeduction += deduction;
                        ticket.state = 1;
                    }
                } else if (useRule instanceof UseDiscountRule) {
                    if (((UseDiscountRule)useRule).discountType == 0) {
                        totalDeduction = Math.round(amount - amount * ((DiscountTicket)ticket).discount * ((UseDiscountRule)useRule).convertRate / 100);
                    } else {
                        totalDeduction = amount - amount * ((UseDiscountRule)useRule).discount / 100;
                    }
                    if (totalDeduction.compareTo(useRule.maxDeduction) > 0) {
                        totalDeduction = useRule.maxDeduction;
                    }
                    if (((DiscountTicket)ticket).once) {
                        ticket.state = 1;
                    }
                    //break;
                } else {
                    Logger.info("useRule type error!");
                    throw new Exception("useRule type error!");
                }
                tickets.add(ticket);
            }
            netPaid = amount - totalDeduction;
            type = 0;
        } else if (useGrouponNode != null) {
            String token = useGrouponNode.get("token").asText();
            query = JPA.em().createNamedQuery("queryConfirmCodeToken");
            query.setParameter("manager", manager);
            query.setParameter("customer", customer);
            query.setParameter("token", token);
            List<ConfirmCode> ccs = query.getResultList();
            if (ccs.isEmpty()) {
                return badRequest();
            }

            if (number < useGrouponNode.get("tickets").size() && flag == false) {
               return forbidden("balance not enough or no config");
            }

            JsonNode ticketsNode = useGrouponNode.get("tickets");
            Iterator itTickets = ticketsNode.iterator();
            Long totalDeduction = 0L;
            Long totalCost = 0L;
            while (itTickets.hasNext()) {
                JsonNode ticketNode = (JsonNode)itTickets.next();
                Long ticketId = ticketNode.get("ticketId").asLong();
                Ticket ticket = JPA.em().find(Ticket.class, ticketId);
                if (ticket.state != 0) {
                    return badRequest();
                }
                if (!(ticket instanceof GrouponTicket)) {
                    return badRequest();
                }
                ticket.state = 1;
                totalDeduction += ((GrouponTicket)ticket).deduction;
                totalCost += ((GrouponTicket)ticket).cost;
                tickets.add(ticket);
            }
            if (amount.compareTo(totalDeduction) > 0) {
                netPaid = amount - totalDeduction + totalCost;
            } else {
                netPaid = totalCost;
            }
            type = 1;
        } else {
            netPaid = amount;
        }

        Calendar calendar = Calendar.getInstance();
        query = JPA.em().createNamedQuery("queryStaticsCustomer");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsCustomer staticsCustomer = null;
        List<StaticsCustomer> staticsCustomers = query.getResultList();
        if (staticsCustomers.isEmpty()) {
            staticsCustomer = new StaticsCustomer(calendar.getTime(), manager.company);
        } else {
            staticsCustomer = staticsCustomers.get(0);
        }

        query = JPA.em().createNamedQuery("queryStaticsConsume");
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsConsume staticsConsume = null;
        List<StaticsConsume> staticsConsumes = query.getResultList();
        if (staticsConsumes.isEmpty()) {
            staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), new Long(tickets.size()), 0L, amount, netPaid, 0L, 0L, 0L,0L);
        } else {
            staticsConsume = staticsConsumes.get(0);
            staticsConsume.uses += new Long(tickets.size());
            staticsConsume.amount += amount;
            staticsConsume.netPaid += netPaid;
        }
        JPA.em().persist(staticsConsume);

        query = JPA.em().createNamedQuery("queryCompanyCustomer");
        query.setParameter("company", manager.company);
        query.setParameter("customer", customer);
        CompanyCustomer companyCustomer = null;
        List<CompanyCustomer> companyCustomers = query.getResultList();
        if (companyCustomers.isEmpty()) {
            companyCustomer = new CompanyCustomer(manager.company, customer, netPaid, staticsCustomer);
            JPA.em().persist(companyCustomer);
            staticsCustomer.regs += 1;
        } else {
            companyCustomer = companyCustomers.get(0);
            companyCustomer.amount += netPaid;
            companyCustomer.times += 1;
            JPA.em().persist(companyCustomer);
        }

        staticsCustomer.amount += amount;
        staticsCustomer.netPaid += netPaid;
        JPA.em().persist(staticsCustomer);

        Consume consume = new Consume(companyCustomer, staticsConsume, amount, netPaid, 0L, type);
        JPA.em().persist(consume);
        if (customer.dynamicConfirmCode) {
            JPA.em().persist(customer.changeConfirmCode());
        }
        ConsumeLog record = new ConsumeLog(manager, consume);
        JPA.em().persist(record);

        Iterator itTickets = tickets.iterator();
        while (itTickets.hasNext()) {
            Ticket ticket = (Ticket)itTickets.next();
            ticket.used = consume;
            JPA.em().persist(ticket);

            if (companyAccountConfig.isAccount == true && companyAccountConfig.useticketPoint == true) {
                JPA.em().persist(new CompanyAccount(manager.company, ticket, 0 - Long.parseLong(companyAccountConfig.accountValue), customer.name));
            }

            ConsumeTicket consumeTicket = new ConsumeTicket(consume, ticket, 0);
            JPA.em().persist(consumeTicket);
        }

        ConsumeInfo consumeInfo = new ConsumeInfo(customer, companyCustomer.company, consume);
        JPA.em().persist(consumeInfo);
        JsonNode companyProductsNode = json.get("companyProducts");
        if (companyProductsNode != null) {
            // iterate companyProducts to create CompanyProductConsume
            //  and iterate companyProduct.postSaleServices to create CompanyProductConsumeService
            //ArrayNode arrayNode = (ArrayNode)companyProductsNode;
            Iterator iterator = companyProductsNode.iterator();
            while (iterator.hasNext()) {
                JsonNode companyProductNode = (JsonNode)iterator.next();
                Long companyProductId = companyProductNode.get("companyProductId").asLong();
                int quantity = companyProductNode.get("quantity").asInt();
                String antiFakeNote = companyProductNode.get("antifakenote").asText();
                CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
                CompanyProductConsume companyProductConsume = new CompanyProductConsume(companyProduct, consume, quantity);
                if (antiFakeNote != null) {
                    companyProductConsume.antiFakeNote = antiFakeNote;
                }
                if (companyProduct.source != null) {
                    query = JPA.em().createNamedQuery("queryCompanyCustomer");
                    query.setParameter("company", companyProduct.source.company);
                    query.setParameter("customer", customer);
                    List<CompanyCustomer> companyCustomerList = query.getResultList();
                    if (companyCustomerList.isEmpty()) {
                        JPA.em().persist(new CompanyCustomer(companyProduct.source.company, customer));
                        Logger.info("----");
                    }
                }
                List<CompanyProductService> services = companyProduct.postSaleServices;
                Iterator it = services.iterator();
                while (it.hasNext()) {
                    CompanyProductService companyProductService = (CompanyProductService)it.next();
                    if (companyProductService.serviceType == 0) {
                        CompanyProductConsumeService service = new CompanyProductConsumeService(companyProductConsume, companyProductService.statement);
                        if (companyProductService.inherit != null) {
                            service.inherit = companyProductService.inherit.companyProduct.company;
                        }
                        JPA.em().persist(service);
                    } else {
                        CompanyProductConsumeService service = new CompanyProductConsumeService(companyProductConsume, companyProductService.statement, companyProductService.validDays);
                        if (companyProductService.inherit != null) {
                            service.inherit = companyProductService.inherit.companyProduct.company;
                        }
                        JPA.em().persist(service);
                    }
                }
                List<AntiFakePicture> pictures = new ArrayList<>();
                if (companyProductNode.get("picture") != null && companyProductNode.get("picture").asLong() != 0) {
                    Long picture = companyProductNode.get("picture").asLong();
                    AntiFakePicture antiFakePicture = JPA.em().find(AntiFakePicture.class, picture);
                    antiFakePicture.companyProductConsume = companyProductConsume;
                    JPA.em().persist(antiFakePicture);
                    pictures.add(antiFakePicture);
                }

                if (companyProductNode.get("picture1") != null && companyProductNode.get("picture1").asLong() != 0) {
                    Long picture1 = companyProductNode.get("picture1").asLong();
                    AntiFakePicture antiFakePicture1 = JPA.em().find(AntiFakePicture.class, picture1);
                    antiFakePicture1.companyProductConsume = companyProductConsume;
                    JPA.em().persist(antiFakePicture1);
                    pictures.add(antiFakePicture1);
                }

                if (companyProductNode.get("picture2") != null && companyProductNode.get("picture2").asLong() != 0) {
                    Long picture2 = companyProductNode.get("picture2").asLong();
                    AntiFakePicture antiFakePicture2 = JPA.em().find(AntiFakePicture.class, picture2);
                    antiFakePicture2.companyProductConsume = companyProductConsume;
                    JPA.em().persist(antiFakePicture2);
                    pictures.add(antiFakePicture2);

                }
                Logger.info(String.valueOf(pictures.size()));
                if (!pictures.isEmpty()) {
                    companyProductConsume.antiFakePictures.addAll(pictures);
                    Logger.info("add antifakepictures success");
                }
                JPA.em().persist(companyProductConsume);
            }
        }

        JsonNode bookingsNode = json.get("bookings");
        if (bookingsNode != null) {
            Iterator iterator = bookingsNode.iterator();
            while (iterator.hasNext()) {
                JsonNode bookingNode = (JsonNode)iterator.next();
                Long bookingId = bookingNode.get("bookingId").asLong();
                Booking booking = JPA.em().find(Booking.class, bookingId);
                booking.consume = consume;
                booking.status = 4;
                JPA.em().persist(booking);
            }
        }
        return ok(toJson(consume));
    }

    @Transactional
    public static Result postQuickConsume(Long managerId, Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Customer customer = JPA.em().find(Customer.class, customerId);
        Long amount = json.get("amount").asLong();

        Long netPaid = amount;
        Logger.info(""+request().body().asJson());
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsCustomer");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsCustomer staticsCustomer = null;
        List<StaticsCustomer> staticsCustomers = query.getResultList();
        if (staticsCustomers.isEmpty()) {
            staticsCustomer = new StaticsCustomer(calendar.getTime(), manager.company);
        } else {
            staticsCustomer = staticsCustomers.get(0);
        }

        query = JPA.em().createNamedQuery("queryStaticsConsume");
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsConsume staticsConsume = null;
        List<StaticsConsume> staticsConsumes = query.getResultList();
        if (staticsConsumes.isEmpty()) {
            staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), new Long(0), 0L, amount, netPaid, 0L, 0L, 0L,0L);
        } else {
            staticsConsume = staticsConsumes.get(0);
            staticsConsume.uses += new Long(0);
            staticsConsume.amount += amount;
            staticsConsume.netPaid += netPaid;
        }
        JPA.em().persist(staticsConsume);

        query = JPA.em().createNamedQuery("queryCompanyCustomer");
        query.setParameter("company", manager.company);
        query.setParameter("customer", customer);
        CompanyCustomer companyCustomer = null;
        List<CompanyCustomer> companyCustomers = query.getResultList();
        if (companyCustomers.isEmpty()) {
            companyCustomer = new CompanyCustomer(manager.company, customer, netPaid, staticsCustomer);
            JPA.em().persist(companyCustomer);
            staticsCustomer.regs += 1;
            Logger.info("===isEmpty()===="+manager.company.id);
        } else {
            companyCustomer = companyCustomers.get(0);
            companyCustomer.amount += netPaid;
            companyCustomer.times += 1;
            Logger.info("====not Empty===="+companyCustomer.company.id);
            JPA.em().persist(companyCustomer);
        }

        staticsCustomer.amount += amount;
        staticsCustomer.netPaid += netPaid;
        JPA.em().persist(staticsCustomer);

        Consume consume = new Consume(companyCustomer, staticsConsume, amount, netPaid, 0L, 0);
        JPA.em().persist(consume);
        if (customer.dynamicConfirmCode) {
            JPA.em().persist(customer.changeConfirmCode());
        }
        ConsumeLog record = new ConsumeLog(manager, consume);
        JPA.em().persist(record);

        ConsumeInfo consumeInfo = new ConsumeInfo(customer, companyCustomer.company, consume);
        JPA.em().persist(consumeInfo);
        JsonNode bookingsNode = json.get("bookings");
        if(bookingsNode !=null){
            Iterator bookingsIterator = bookingsNode.iterator();
            while (bookingsIterator.hasNext()){
                JsonNode bookingNode = (JsonNode)bookingsIterator.next();
                Long bookingId = Long.parseLong(bookingNode.asText());
                Booking booking = JPA.em().find(Booking.class,bookingId);
                if(booking == null){
                    return badRequest("invalid bookingId");
                }else {
                    booking.status = 4;
                }
                Logger.info(toJson(booking)+"");
                JPA.em().persist(booking);
            }
        }
        JsonNode companyProductsNode = json.get("companyProducts");
        if (companyProductsNode != null) {
            Iterator iterator = companyProductsNode.iterator();
            while (iterator.hasNext()) {
                JsonNode companyProductNode = (JsonNode)iterator.next();
                Long companyProductId = companyProductNode.get("companyProductId").asLong();
                int quantity = companyProductNode.get("quantity").asInt();
                String antiFakeNote = companyProductNode.get("antifakenote").asText();
                CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
                if(companyProduct == null){
                    return badRequest("invalid companyProductId ");
                }
                CompanyProductConsume companyProductConsume = new CompanyProductConsume(companyProduct, consume, quantity);
                if (antiFakeNote != null) {
                    companyProductConsume.antiFakeNote = antiFakeNote;
                }

                if (companyProduct.source != null) {
                    Query querySource = JPA.em().createNamedQuery("queryCompanyCustomer");
                    querySource.setParameter("company",companyProduct.source.company);
                    querySource.setParameter("customer",customer);
                    List<CompanyCustomer> sourceCompanyCustomers = querySource.getResultList();
                    if(sourceCompanyCustomers.isEmpty()){
                        JPA.em().persist(new CompanyCustomer(companyProduct.source.company, customer));
                    }
                }
                List<CompanyProductService> services = companyProduct.postSaleServices;
                Iterator it = services.iterator();
                while (it.hasNext()) {
                    CompanyProductService companyProductService = (CompanyProductService)it.next();
                    if (companyProductService.serviceType == 0) {
                        CompanyProductConsumeService service = new CompanyProductConsumeService(companyProductConsume, companyProductService.statement);
                        if (companyProductService.inherit != null) {
                            service.inherit = companyProductService.inherit.companyProduct.company;
                        }
                        JPA.em().persist(service);
                    } else {
                        CompanyProductConsumeService service = new CompanyProductConsumeService(companyProductConsume, companyProductService.statement, companyProductService.validDays);
                        if (companyProductService.inherit != null) {
                            service.inherit = companyProductService.inherit.companyProduct.company;
                        }
                        JPA.em().persist(service);
                    }
                }
                List<AntiFakePicture> pictures = new ArrayList<>();
                if (companyProductNode.get("picture") != null && companyProductNode.get("picture").asLong() != 0) {
                    Long picture = companyProductNode.get("picture").asLong();
                    AntiFakePicture antiFakePicture = JPA.em().find(AntiFakePicture.class, picture);
                    antiFakePicture.companyProductConsume = companyProductConsume;
                    JPA.em().persist(antiFakePicture);
                    pictures.add(antiFakePicture);
                }

                if (companyProductNode.get("picture1") != null && companyProductNode.get("picture1").asLong() != 0) {
                    Long picture1 = companyProductNode.get("picture1").asLong();
                    AntiFakePicture antiFakePicture1 = JPA.em().find(AntiFakePicture.class, picture1);
                    antiFakePicture1.companyProductConsume = companyProductConsume;
                    JPA.em().persist(antiFakePicture1);
                    pictures.add(antiFakePicture1);
                }

                if (companyProductNode.get("picture2") != null && companyProductNode.get("picture2").asLong() != 0) {
                    Long picture2 = companyProductNode.get("picture2").asLong();
                    AntiFakePicture antiFakePicture2 = JPA.em().find(AntiFakePicture.class, picture2);
                    antiFakePicture2.companyProductConsume = companyProductConsume;
                    JPA.em().persist(antiFakePicture2);
                    pictures.add(antiFakePicture2);

                }
                Logger.info(String.valueOf(pictures.size()));
                if (!pictures.isEmpty()) {
                    companyProductConsume.antiFakePictures.addAll(pictures);
                    Logger.info("add antifakepictures success");
                }
                JPA.em().persist(companyProductConsume);

            }
        }

        return ok(toJson(consume));
    }

    @Transactional
    public static Result updateIntegralStrategy(Long managerId, Long integralStrategyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        IntegralStrategy integralStrategy = JPA.em().find(IntegralStrategy.class, integralStrategyId);

        JsonNode isEnabledNode = json.get("isEnabled");
        integralStrategy.isEnabled = isEnabledNode.asBoolean();
        JPA.em().persist(integralStrategy);
        return ok(toJson(integralStrategy));
    }

    @Transactional
    public static Result getIntegralStrategy(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryEnabledIntegralStrategies");
        query.setParameter("company", manager.company);
        List<IntegralStrategy> integralStrategies = query.getResultList();
        return ok(toJson(integralStrategies));

    }

    @Transactional
    public static Result postIntegralStrategy(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode json = request().body().asJson();
        String name = json.get("name").asText();
        Long discount = json.get("discount").asLong();
        Long minNumber = json.get("minNumber").asLong();
        Long maxNumber = json.get("maxNumber").asLong();
        String note = json.get("note").asText();
        IntegralStrategy integralStrategy = new IntegralStrategy(manager.company, discount, minNumber, maxNumber, note, name);
        JPA.em().persist(integralStrategy);
        return ok();

    }

    @Transactional
    public static Result postDirectTickets(Long managerId, Long customerId) {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Customer customer = JPA.em().find(Customer.class, customerId);
        if (customer == null) {
            return notFound();
        } else {
//            Query query = JPA.em().createNamedQuery("queryCompanyCustomerOfCustomer");
//            query.setParameter("customer", customer);
           Query query = JPA.em().createNamedQuery("queryCompanyCustomer");
            query.setParameter("customer",customer);
            query.setParameter("company",manager.company);
            List<CompanyCustomer> companyCustomers = query.getResultList();
            if (companyCustomers.isEmpty()) {
                CompanyCustomer companyCustomer = new CompanyCustomer(manager.company, customer);
                JPA.em().persist(companyCustomer);
                Logger.info("===direct reg companyCustomer==="+companyCustomer.id);
            }
            Logger.info("=====direct ======");
        }
        Long batchId = json.get("batchId").asLong();
        int count = json.get("count").asInt();
        TicketBatch batch = JPA.em().find(TicketBatch.class, batchId);
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsDirectIssue");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("batch", batch);
        StaticsDirectIssue staticsDirectIssue = null;
        List<StaticsDirectIssue> staticsDirectIssues = query.getResultList();
        if (staticsDirectIssues.isEmpty()) {
            staticsDirectIssue = new StaticsDirectIssue(calendar.getTime(), batch);
        } else {
            staticsDirectIssue = staticsDirectIssues.get(0);
        }

        Direct direct = new Direct();
        List<Ticket> tickets = new ArrayList<Ticket>();
        Ticket ticket = null;
        if (batch instanceof DeductionBatch) {
            if (batch.leftNumber >= count) {
                for (int i = 0; i < count; ++i) {
                    ticket = new DeductionTicket(customer, manager.company, manager, staticsDirectIssue, (DeductionBatch)batch);
                    batch.current += 1;
                    staticsDirectIssue.amount += ((DeductionTicket)ticket).deduction;
                    ticket.directIssue = direct;
                    direct.addIssue(ticket, customer.name);
                    tickets.add(ticket);
                    staticsDirectIssue.count += 1;
                }
            } else {
                batch.isEnabled = false;
                JPA.em().persist(batch);
                return badRequest("Leftnumber of this batch is empty");
            }
        } else if (batch instanceof DiscountBatch) {
            if (batch.leftNumber >= count) {
                for (int i = 0; i < count; ++i) {
                    ticket = new DiscountTicket(customer, manager.company, manager, staticsDirectIssue, (DiscountBatch)batch);
                    batch.current += 1;
                    direct.addIssue(ticket, customer.name);
                    ticket.directIssue = direct;
                    tickets.add(ticket);
                    staticsDirectIssue.count += 1;
                }

            } else {
                batch.isEnabled = false;
                JPA.em().persist(batch);
                return badRequest("Leftnumber of this batch is empty");
            }

        } else if (batch instanceof GrouponBatch) {
            if (batch.leftNumber >= count) {
                for (int i = 0; i < count; ++i) {
                    ticket = new GrouponTicket(customer, manager.company, manager, staticsDirectIssue, (GrouponBatch)batch);
                    batch.current += 1;
                    staticsDirectIssue.amount += ((GrouponTicket)ticket).deduction;
                    ticket.directIssue = direct;
                    direct.addIssue(ticket, customer.name);
                    tickets.add(ticket);
                    staticsDirectIssue.count += 1;
                }
            } else {
                batch.isEnabled = false;
                JPA.em().persist(batch);
                return badRequest("Leftnumber of this batch is empty");
            }
        } else if (batch instanceof CompanyProductBatch) {
            if (batch.leftNumber >= count) {
                for (int i = 0; i < count; ++i) {
                    ticket = new CompanyProductTicket(customer, manager.company, manager, staticsDirectIssue, (CompanyProductBatch)batch);
                    batch.current += 1;
                    ticket.directIssue = direct;
                    direct.addIssue(ticket, customer.name);
                    tickets.add(ticket);
                    staticsDirectIssue.count += 1;
                }
            } else {
                batch.isEnabled = false;
                JPA.em().persist(batch);
                return badRequest("Leftnumber of this batch is empty");
            }
        } else {
            return badRequest();
        }
        JPA.em().persist(staticsDirectIssue);
        JPA.em().persist(direct);

        DirectInfo info = new DirectInfo(customer, manager.company, direct);
        DirectLog record = new DirectLog(manager, direct);
        JPA.em().persist(info);
        JPA.em().persist(record);
        batch.leftNumber = batch.maxNumber - batch.current;
        JPA.em().persist(batch);

        return ok(toJson(tickets));
    }

    @Transactional
    public static Result putDirectTickets(Long managerId, Long customerId) throws Exception {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Customer customer = JPA.em().find(Customer.class, customerId);
        if (customer == null) {
            return notFound();
        }
        JsonNode ticketsNode = json.get("tickets");
        Query query = JPA.em().createNamedQuery("queryCompanyAccountConfig");
        query.setParameter("company", manager.company);
        List<CompanyAccountConfig> companyAccountConfigs = query.getResultList();
        CompanyAccountConfig companyAccountConfig = null;
        if (companyAccountConfigs.isEmpty()) {
            return forbidden("no config");
        } else {
            companyAccountConfig = companyAccountConfigs.get(0);
        }
        Long number = manager.company.balance / Long.parseLong(companyAccountConfig.accountValue);
        if (number < json.get("tickets").size()) {
            return forbidden("balance not enough");
        }

        // use tickets
        List<Ticket> tickets = new ArrayList<Ticket>();

        String token = json.get("token").asText();
        query = JPA.em().createNamedQuery("queryConfirmCodeToken");
        query.setParameter("manager", manager);
        query.setParameter("customer", customer);
        query.setParameter("token", token);
        List<ConfirmCode> ccs = query.getResultList();
        if (ccs.isEmpty()) {
            return badRequest();
        }

        Direct direct = new Direct();


        Iterator iterator = ticketsNode.iterator();
        while (iterator.hasNext()) {
            JsonNode ticketNode = (JsonNode)iterator.next();
            Long ticketId = ticketNode.get("ticketId").asLong();
            Ticket ticket = JPA.em().find(Ticket.class, ticketId);
            if (ticket.state != 0) {
                return badRequest("state error or other companys ticket");
            }
            ticket.state = 1;
            ticket.directUse = direct;
            direct.addUse(ticket, customer.name);
            tickets.add(ticket);
        }
        JPA.em().persist(direct);
        Iterator itTickets = tickets.iterator();
        while (itTickets.hasNext()) {
            Ticket ticket = (Ticket)itTickets.next();
            companyAccountConfig = companyAccountConfigs.get(0);
            if (companyAccountConfig.isAccount == true && companyAccountConfig.useticketPoint == true) {
                JPA.em().persist(new CompanyAccount(manager.company, ticket, 0 - Long.parseLong(companyAccountConfig.accountValue), customer.name));
            }
        }

        DirectInfo info = new DirectInfo(customer, manager.company,direct);
        DirectLog record = new DirectLog(manager, direct);
        JPA.em().persist(info);
        JPA.em().persist(record);
        return ok(toJson(tickets));
    }

    @Transactional
    public static Result postTickets(Long managerId, Long companyCustomerId) {

        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Long consumeId = json.get("consumeId").asLong();
        Long ruleId = json.get("ruleId").asLong();
        Long issues = 0L;
        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        Consume consume = JPA.em().find(Consume.class, consumeId);
        IssueRule rule = JPA.em().find(IssueRule.class, ruleId);

        if (consume.netPaid.compareTo(rule.startingAmount) < 0 || consume.netPaid.compareTo(rule.endAmount) >= 0) {
            return badRequest("the netPaid is not between startingAmount and endAmount of issueRule");
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsIssue");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("issueRule", rule);
        StaticsIssue staticsIssue = null;
        List<StaticsIssue> staticsIssues = query.getResultList();
        if (staticsIssues.isEmpty()) {
            staticsIssue = new StaticsIssue(calendar.getTime(), rule);
        } else {
            staticsIssue = staticsIssues.get(0);
        }

        List<Ticket> tickets = new ArrayList<Ticket>();
        Ticket ticket = null;
        if (rule.batch instanceof DeductionBatch) {
            //long issueCount = consume.netPaid / rule.startingAmount;
            long issueCount = 1L;
            if (issueCount <= ((DeductionBatch) rule.batch).leftNumber) {
                ((DeductionBatch) rule.batch).current += issueCount;
                ((DeductionBatch) rule.batch).leftNumber = ((DeductionBatch) rule.batch).maxNumber - ((DeductionBatch) rule.batch).current;
                for (long i = 0l; i < issueCount; ++i) {
                    ticket = new DeductionTicket(companyCustomer.customer, consume, rule, companyCustomer.company, manager, staticsIssue, (DeductionBatch)rule.batch);
                    staticsIssue.amount += ((DeductionTicket)ticket).deduction;
                    JPA.em().persist(ticket);
                    tickets.add(ticket);
                    staticsIssue.count += 1;
                }
            } else {
                rule.batch.isEnabled = false;
                JPA.em().persist(rule.batch);
                return badRequest("Leftnumber of this batch is empty");
            }

        } else if (rule.batch instanceof DiscountBatch) {
            if (((DiscountBatch) rule.batch).leftNumber != 0) {
                JPA.em().persist(consume);
                ticket = new DiscountTicket(companyCustomer.customer, consume, rule, companyCustomer.company, manager, staticsIssue, (DiscountBatch)rule.batch);
                ((DiscountBatch) rule.batch).current += 1;
                ((DiscountBatch) rule.batch).leftNumber = ((DiscountBatch) rule.batch).maxNumber - ((DiscountBatch) rule.batch).current;
                JPA.em().persist(ticket);
                tickets.add(ticket);
                staticsIssue.count += 1;
            } else {
                rule.batch.isEnabled = false;
                JPA.em().persist(rule.batch);
                return badRequest("Leftnumber of this batch is empty");
            }

        } else {
            return badRequest();
        }
        issues = new Long(tickets.size());

        ConsumeTicket consumeTicket = new ConsumeTicket(consume, ticket, 1);
        JPA.em().persist(consumeTicket);
        JPA.em().persist(staticsIssue);

        calendar = Calendar.getInstance();
        query = JPA.em().createNamedQuery("queryStaticsConsume");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsConsume staticsConsume = null;
        List<StaticsConsume> staticsConsumes = query.getResultList();
        if (staticsConsumes.isEmpty()) {
            staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), 0L, issues, consume.amount, consume.netPaid, 0L, 0L, 0L,0L);
        } else {
            staticsConsume = staticsConsumes.get(0);
            staticsConsume.issues += new Long(tickets.size());
            staticsConsume.amount += consume.amount;
            staticsConsume.netPaid += consume.netPaid;
        }
        JPA.em().persist(staticsConsume);

        return ok(toJson(tickets));
    }

/*
    @Transactional
    public static Result updateTicket(Long managerId, Long ticketId)
    {
        JsonNode json = request().body().asJson();
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Long consumeId = json.get("consumeId").asLong();
        //Long save = json.get("save").asLong();
        Consume consume = JPA.em().find(Consume.class, consumeId);
        Ticket ticket = JPA.em().find(Ticket.class, ticketId);

        Calendar calendar = Calendar.getInstance();
        Query query = null;
        Long useRuleId = json.get("useRuleId").asLong();
        useRule = JPA.em().find(UseRule.class, useRuleId);
        query = JPA.em().createNamedQuery("queryStaticsAcceptWithRule");
        query.setParameter("useRule", useRule);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("usedIn", manager.company);
        query.setParameter("issuedBy", ticket.company);
        StaticsAccept staticsAccept = null;
        List<StaticsAccept> staticsAccepts = query.getResultList();
        if (staticsAccepts.isEmpty()) {
            staticsAccept = new StaticsAccept(calendar.getTime(), manager.company, ticket.company, useRule);
        } else {
            staticsAccept = staticsAccepts.get(0);
        }
        staticsAccept.save += save;
        staticsAccept.count += 1;
        JPA.em().persist(staticsAccept);

        TicketUse ticketUse = new TicketUse(ticket, consume, useRule, save, staticsAccept);
        ticket.addTicketUse(ticketUse);
        int state = json.get("state").asInt();
        ticket.state = state;
        JsonNode leftDeductionNode = json.get("leftDeduction");
        if (leftDeductionNode != null) {
            ((DeductionTicket)ticket).leftDeduction = leftDeductionNode.asLong();
        }

        query = JPA.em().createNamedQuery("queryConfig");
        query.setParameter("configName", "CHARGE_RATE");
        List<TicketConfig> ticketConfigs = query.getResultList();
        if (ticketConfigs.isEmpty()) {
            CompanyAccount account = new CompanyAccount(manager.company, ticketUse, -100L);
            manager.company.addAccount(account);
        } else {
            TicketConfig tc = ticketConfigs.get(0);
            CompanyAccount account = new CompanyAccount(manager.company, ticketUse, 0 - Long.parseLong(tc.configValue));
            manager.company.addAccount(account);
        }

        JPA.em().persist(manager.company);
        JPA.em().persist(ticket);
        return ok(toJson(ticket));
    }
*/

    @Transactional
    public static Result getStaticsCustomers(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsCustomers");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", manager.company);
        List<StaticsCustomer> staticsCustomers = query.getResultList();
        return ok(toJson(staticsCustomers));
    }

    @Transactional
    public static Result getStaticsIssues(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsIssues");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", manager.company);
        List<StaticsIssue> staticsIssues = query.getResultList();
        return ok(toJson(staticsIssues));
    }

    @Transactional
    public static Result getStaticsDirectIssues(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsDirectIssues");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", manager.company);
        List<StaticsDirectIssue> staticsDirectIssues = query.getResultList();
        return ok(toJson(staticsDirectIssues));
    }

    @Transactional
    public static Result getStaticsTicketRequest(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsTicketRequests");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", manager.company);
        List<StaticsTicketRequest> staticsTicketRequests= query.getResultList();
        return ok(toJson(staticsTicketRequests));
    }

    @Transactional
    public static Result getStaticsAcceptsByAccept(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsAcceptsByAccept");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("usedIn", manager.company);
        query.setParameter("issuedBy", manager.company);
        List<StaticsAccept> staticsAccepts = query.getResultList();
        return ok(toJson(staticsAccepts));
    }

    @Transactional
    public static Result getStaticsAcceptsByIssuedCompany(Long managerId, Long issuedCompanyId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Company issuedCompany = JPA.em().find(Company.class, issuedCompanyId);
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsAcceptsByCompany");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("issuedBy", issuedCompany);
        query.setParameter("usedIn", manager.company);

        List<StaticsAccept> staticsAccepts = query.setFirstResult(startIndex).setMaxResults(10).getResultList();
        Map rst = new HashMap();
        rst.put("totalNumbers", query.getResultList().size());
        rst.put("staticsAccept", staticsAccepts);
        return ok(toJson(rst));
    }

    @Transactional
    public static Result getStaticsAcceptsByUsedCompany(Long managerId, Long usedCompanyId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Company usedCompany = JPA.em().find(Company.class, usedCompanyId);
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsAcceptsByCompany");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("usedIn", usedCompany);
        query.setParameter("issuedBy", manager.company);
        List staticsAccepts = query.setFirstResult(startIndex).setMaxResults(10).getResultList();

        Map rst = new HashMap();
        rst.put("totalNumbers", query.getResultList().size());
        rst.put("staticsAccept", staticsAccepts);
        return ok(toJson(rst));
    }

    @Transactional
    public static Result getStaticsAcceptsByIssue(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsAcceptsByIssue");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("issuedBy", manager.company);
        List<StaticsAccept> staticsAccepts = query.getResultList();
        return ok(toJson(staticsAccepts));
    }

    @Transactional
    public static Result getStaticsTransfers(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsTransfers");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", manager.company);
        List<StaticsTransfer> staticsTransfers = query.getResultList();
        return ok(toJson(staticsTransfers));
    }


    @Transactional
    public static Result getCompanyAccounts(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryCompanyAccounts");
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", manager.company);
        List<CompanyAccount> companyAccounts = query.getResultList();
        return ok(toJson(companyAccounts));
    }

    @Transactional
    public static Result getConfirmCode(Long managerId, Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Customer customer = JPA.em().find(Customer.class, customerId);

        HashMap confirmCodeMap = new HashMap();
        confirmCodeMap.put("confirmCode", customer.confirmCode);
        return ok(toJson(confirmCodeMap));
    }

    @Transactional
    public static Result getInitConfirmCode(Long managerId, Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Customer customer = JPA.em().find(Customer.class, customerId);

        Query query = JPA.em().createNamedQuery("queryCompanyAccountConfig");
        query.setParameter("company", manager.company);
        List<CompanyAccountConfig> companyAccountConfigs = query.getResultList();
        CompanyAccountConfig companyAccountConfig = null;
        if (companyAccountConfigs.isEmpty()) {
            return forbidden("no config");
        } else {
            companyAccountConfig = companyAccountConfigs.get(0);
        }
        if (manager.company.balance < 10L) {
            return forbidden("balance not enough");
        }
        if (companyAccountConfig.isAccount == true && companyAccountConfig.confirmcodePoint == true) {
            JPA.em().persist(new CompanyAccount(manager.company, manager, 0 - 10L, customer.name));
        }
        new Message(customer.name, customer.confirmCode, "103501").start();
        return ok();
    }

    @Transactional
    public static Result postConfirmCode(Long managerId, Long customerId) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Customer customer = JPA.em().find(Customer.class, customerId);
        JsonNode json = request().body().asJson();
        String confirmCode = json.get("confirmCode").asText();
        if (confirmCode.equals(customer.confirmCode)) {
            if (customer.dynamicConfirmCode) {
                JPA.em().persist(customer.changeConfirmCode());
            }
            Calendar calendar = Calendar.getInstance();
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update((UUID.randomUUID().toString().toUpperCase() + manager.name + calendar.getTimeInMillis()).getBytes());
            String token = Base64.encodeBase64String(messageDigest.digest());
            calendar.add(Calendar.MINUTE, 10);
            Date expiredWhen = calendar.getTime();
            ConfirmCode cc = new ConfirmCode(manager, customer, token, expiredWhen);
            JPA.em().persist(cc);
            return ok(toJson(cc));
        } else {
            return badRequest();
        }
    }

    @Transactional
    public static Result getLatestLog(Long myId, Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager my = JPA.em().find(Manager.class, myId);
        if (!isTokenValid(my.token, my.expiredWhen)) {
            return unauthorized();
        }
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!my.company.id.equals(manager.company.id)) {
            return badRequest("not same company");
        }
        if (!my.id.equals(manager.id) && my.level >= manager.level) {
            return badRequest("can only query lower level");
        }

        Query query = JPA.em().createNamedQuery("queryLogsByManager");
        List<Log> logs = query.setParameter("manager", manager).setMaxResults(1).getResultList();
        if (logs.isEmpty()) {
            return notFound();
        } else {
            Log log = logs.get(0);
            /*
            ObjectNode jconsume = (ObjectNode)toJson(consume);
            ArrayNode jtickets = jconsume.putArray("tickets");
            Iterator iterator = consume.tickets.iterator();
            while (iterator.hasNext()) {
                JsonNode jticket = (JsonNode)toJson(iterator.next());
                jtickets.add(jticket);
            }

            ArrayNode juses = jconsume.putArray("uses");
            iterator = consume.uses.iterator();
            while (iterator.hasNext()) {
                JsonNode juse = (JsonNode)toJson(iterator.next());
                juses.add(juse);
            }
            */
            return ok(toJson(log));
        }
    }


    @Transactional
    public static Result getStaticsConsumes(Long managerId, String customerName, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        CompanyCustomer companyCustomer = null;
        Query query = JPA.em().createNamedQuery("queryCompanyCustomerByName");
        query.setParameter("company", manager.company);
        query.setParameter("customerName", customerName);
        List<CompanyCustomer> companyCustomers = query.getResultList();
        if (!companyCustomers.isEmpty()){
            companyCustomer = companyCustomers.get(0);
        } else {
            return notFound();
        }

        query = JPA.em().createNamedQuery("queryStaticsConsumes");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", companyCustomer.company);
        List<StaticsConsume> staticsConsumes = query.setFirstResult(startIndex).setMaxResults(10).getResultList();

        Map rst = new HashMap();
        rst.put("totalNumbers",query.getResultList().size());
        rst.put("staticsConsume",staticsConsumes);
        return ok(toJson(rst));
    }
    @Transactional
    public static Result staticsManufacturerConsumes(Long managerId, String customerName, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        CompanyCustomer companyCustomer = null;  // query company customer
        Query query = JPA.em().createNamedQuery("queryCompanyCustomerByName");
        query.setParameter("company", manager.company);
        query.setParameter("customerName", customerName);
        List<CompanyCustomer> companyCustomers = query.getResultList();
        if (!companyCustomers.isEmpty()){
            companyCustomer = companyCustomers.get(0);
        } else {
            return notFound();
        }

        query = JPA.em().createNamedQuery("findCustomerCompanyProductConsumeForManufacturer");
        query.setParameter("customer",companyCustomer.customer);
        query.setParameter("manufacturer",manager.company);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        int totalNumber = query.getResultList().size();
        Logger.info("===total num===="+totalNumber);
        List<CompanyProductConsume>  mConsume = query.setFirstResult(startIndex).setMaxResults(10).getResultList();
        Iterator<CompanyProductConsume> iterator = mConsume.iterator();
        List<Map<String,Object>> returnData =  new ArrayList<>();
        /* */
        while (iterator.hasNext()){
            CompanyProductConsume cpConsume = (CompanyProductConsume)iterator.next();
            Map<String,Object> map = new HashMap();
            map.put("consumeWhen",cpConsume.consume.consumeWhen);
            map.put("company",cpConsume.consume.staticsConsume.company);
            map.put("product",cpConsume.companyProduct.product);
            map.put("service",cpConsume.companyProductConsumeServices);
            map.put("antiFakeNote",cpConsume.antiFakeNote);
            Query queryPictures = JPA.em().createNamedQuery("queryPicturesOfConsume");
            queryPictures.setParameter("cpConsume",cpConsume);
            List<AntiFakePicture> picturesForConsume = queryPictures.getResultList();
            Iterator pic = picturesForConsume.iterator();
            List picIds = new ArrayList();
            while (pic.hasNext()){
                AntiFakePicture atiPic = (AntiFakePicture)pic.next();
                picIds.add(atiPic.id);
            }
            map.put("images",picIds);
            returnData.add(map);
        }
        Map dataMap = new HashMap();
        dataMap.put("productConsumes",returnData);
        dataMap.put("totalNumbers",totalNumber);

       return ok(toJson(dataMap));
    }
    @Transactional
    public static Result staticsManufacturerConsumesByCompany(Long managerId, Long companyId,Long companyProductId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Company company = null;  // query company
        Query query = JPA.em().createNamedQuery("findCompanyById");
        query.setParameter("companyId", companyId);
        List<Company> companies = query.getResultList();
        if (!companies.isEmpty()){
            company = companies.get(0);
            Logger.info("===company=="+company.id);
        } else {
            return notFound();
        }
        Query queryProduct = JPA.em().createNamedQuery("findCompanyProductsOfManufacturerById");
        queryProduct.setParameter("company",manager.company);
        queryProduct.setParameter("companyProductId",companyProductId);
        CompanyProduct cProduct = null;
        List<CompanyProduct> cps = queryProduct.getResultList();
        if(!cps.isEmpty()){
            cProduct = cps.get(0);
        }else {
            return notFound();
        }

        query = JPA.em().createNamedQuery("findCompanyProductConsumeForManufacturerByCompany");
        query.setParameter("company",company);
        query.setParameter("companyProduct",cProduct);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        int totalNumber = query.getResultList().size();
        List<CompanyProductConsume>  mConsume = query.setFirstResult(startIndex).setMaxResults(10).getResultList();
        Iterator<CompanyProductConsume> iterator = mConsume.iterator();
        List<Map<String,Object>> returnData =  new ArrayList<>();
        Logger.info("======len ==="+mConsume.size());
        while (iterator.hasNext()){
            CompanyProductConsume cpConsume = (CompanyProductConsume)iterator.next();
            Logger.info("-------"+cpConsume.companyProduct.company.id);
            Map<String,Object> map = new HashMap();
            map.put("consumeWhen",cpConsume.consume.consumeWhen);
            map.put("customerName",cpConsume.consume.companyCustomer.customer.name);
            map.put("service",cpConsume.companyProductConsumeServices);
            map.put("antiFakeNote",cpConsume.antiFakeNote);
            Query queryPictures = JPA.em().createNamedQuery("queryPicturesOfConsume");
            queryPictures.setParameter("cpConsume",cpConsume);
            List<AntiFakePicture> picturesForConsume = queryPictures.getResultList();
            Iterator pic = picturesForConsume.iterator();
            List picIds = new ArrayList();
            while (pic.hasNext()){
                AntiFakePicture atiPic = (AntiFakePicture)pic.next();
                picIds.add(atiPic.id);
            }
            map.put("images",picIds);
            returnData.add(map);
        }
        Map dataMap = new HashMap();
        dataMap.put("productConsumes",returnData);
        dataMap.put("companyProduct",cProduct);
        dataMap.put("totalNumbers",totalNumber);
        return ok(toJson(dataMap));
    }
    @Transactional
    public static Result staticsManufacturerConsumesByCompanyProductDetail(Long managerId, Long companyId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Company company = null;  // query company
        Query query = JPA.em().createNamedQuery("findCompanyById");
        query.setParameter("companyId", companyId);
        List<Company> companies = query.getResultList();
        if (!companies.isEmpty()){
            company = companies.get(0);
        } else {
            return notFound();
        }

        query = JPA.em().createNamedQuery("findcompanyProductDetailsForManufacturer");
        query.setParameter("company",company);
        query.setParameter("manufacturer",manager.company);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        List<CompanyProductConsume>  results = query.getResultList();

        List<CompanyProduct> cpList = new ArrayList<>();
        for (CompanyProductConsume cConsume: results) {
            Logger.info("======="+cConsume.companyProduct.product.name+":  "+cConsume.quantity);

           if(!cpList.contains(cConsume.companyProduct.source)){
               cpList.add(cConsume.companyProduct.source);
               cConsume.companyProduct.source.count = cConsume.quantity;
           }else{
              cConsume.companyProduct.source.count += cConsume.quantity;
           }
        }
        List<Map<String,Object>> returnData = new ArrayList<>();
        for (CompanyProduct cProduct: cpList) {
             Map dataMap = new HashMap();
             dataMap.put("count",cProduct.count);
             dataMap.put("companyProduct",cProduct);
             returnData.add(dataMap);
        }
        return ok(toJson(returnData));
    }
    @Transactional
    public static Result staticsManufacturerConsumesByProduct(Long managerId, Long companyProductId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        CompanyProduct companyProduct = null;  // query company
        Query query = JPA.em().createNamedQuery("findCompanyProductsOfManufacturerById");
        query.setParameter("company",manager.company);
        query.setParameter("companyProductId", companyProductId);
        List<CompanyProduct> cpts = query.getResultList();
        if (!cpts.isEmpty()){
            companyProduct = cpts.get(0);
            Logger.info("++++++ companyProduct======"+companyProduct.id);
        } else {
            return notFound();
        }

        query = JPA.em().createNamedQuery("findCompanyProductConsumeForManufacturerByProduct");
        query.setParameter("companyProduct",companyProduct);
        query.setParameter("manufacturer",manager.company);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        List<CompanyProductConsume>  results = query.getResultList();
         Logger.info("===results length==="+results.size());
        List<Company> companies = new ArrayList<>();
        for (CompanyProductConsume cConsume: results) {
            Logger.info("======="+cConsume.companyProduct.company.name+":  "+cConsume.quantity);

            if(!companies.contains(cConsume.companyProduct.company)){
                companies.add(cConsume.companyProduct.company);
                cConsume.companyProduct.company.counts = cConsume.quantity;
            }else{
                cConsume.companyProduct.company.counts += cConsume.quantity;
            }
        }
        List<Map<String,Object>> returnData = new ArrayList<>();
        for (Company company: companies) {
            Map dataMap = new HashMap();
            dataMap.put("count",company.counts);
            dataMap.put("company",company);
            returnData.add(dataMap);
        }
        return ok(toJson(returnData));
    }
    @Transactional
    public static Result getAntiFakePictureForManufacturer(Long managerId, Long pictureId) throws Exception
    {

//        Manager manager = JPA.em().find(Manager.class, managerId);
//        if (!isTokenValid(manager.token, manager.expiredWhen)) {
//            return unauthorized();
//        }

        AntiFakePicture antiFakePicture = JPA.em().find(AntiFakePicture.class, pictureId);
        if (antiFakePicture != null) {
            return ok(antiFakePicture.antiFakePicture).as(antiFakePicture.contentType);
        } else {
            return notFound();
        }
    }

    @Transactional
    public static Result getStaticsHomePageHits(Long managerId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryStaticsHomePageHitss");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        query.setParameter("company", manager.company);
        List<StaticsHomePageHits> staticsHomePageHitses = query.setFirstResult(startIndex).setMaxResults(10).getResultList();

        Map rst = new HashMap();
        rst.put("totalNumbers",query.getResultList().size());
        rst.put("staticsHomePageHits",staticsHomePageHitses);
        query = JPA.em().createNamedQuery("queryTotalHomePageHits");
        query.setParameter("company", manager.company);
        Long totalHits = (Long) query.getSingleResult();
        rst.put("totalHomePageHits", totalHits);
        return ok(toJson(rst));
    }

    @Transactional
    public static Result getStaticsBatchHits(Long managerId, Long batchId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryStaticsBatchHitses");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        Batch batch = JPA.em().find(Batch.class, batchId);
        query.setParameter("batch", batch);
        List<StaticsBatchHits> staticsBatchHitses = query.setFirstResult(startIndex).setMaxResults(10).getResultList();

        Map rst = new HashMap();
        rst.put("totalNumbers",query.getResultList().size());
        rst.put("staticsBatchHits", staticsBatchHitses);
        query = JPA.em().createNamedQuery("queryTotalBatchHits");
        query.setParameter("batch", batch);
        Long totalHits = (Long) query.getSingleResult();
        rst.put("totalBatchHits", totalHits);
        return ok(toJson(rst));
    }

    @Transactional
    public static Result getStaticsProductHits(Long managerId, Long productId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryStaticsProductHites");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        Product product = JPA.em().find(Product.class, productId);
        query.setParameter("product", product);
        List<StaticsProductHits> staticsProductHitses= query.setFirstResult(startIndex).setMaxResults(10).getResultList();

        Map rst = new HashMap();
        rst.put("totalNumbers",query.getResultList().size());
        rst.put("staticsProductHits",staticsProductHitses);
        query = JPA.em().createNamedQuery("queryTotalProductHits");
        query.setParameter("product", product);
        Long totalHits = (Long) query.getSingleResult();
        rst.put("totalProductHits", totalHits);
        return ok(toJson(rst));
    }

    @Transactional
    public static Result getStaticsPosterHits(Long managerId, Long posterId, Integer fy, Integer fm, Integer fd, Integer ty, Integer tm, Integer td, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("queryStaticsPosterHitss");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, fy);
        calendar.set(Calendar.MONTH, fm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, fd);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("startDate", calendar.getTime());
        calendar.clear();
        calendar.set(Calendar.YEAR, ty);
        calendar.set(Calendar.MONTH, tm - 1);
        calendar.set(Calendar.DAY_OF_MONTH, td);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("endDate", calendar.getTime());
        CompanyTicket companyTicket = JPA.em().find(CompanyTicket.class, posterId);
        Advertisement advertisement = JPA.em().find(Advertisement.class, posterId);
        query.setParameter("companyTicket", companyTicket);
        query.setParameter("advertisement", advertisement);
        List<StaticsPosterHits> staticsPosterHitses = query.setFirstResult(startIndex).setMaxResults(10).getResultList();
        Logger.info(toJson(staticsPosterHitses).asText());
        Map rst = new HashMap();
        rst.put("totalNumbers",query.getResultList().size());
        rst.put("staticsPosterHits", staticsPosterHitses);
        query = JPA.em().createNamedQuery("queryTotalCompanyTicketHits");
        query.setParameter("companyTicket", companyTicket);
        Long totalCompanyTicketHits = (Long) query.getSingleResult();
        query = JPA.em().createNamedQuery("queryTotalAdevertisementHits");
        query.setParameter("advertisement", advertisement);
        Long totalAdevertisementHits = (Long) query.getSingleResult();
        rst.put("totalCompanyTicketHits", totalCompanyTicketHits);
        rst.put("totalAdvertisementHits", totalAdevertisementHits);
        return ok(toJson(rst));
    }

    @Transactional
    public static Result getPreviousLog(Long myId, Long managerId, Long logId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager my = JPA.em().find(Manager.class, myId);
        if (!isTokenValid(my.token, my.expiredWhen)) {
            return unauthorized();
        }
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!my.company.id.equals(manager.company.id)) {
            return badRequest("not same company");
        }
        if (!my.id.equals(manager.id) && my.level >= manager.level) {
            return badRequest("can only query lower level");
        }

        Log currentLog = JPA.em().find(Log.class, logId);
        Query query = JPA.em().createNamedQuery("queryPreviousLog");
        List<Log> logs = query.setParameter("manager", manager)
                                   .setParameter("logWhen", currentLog.logWhen)
                                   .setMaxResults(1)
                                   .getResultList();
        if (logs.isEmpty()) {
            return notFound();
        } else {
            Log log = logs.get(0);
            return ok(toJson(log));
        }
    }

    @Transactional
    @BodyParser.Of(value = BodyParser.AnyContent.class, maxLength = MAX_LOGO_LENGTH)
    public static Result postLogoOfProduct(Long managerId, Long productId) throws Exception
    {
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Product product = JPA.em().find(Product.class, productId);
        MultipartFormData body = request().body().asMultipartFormData();
        List<MultipartFormData.FilePart> files = body.getFiles();
        
        MultipartFormData.FilePart logoFilePart = body.getFile("logo");
        if (logoFilePart != null) {
            String logoFileName = logoFilePart.getFilename();
            String logoContentType = logoFilePart.getContentType();
            File logoFile = logoFilePart.getFile();
            product.contentType = logoContentType;
            FileInputStream logoStream = new FileInputStream(logoFile);
            int totalLen = (int)logoFile.length();
            byte[] logo = new byte[totalLen];
            int offset = 0;
            while (offset < totalLen) {
                int readLen = logoStream.read(logo, offset, totalLen - offset);
                offset += readLen;
            }
            product.logo = logo;
            JPA.em().persist(product);
            return ok();
        } else {
            return badRequest("upload failed!");
        }
    }


    @Transactional
    public static Result getDiningProducts(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findDiningProductsOfCompany");
        List<CompanyProduct> companyDiningProducts = query.setParameter("company", manager.company)
                                   .getResultList();
        ArrayNode jdiningProducts = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = companyDiningProducts.iterator();
        while (iterator.hasNext()) {
            CompanyProduct companyDiningProduct = (CompanyProduct)iterator.next();
            ObjectNode jcompanyDiningProduct = (ObjectNode)toJson(companyDiningProduct);
            jcompanyDiningProduct.put("product", toJson((DiningProduct)companyDiningProduct.product));
            jdiningProducts.add(jcompanyDiningProduct);
        }
        return ok(jdiningProducts);
    }

    @Transactional
    public static Result getVehicleProducts(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findVehicleProductsOfCompany");
        List<CompanyProduct> companyVehicleProducts = query.setParameter("company", manager.company)
                                   .getResultList();
        ArrayNode jvehicleProducts = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = companyVehicleProducts.iterator();
        while (iterator.hasNext()) {
            CompanyProduct companyVehicleProduct = (CompanyProduct)iterator.next();
            ObjectNode jcompanyVehicleProduct = (ObjectNode)toJson(companyVehicleProduct);
            jcompanyVehicleProduct.put("product", toJson(toJson((VehicleProduct)companyVehicleProduct.product)));
            if (companyVehicleProduct.source != null) {
                jcompanyVehicleProduct.put("isLicense", true);
            } else {
                jcompanyVehicleProduct.put("isLicense", false);
            }
            jvehicleProducts.add(jcompanyVehicleProduct);
        }
        return ok(jvehicleProducts);
    }

    @Transactional
    @BodyParser.Of(value = BodyParser.AnyContent.class, maxLength = MAX_LOGO_LENGTH)
    public static Result postDiningProducts(Long managerId) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        MultipartFormData body = request().body().asMultipartFormData();
        Map<String, String[]> form = body.asFormUrlEncoded();
        String name = form.get("name")[0];
        String description = form.get("description")[0];
        String model = form.get("model")[0];
        String unit = form.get("unit")[0];
        Double price = Double.parseDouble(form.get("price")[0]);
        Product diningProduct = new DiningProduct(name, unit, model, description);

        MultipartFormData.FilePart logoFilePart = body.getFile("logo");
        if (logoFilePart != null) {
            String logoFileName = logoFilePart.getFilename();
            String logoContentType = logoFilePart.getContentType();
            File logoFile = logoFilePart.getFile();
            diningProduct.contentType = logoContentType;
            FileInputStream logoStream = new FileInputStream(logoFile);
            int totalLen = (int)logoFile.length();
            byte[] logo = new byte[totalLen];
            int offset = 0;
            while (offset < totalLen) {
                int readLen = logoStream.read(logo, offset, totalLen - offset);
                offset += readLen;
            }
            diningProduct.logo = logo;
        }

        CompanyProduct companyProduct = new CompanyProduct(manager.company, diningProduct, -1);
        CompanyProductPrice companyProductPrice = new CompanyProductPrice(companyProduct, Math.round(price), manager);
        companyProduct.setPrice(companyProductPrice);
        JPA.em().persist(diningProduct);
        JPA.em().persist(companyProduct);
        JPA.em().persist(companyProductPrice);
        return ok(toJson(companyProduct));
    }

    @Transactional
    @BodyParser.Of(value = BodyParser.AnyContent.class, maxLength = MAX_LOGO_LENGTH)
    public static Result postVehicleProducts(Long managerId) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        MultipartFormData body = request().body().asMultipartFormData();
        Map<String, String[]> form = body.asFormUrlEncoded();
        String name = form.get("name")[0];
        String description = form.get("description")[0];
        String model = form.get("model")[0];
        String eanCode = form.get("eanCode")[0];
        String materialCode = form.get("materialCode")[0];
        String manufacture = form.get("manufacture")[0];
        String adaptableVehicle = form.get("adaptableVehicle")[0];
        String unit = form.get("unit")[0];
        Double suggestedRetailPrice = Double.parseDouble(form.get("suggestedRetailPrice")[0]);
        Double price = Double.parseDouble(form.get("price")[0]);
        Product vehicleProduct = new VehicleProduct(name, unit, model, description, eanCode, materialCode, manufacture, adaptableVehicle, Math.round(suggestedRetailPrice));

        MultipartFormData.FilePart logoFilePart = body.getFile("logo");
        if (logoFilePart != null) {
            String logoFileName = logoFilePart.getFilename();
            String logoContentType = logoFilePart.getContentType();
            File logoFile = logoFilePart.getFile();
            vehicleProduct.contentType = logoContentType;
            FileInputStream logoStream = new FileInputStream(logoFile);
            int totalLen = (int)logoFile.length();
            byte[] logo = new byte[totalLen];
            int offset = 0;
            while (offset < totalLen) {
                int readLen = logoStream.read(logo, offset, totalLen - offset);
                offset += readLen;
            }
            vehicleProduct.logo = logo;
        }

        CompanyProduct companyProduct = new CompanyProduct(manager.company, vehicleProduct, -1);
        CompanyProductPrice companyProductPrice = new CompanyProductPrice(companyProduct, Math.round(price), manager);
        companyProduct.setPrice(companyProductPrice);
        JPA.em().persist(vehicleProduct);
        JPA.em().persist(companyProduct);
        JPA.em().persist(companyProductPrice);
        return ok(toJson(companyProduct));
    }

    @Transactional
    public static Result putDiningProducts(Long managerId, Long companyProductId) throws Exception
    {
        if (!isVersionMatch("2")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        JsonNode json = request().body().asJson();
        JsonNode isPublishedNode = json.get("isPublished");
        JsonNode canBookNode = json.get("canBook");
        if (isPublishedNode != null && canBookNode == null) {   // publish or unpublish
            boolean isPublished = isPublishedNode.asBoolean();
            companyProduct.isPublished = isPublished;
            JPA.em().persist(companyProduct);
        } else if (canBookNode != null) {
            boolean canBook = canBookNode.asBoolean();
            companyProduct.canBook = canBook;
            JPA.em().persist(companyProduct);
        } else {
            JsonNode jproduct = json.findPath("product");
            String name = jproduct.findPath("name").asText();
            String description = jproduct.findPath("description").asText();
            String model = jproduct.findPath("model").asText();
            String unit = jproduct.findPath("unit").asText();
            DiningProduct diningProduct = (DiningProduct)companyProduct.product;
            diningProduct.name = name;
            diningProduct.description = description;
            diningProduct.model = model;
            diningProduct.unit = unit;
            JPA.em().persist(diningProduct);
        }
        return ok(toJson(companyProduct));
    }

    @Transactional
    public static Result putVehicleProducts(Long managerId, Long companyProductId) throws Exception
    {
        if (!isVersionMatch("2")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        JsonNode json = request().body().asJson();
        JsonNode isPublishedNode = json.get("isPublished");
        JsonNode canBookNode = json.get("canBook");
        if (isPublishedNode != null && canBookNode == null) {   // publish or unpublish
            boolean isPublished = isPublishedNode.asBoolean();
            companyProduct.isPublished = isPublished;
            JPA.em().persist(companyProduct);
        } else if (canBookNode != null) {
            boolean canBook = canBookNode.asBoolean();
            companyProduct.canBook = canBook;
            JPA.em().persist(companyProduct);
        } else {
            if (companyProduct.source != null) {
                return forbidden();
            }
            JsonNode jproduct = json.findPath("product");
            String name = jproduct.findPath("name").asText();
            String description = jproduct.findPath("description").asText();
            String model = jproduct.findPath("model").asText();
            String eanCode = jproduct.findPath("eanCode").asText();
            String materialCode = jproduct.findPath("materialCode").asText();
            String adaptableVehicle = jproduct.findPath("adaptableVehicle").asText();
            Double suggestedRetailPrice = jproduct.findPath("suggestedRetailPrice").asDouble();
            String unit = jproduct.findPath("unit").asText();
            VehicleProduct vehicleProduct = (VehicleProduct)companyProduct.product;
            vehicleProduct.name = name;
            vehicleProduct.description = description;
            vehicleProduct.model = model;
            vehicleProduct.eanCode = eanCode;
            vehicleProduct.materialCode = materialCode;
            vehicleProduct.adaptableVehicle = adaptableVehicle;
            vehicleProduct.suggestedRetailPrice = Math.round(suggestedRetailPrice);
            vehicleProduct.unit = unit;
            JPA.em().persist(vehicleProduct);
        }
        return ok(toJson(companyProduct));
    }

    @Transactional
    public static Result postCompanyProductPrices(Long managerId, Long companyProductId) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        JsonNode json = request().body().asJson();
        Long price = json.get("price").asLong();
        CompanyProductPrice companyProductPrice = new CompanyProductPrice(companyProduct, price, manager);
        companyProduct.currentPrice = companyProductPrice;
        JPA.em().persist(companyProductPrice);
        JPA.em().persist(companyProduct);
        return ok(toJson(companyProduct));
    }

    @Transactional
    public static Result putLicensedCompanyProducts(Long managerId, Long companyProductId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        JsonNode json = request().body().asJson();
        Long companyId = json.get("id").asLong();
        boolean isRemoved = json.get("isRemoved").asBoolean();
        Company company = JPA.em().find(Company.class, companyId);
        Query query = JPA.em().createNamedQuery("findCompanyProductsOfSource");
        query.setParameter("company", company);
        query.setParameter("source", companyProduct);
        List<CompanyProduct> companyProducts = query.getResultList();
        if (isRemoved == true) {
            companyProducts.get(0).isRemoved = isRemoved;
            query = JPA.em().createNamedQuery("queryEnabledCompanyProductBatchesByCompanyProduct");
            query.setParameter("companyProduct", companyProduct);
            List<CompanyProductBatch> cpbs = query.getResultList();
            if (!cpbs.isEmpty()) {
                Iterator iterator = cpbs.iterator();
                while (iterator.hasNext()) {
                    TicketBatch ticketBatch = (TicketBatch) iterator.next();
                    if (ticketBatch.validCompanies.contains(company)) {
                        ticketBatch.validCompanies.remove(company);
                        JPA.em().persist(ticketBatch);
                        Logger.info("remove success");
                    }
                }
            }
            JPA.em().persist(companyProducts.get(0));
            return ok(toJson(companyProducts.get(0)));
        } else {
            CompanyProduct licensedCompanyProduct = null;
            if (companyProducts.isEmpty()) {
                licensedCompanyProduct = new CompanyProduct(company, companyProduct, 1);
            } else {
                licensedCompanyProduct = companyProducts.get(0);
                licensedCompanyProduct.isRemoved = false;
            }
            if (companyProduct.postSaleServices.size() > 0) {
                Iterator iterator = companyProduct.postSaleServices.iterator();
                while (iterator.hasNext()) {
                    CompanyProductService companyProductService = (CompanyProductService) iterator.next();
                    CompanyProductService cs = null;
                    if (companyProductService.serviceType == 0) {
                        cs = new CompanyProductService(licensedCompanyProduct, companyProductService.statement);
                    } else {
                        cs = new CompanyProductService(licensedCompanyProduct, companyProductService.statement, companyProductService.validDays);
                    }
                    cs.inherit = companyProductService;
                    Logger.info("success");
                    cs.lastModified = companyProductService.lastModified;
                    JPA.em().persist(cs);
                }
            }
            licensedCompanyProduct.isPublished = true;
            JPA.em().persist(licensedCompanyProduct);
            query = JPA.em().createNamedQuery("queryEnabledCompanyProductBatchesByCompanyProduct");
            query.setParameter("companyProduct", companyProduct);
            Logger.info("====="+companyProduct.id+companyProduct.product.name);
            List<CompanyProductBatch> cpbs = query.getResultList();

            if (!cpbs.isEmpty()) {
                Iterator iterator = cpbs.iterator();
                while (iterator.hasNext()) {
                    TicketBatch ticketBatch = (TicketBatch) iterator.next();
                    if (!ticketBatch.validCompanies.contains(company)) {
                        ticketBatch.validCompanies.add(company);
                        JPA.em().persist(ticketBatch);
                        Logger.info("add success");
                    }
                }
            }
            licensedCompanyProduct.setPrice(companyProduct.currentPrice);
            JPA.em().persist(licensedCompanyProduct);
            return ok(toJson(licensedCompanyProduct));
        }
    }

    @Transactional
    public static Result manufacturerProducts(Long managerId, Long companyId)
    {
        if (!isVersionMatch("1")) {
           return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);

        Query query = JPA.em().createNamedQuery("findVehicleProductsOfCompany");
        query.setParameter("company", manager.company);
        List<CompanyProduct> companyVehicleProducts =query.getResultList();
        ArrayNode jvehicleProducts = new ArrayNode(JsonNodeFactory.instance);
        if (!companyVehicleProducts.isEmpty()) {
            Iterator iterator = companyVehicleProducts.iterator();
            while (iterator.hasNext()) {
                CompanyProduct companyVehicleProduct = (CompanyProduct)iterator.next();
                ObjectNode jcompanyVehicleProduct = (ObjectNode)toJson(companyVehicleProduct);
                jcompanyVehicleProduct.put("product", toJson((VehicleProduct)companyVehicleProduct.product));
                query = JPA.em().createNamedQuery("findSourceProductsOfCompany");
                List<CompanyProduct> liscenseProducts = query.setParameter("company", company).getResultList();
                if (!liscenseProducts.isEmpty() && liscenseProducts.contains(companyVehicleProduct)) {
                    jcompanyVehicleProduct.put("isLicense", true);
                }
                jvehicleProducts.add(jcompanyVehicleProduct);
            }
        }
        return ok(jvehicleProducts);
    }

    @Transactional
    public static Result postServices(Long managerId) {

        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode json = request().body().asJson();
        int serviceType = json.get("serviceType").asInt();
        String statement = json.get("statement").asText();
        int validDays = json.get("validDays").asInt();
        Service service = null;
        if (serviceType == 0) {
            service = new Service(manager.company, statement);
        } else {
            service = new Service(manager.company, statement, validDays);
        }
        JPA.em().persist(service);
        HashMap serviceOfCompany = new HashMap<String, Object>();
        serviceOfCompany.put("company", manager.company);
        serviceOfCompany.put("service", service);
        return ok(toJson(serviceOfCompany));
    }

    @Transactional
    public static Result services(Long managerId) {

        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Query query = JPA.em().createNamedQuery("findServicesOfCompany");
        query.setParameter("company", manager.company);
        List<Service> services = query.getResultList();
        return ok(toJson(services));
    }

    @Transactional
    public static Result putCompanyProductServices(Long managerId, Long companyProductId) {

        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode json = request().body().asJson();

        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        JsonNode jservicesNode = json.get("servicesNode");
        CompanyProductService companyProductService = null;

        Query query = JPA.em().createNamedQuery("findCompanyProductServices");
        query.setParameter("companyProduct", companyProduct);
        List<CompanyProductService> companyProductServices = query.getResultList();

        Iterator cps = companyProductServices.iterator();
        while (cps.hasNext()) {
            companyProductService = (CompanyProductService) cps.next();
            if (companyProductService.inherit == null) {
                JPA.em().remove(companyProductService);
            }
        }

        if (jservicesNode != null) {
            JsonNode servicesNode = jservicesNode.get("services");
            Iterator iterator = servicesNode.iterator();
            while (iterator.hasNext()) {
                JsonNode serviceNode = (JsonNode)iterator.next();
                Long serviceId = serviceNode.get("serviceId").asLong();
                Service service = JPA.em().find(Service.class, serviceId);
                if (service.serviceType == 0) {
                    companyProductService = new CompanyProductService(companyProduct, service.statement);
                } else {
                    companyProductService = new CompanyProductService(companyProduct, service.statement, service.validDays);
                }
                companyProductService.lastModified = service.lastModified;
                JPA.em().persist(companyProductService);

                if (manager.company.companyType == 0) {
                    query = JPA.em().createNamedQuery("findCompanyProductsOfSource");
                    query.setParameter("source", companyProduct);
                    for (Company exclusive: manager.company.exclusiveCompanies) {
                        query.setParameter("company", exclusive);
                        List<CompanyProduct> ecps = query.getResultList();
                        CompanyProductService cs = null;
                        if (!ecps.isEmpty()) {
                            if (companyProductService.serviceType == 0) {
                                cs = new CompanyProductService(ecps.get(0), companyProductService.statement);
                            } else {
                                cs = new CompanyProductService(ecps.get(0), companyProductService.statement, companyProductService.validDays);
                            }
                        }
                        if (cs != null) {
                            cs.inherit = companyProductService;
                            Logger.info("success");
                            cs.lastModified = companyProductService.lastModified;
                            JPA.em().persist(cs);
                        }
                    }
                }
            }
        }

        return ok(toJson(companyProduct.postSaleServices));

    }

    @Transactional
    public static Result deleteService(Long managerId, Long serviceId) {

        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Service service = JPA.em().find(Service.class, serviceId);
        JPA.em().remove(service);
        return ok(toJson(service));
    }

    @Transactional
    public static Result deleteCompanyProduct(Long managerId, Long companyProductId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        companyProduct.isRemoved = true;
        JPA.em().persist(companyProduct);
        if (manager.company.companyType == 0) {
            Query query = JPA.em().createNamedQuery("findCompanyProductsOfSource");
            query.setParameter("source", companyProduct);
            if (!manager.company.exclusiveCompanies.isEmpty()) {
                for (Company exclusive: manager.company.exclusiveCompanies) {
                    query.setParameter("company", exclusive);
                    List<CompanyProduct> ecps = query.getResultList();
                    if (!ecps.isEmpty()) {
                        ecps.get(0).isRemoved = true;
                        JPA.em().persist(ecps.get(0));
                    }
                }
            }

            query = JPA.em().createNamedQuery("queryEnabledCompanyProductBatchesByCompanyProduct");
            query.setParameter("companyProduct", companyProduct);
            List<CompanyProductBatch> cpbs = query.getResultList();
            if (!cpbs.isEmpty()) {
                Iterator iterator = cpbs.iterator();
                while (iterator.hasNext()) {
                    TicketBatch ticketBatch = (TicketBatch) iterator.next();
                    ticketBatch.isEnabled = false;
                    JPA.em().persist(ticketBatch);
                }
            }
        }
        return ok(toJson(companyProduct));
    }

    @Transactional
    public static Result getCompanyProductServices(Long managerId, Long companyProductId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        ArrayNode services = new ArrayNode(JsonNodeFactory.instance);
        if (companyProduct.postSaleServices.size() > 0) {
            Iterator iterator = companyProduct.postSaleServices.iterator();
            while (iterator.hasNext()) {
                CompanyProductService companyProductService = (CompanyProductService) iterator.next();
                JsonNode j = toJson(companyProductService);
                ObjectNode o = (ObjectNode) j;
                if (companyProductService.inherit != null) {
                    o.put("company", toJson(companyProduct.source.company));
                } else {
                    o.put("company", toJson(companyProduct.company));
                }
                services.add(o);
            }
        }
        return ok(toJson(services));
    }

    @Transactional
    public static Result getCompanyProductConsumeServicesByCustomer(Long managerId, Long customerId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        Customer customer = JPA.em().find(Customer.class, customerId);

        Query query = JPA.em().createNamedQuery("findCustomerCompanyProductConsume");
        query.setParameter("customer", customer);
        List<CompanyProductConsume> companyProductConsumes = query.setFirstResult(startIndex).setMaxResults(10).getResultList();

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
            Map rst = new HashMap();
            rst.put("totalNumbers", query.getResultList().size());
            rst.put("companyProductConsume", jcompanyProductConsumes);
            return ok(toJson(rst));
        } else {
            return  notFound();
        }
    }

    @Transactional
    public static Result getCompanyProductConsumeServicesByProduct(Long managerId, Long companyProductId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);

        Query query = JPA.em().createNamedQuery("findCompanyProductConsume");
        query.setParameter("companyProduct", companyProduct);
        List<CompanyProductConsume> companyProductConsumes = query.setFirstResult(startIndex).setMaxResults(10).getResultList();
        Logger.info(String.valueOf(toJson(companyProductConsumes)));
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
                    jcompanyProductConsume.put("companyCustomer", companyProductConsumeService.companyProductConsume.consume.companyCustomer.customer.name);
                }

                jcompanyProductConsumes.add(jcompanyProductConsume);
            }
            Map rst = new HashMap();
            rst.put("totalNumbers", query.getResultList().size());
            rst.put("companyProductConsume", jcompanyProductConsumes);
            return ok(toJson(rst));
        } else {
            return  notFound();
        }
    }

    @Transactional
    public static Result postCompanyProductServices(Long managerId, Long companyProductId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
        if (companyProduct.company.id != manager.company.id) {
            return badRequest();
        }

        JsonNode json = request().body().asJson();
        int serviceType = json.get("serviceType").asInt();
        String statement = json.get("statement").asText();
        int validDays = json.get("validDays").asInt();
        CompanyProductService companyProductService = null;
        if (serviceType == 0) {
            companyProductService = new CompanyProductService(companyProduct, statement);
        } else {
            companyProductService = new CompanyProductService(companyProduct, statement, validDays);
        }
        JPA.em().persist(companyProductService);
        HashMap serviceOfCompany = new HashMap<String, Object>();
        serviceOfCompany.put("company", companyProduct.company);
        serviceOfCompany.put("service", companyProductService);
        return ok(toJson(serviceOfCompany));
    }

    @Transactional
    public static Result deleteCompanyProductService(Long managerId, Long serviceId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyProductService companyProductService = JPA.em().find(CompanyProductService.class, serviceId);
        if (companyProductService.companyProduct.source != null) {
            return forbidden();
        }
        if (companyProductService.companyProduct.company.id != manager.company.id) {
            return badRequest();
        }
        JPA.em().remove(companyProductService);
        return ok(toJson(companyProductService));
    }

/*
** https://github.com/playframework/playframework/pull/3388
    @Transactional
    public static Promise<Result> postAdvertisements(Long managerId)
    {
        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return Promise.promise(new Function0<Result> () {
                public Result apply() {
                    return unauthorized();
                }
            });
        }

        Promise<Advertisement> promiseOfAdvertisement = Promise.promise(
            new Function0<Advertisement>() {
                public Advertisement apply() {
                    JsonNode json = request().body().asJson();
                    String content = json.get("content").asText();
                    Long adStart = json.get("adStart").asLong();
                    Long adEnd = json.get("adEnd").asLong();
                    Advertisement ad = new Advertisement(manager.company, content, new Date(adStart), new Date(adEnd));
                    JPA.em().persist(ad);

                    List<CompanyCustomer> companyCustomers = manager.company.companyCustomers;
                    Iterator<CompanyCustomer> iter = companyCustomers.iterator();
                    while (iter.hasNext()) {
                        CompanyCustomer companyCustomer = (CompanyCustomer)iter.next();
                        AdvertisementInfo adInfo = new AdvertisementInfo(companyCustomer.customer, ad);
                        JPA.em().persist(adInfo);
                    }
                    return ad;
                }
            }
        );

        return promiseOfAdvertisement.map(
            new Function<Advertisement, Result>() {
                public Result apply(Advertisement ad) {
                    return ok(toJson(ad));
                }
            }
        );
    }
*/

    @Transactional
    public static Result getBroadcasts(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryBroadcastOfCompany");
        query.setParameter("company", manager.company);
        List<Poster> posters = query.getResultList();
        ArrayNode jposters = new ArrayNode(JsonNodeFactory.instance);
        if(!posters.isEmpty()){
            Poster poster=posters.get(0);
            int interval = BROADCAST_INTERVAL;
            query = JPA.em().createNamedQuery("queryConfig");
            query.setParameter("configName", "BROADCAST_INTERVAL");
            List<TicketConfig> ticketConfigs = query.getResultList();
            if (!ticketConfigs.isEmpty()) {
                TicketConfig tc = ticketConfigs.get(0);
                interval = Integer.parseInt(tc.configValue);
            }
            Calendar calendarExpired = Calendar.getInstance();
            calendarExpired.setTime(poster.posterWhen);
            calendarExpired.add(Calendar.SECOND, interval);

            Iterator iterator = posters.iterator();
            while (iterator.hasNext()) {
                Poster jposter = (Poster) iterator.next();
                JsonNode j = toJson(jposter);
                ObjectNode o = (ObjectNode) j;
                if(poster.id == jposter.id){
                    o.put("leftTime", toJson(caculateLeftTime(calendarExpired)));
                }
                jposters.add(j);
            }
        }

        return ok(toJson(jposters));
    }

    @Transactional
    public static Result getNotifications(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryNotificationOfCompany");
        query.setParameter("company", manager.company);
        List<Poster> posters = query.getResultList();
        ArrayNode jposters = new ArrayNode(JsonNodeFactory.instance);
        if(!posters.isEmpty()){
            Poster poster=posters.get(0);
            int interval = NOTIFICATION_INTERVAL;
            query = JPA.em().createNamedQuery("queryConfig");
            query.setParameter("configName", "NOTIFICATION_INTERVAL");
            List<TicketConfig> ticketConfigs = query.getResultList();
            if (!ticketConfigs.isEmpty()) {
                TicketConfig tc = ticketConfigs.get(0);
                interval = Integer.parseInt(tc.configValue);
            }
            Calendar calendarExpired = Calendar.getInstance();
            calendarExpired.setTime(poster.posterWhen);
            calendarExpired.add(Calendar.SECOND, interval);
            Iterator iterator = posters.iterator();
            while (iterator.hasNext()) {
                Poster jposter = (Poster) iterator.next();
                JsonNode j = toJson(jposter);
                ObjectNode o = (ObjectNode) j;
                if(poster.id == jposter.id){
                    o.put("leftTime", toJson(caculateLeftTime(calendarExpired)));
                }
                jposters.add(j);
            }
        }
        return ok(jposters);
    }
    @Transactional
    public static Result postAdvertisements(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }

        JsonNode json = request().body().asJson();
        String content = json.get("content").asText();
        int scope = json.get("scope").asInt();
        if (scope == 0) {   // check broadcast interval
            Query query = JPA.em().createNamedQuery("queryBroadcastOfCompany");
            query.setParameter("company", manager.company);
            List<Poster> posters = query.setMaxResults(1).getResultList();
            if (!posters.isEmpty()) {
                Poster poster = posters.get(0);
                // check interval
                int interval = BROADCAST_INTERVAL;
                query = JPA.em().createNamedQuery("queryConfig");
                query.setParameter("configName", "BROADCAST_INTERVAL");
                List<TicketConfig> ticketConfigs = query.getResultList();
                if (!ticketConfigs.isEmpty()) {
                    TicketConfig tc = ticketConfigs.get(0);
                    interval = Integer.parseInt(tc.configValue);
                }
                Calendar calendarExpired = Calendar.getInstance();
                calendarExpired.setTime(poster.posterWhen);
                calendarExpired.add(Calendar.SECOND, interval);
                if (calendarExpired.after(Calendar.getInstance())) {
                    return badRequest("time interval error");
                }
            }
            Advertisement ad = new Advertisement(manager.company, content, scope);
            JPA.em().persist(ad);
            return ok(toJson(ad));
        } else if (scope == 1) {
            Query query = JPA.em().createNamedQuery("queryNotificationOfCompany");
            query.setParameter("company", manager.company);
            List<Poster> posters = query.setMaxResults(1).getResultList();
            if (!posters.isEmpty()) {
                Poster poster = posters.get(0);
                // check interval
                int interval = NOTIFICATION_INTERVAL;
                query = JPA.em().createNamedQuery("queryConfig");
                query.setParameter("configName", "NOTIFICATION_INTERVAL");
                List<TicketConfig> ticketConfigs = query.getResultList();
                if (!ticketConfigs.isEmpty()) {
                    TicketConfig tc = ticketConfigs.get(0);
                    interval = Integer.parseInt(tc.configValue);
                }
                Calendar calendarExpired = Calendar.getInstance();
                calendarExpired.setTime(poster.posterWhen);
                calendarExpired.add(Calendar.SECOND, interval);
                if (calendarExpired.after(Calendar.getInstance())) {
                    return badRequest("time interval error");
                }
            }
            Advertisement ad = new Advertisement(manager.company, content, scope);
            JPA.em().persist(ad);
            List<CompanyCustomer> companyCustomers = manager.company.companyCustomers;
            Iterator<CompanyCustomer> iter = companyCustomers.iterator();
            while (iter.hasNext()) {
                CompanyCustomer companyCustomer = (CompanyCustomer)iter.next();
                AdvertisementInfo adInfo = new AdvertisementInfo(companyCustomer.customer, manager.company, ad);
                JPA.em().persist(adInfo);
            }
            return ok(toJson(ad));
        } else {
            return badRequest("parameter error");
        }
    }

    @Transactional
    public static Result postCompanyFreeTickets(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();

        String name = json.get("name").asText();
        Long batchId = json.get("batch").get("id").asLong();
        FreeBatch batch = JPA.em().find(FreeBatch.class, batchId);
        if (!batch.company.id.equals(manager.company.id)) {
            return badRequest();
        }
        int scope = json.get("scope").asInt();
        if (scope == 0) {   // check broadcast interval
            Query query = JPA.em().createNamedQuery("queryBroadcastOfCompany");
            query.setParameter("company", manager.company);
            List<Poster> posters = query.setMaxResults(1).getResultList();
            if (!posters.isEmpty()) {
                Poster poster = posters.get(0);
                // check interval
                int interval = BROADCAST_INTERVAL;
                query = JPA.em().createNamedQuery("queryConfig");
                query.setParameter("configName", "BROADCAST_INTERVAL");
                List<TicketConfig> ticketConfigs = query.getResultList();
                if (!ticketConfigs.isEmpty()) {
                    TicketConfig tc = ticketConfigs.get(0);
                    interval = Integer.parseInt(tc.configValue);
                }
                Calendar calendarExpired = Calendar.getInstance();
                calendarExpired.setTime(poster.posterWhen);
                calendarExpired.add(Calendar.SECOND, interval);
                if (calendarExpired.after(Calendar.getInstance())) {
                    return badRequest("time interval error");
                }
            }
            CompanyTicket freeTicket = new CompanyTicket(manager.company, name, batch, scope);
            JPA.em().persist(freeTicket);
            return ok(toJson(freeTicket));
        } else if (scope == 1) {
            Query query = JPA.em().createNamedQuery("queryNotificationOfCompany");
            query.setParameter("company", manager.company);
            List<Poster> posters = query.setMaxResults(1).getResultList();
            if (!posters.isEmpty()) {
                Poster poster = posters.get(0);
                // check interval
                int interval = NOTIFICATION_INTERVAL;
                query = JPA.em().createNamedQuery("queryConfig");
                query.setParameter("configName", "NOTIFICATION_INTERVAL");
                List<TicketConfig> ticketConfigs = query.getResultList();
                if (!ticketConfigs.isEmpty()) {
                    TicketConfig tc = ticketConfigs.get(0);
                    interval = Integer.parseInt(tc.configValue);
                }
                Calendar calendarExpired = Calendar.getInstance();
                calendarExpired.setTime(poster.posterWhen);
                calendarExpired.add(Calendar.SECOND, interval);
                if (calendarExpired.after(Calendar.getInstance())) {
                    return badRequest("time interval error");
                }
            }
            CompanyTicket freeTicket = new CompanyTicket(manager.company, name, batch, scope);
            JPA.em().persist(freeTicket);
            List<CompanyCustomer> companyCustomers = manager.company.companyCustomers;
            Iterator<CompanyCustomer> iter = companyCustomers.iterator();
            while (iter.hasNext()) {
                CompanyCustomer companyCustomer = (CompanyCustomer)iter.next();
                CompanyTicketInfo companyTicketInfo = new CompanyTicketInfo(companyCustomer.customer, manager.company, freeTicket);
                JPA.em().persist(companyTicketInfo);
            }
            return ok(toJson(freeTicket));
        } else {
            return badRequest("parameter error");
        }
    }

    @Transactional
    public static Result postCompanyGrouponTickets(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();

        String name = json.get("name").asText();
        Long batchId = json.get("batch").get("id").asLong();
        GrouponBatch batch = JPA.em().find(GrouponBatch.class, batchId);
        if (!batch.company.id.equals(manager.company.id)) {
            return badRequest();
        }
        int scope = json.get("scope").asInt();
        if (scope == 0) {   // check broadcast interval
            Query query = JPA.em().createNamedQuery("queryBroadcastOfCompany");
            query.setParameter("company", manager.company);
            List<Poster> posters = query.setMaxResults(1).getResultList();
            if (!posters.isEmpty()) {
                Poster poster = posters.get(0);
                // check interval
                int interval = BROADCAST_INTERVAL;
                query = JPA.em().createNamedQuery("queryConfig");
                query.setParameter("configName", "BROADCAST_INTERVAL");
                List<TicketConfig> ticketConfigs = query.getResultList();
                if (!ticketConfigs.isEmpty()) {
                    TicketConfig tc = ticketConfigs.get(0);
                    interval = Integer.parseInt(tc.configValue);
                }
                Calendar calendarExpired = Calendar.getInstance();
                calendarExpired.setTime(poster.posterWhen);
                calendarExpired.add(Calendar.SECOND, interval);
                if (calendarExpired.after(Calendar.getInstance())) {
                    return badRequest("time interval error");
                }
            }
            CompanyTicket grouponTicket = new CompanyTicket(manager.company, name, batch, scope);
            JPA.em().persist(grouponTicket);
            return ok(toJson(grouponTicket));
        } else if (scope == 1) {
            Query query = JPA.em().createNamedQuery("queryNotificationOfCompany");
            query.setParameter("company", manager.company);
            List<Poster> posters = query.setMaxResults(1).getResultList();
            if (!posters.isEmpty()) {
                Poster poster = posters.get(0);
                // check interval
                int interval = NOTIFICATION_INTERVAL;
                query = JPA.em().createNamedQuery("queryConfig");
                query.setParameter("configName", "NOTIFICATION_INTERVAL");
                List<TicketConfig> ticketConfigs = query.getResultList();
                if (!ticketConfigs.isEmpty()) {
                    TicketConfig tc = ticketConfigs.get(0);
                    interval = Integer.parseInt(tc.configValue);
                }
                Calendar calendarExpired = Calendar.getInstance();
                calendarExpired.setTime(poster.posterWhen);
                calendarExpired.add(Calendar.SECOND, interval);
                if (calendarExpired.after(Calendar.getInstance())) {
                    return badRequest("time interval error");
                }
            }
            CompanyTicket grouponTicket = new CompanyTicket(manager.company, name, batch, scope);
            JPA.em().persist(grouponTicket);
            List<CompanyCustomer> companyCustomers = manager.company.companyCustomers;
            Iterator<CompanyCustomer> iter = companyCustomers.iterator();
            while (iter.hasNext()) {
                CompanyCustomer companyCustomer = (CompanyCustomer)iter.next();
                CompanyTicketInfo companyTicketInfo = new CompanyTicketInfo(companyCustomer.customer, manager.company, grouponTicket);
                JPA.em().persist(companyTicketInfo);
            }
            return ok(toJson(grouponTicket));
        } else {
            return badRequest("parameter error");
        }
    }

    @Transactional
    public static Result getCompanyCustomers(Long managerId, String customerName)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryCompanyCustomerByName");
        query.setParameter("company", manager.company);
        query.setParameter("customerName", customerName);
        List<CompanyCustomer> companyCustomers = query.getResultList();
        if (companyCustomers.isEmpty()) {
            return notFound();
        } else {
            CompanyCustomer companyCustomer = companyCustomers.get(0);
            return ok(toJson(companyCustomer));
        }
    }

    @Transactional
    public static Result getAllCompanyCustomers(Long managerId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryCompanyCustomers");
        query.setParameter("company", manager.company);
        int totalNumbers = query.getResultList().size();
        List<CompanyCustomer> companyCustomers = query.setFirstResult(startIndex).setMaxResults(10).getResultList();

        ArrayNode jcompanyCustometers = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = companyCustomers.iterator();
        while (iterator.hasNext()) {
            CompanyCustomer companyCustomer = (CompanyCustomer) iterator.next();
            JsonNode j = toJson(companyCustomer);
            ObjectNode o = (ObjectNode) j;

            query = JPA.em().createNamedQuery("findCustomerAllTicketsValidInCompany");
            query.setParameter("customerId", companyCustomer.customer.id);
            query.setParameter("company", manager.company);
            List<Ticket> tickets = query.getResultList();
            o.put("ticketNumber", tickets.size());

            query = JPA.em().createNamedQuery("queryCompanyCustomerConsumes");
            query.setParameter("companyCustomer", companyCustomer);
            List<Consume> consumes = query.getResultList();
            if (!consumes.isEmpty()) {
                o.put("finalConsumeWhen", consumes.get(0).consumeWhen.getTime());
            } else {
                o.put("finalConsumeWhen", "未消费");
            }
            jcompanyCustometers.add(o);
        }

        Map rst = new HashMap();
        rst.put("companyCustomers", jcompanyCustometers);
        rst.put("totalNumbers", totalNumbers);
        query = JPA.em().createNamedQuery("queryTotalBalanceOfCompany");
        query.setParameter("company", manager.company);
        Long totalBalance = (Long) query.getResultList().get(0);
        rst.put("totalBalance", totalBalance);
        return ok(toJson(rst));
    }

    @Transactional
    public static Result getAllCompanyAccounts(Long managerId, Integer startIndex)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryAllCompanyAccounts");
        query.setParameter("company", manager.company);
        List<CompanyAccount> companyAccounts = query.setFirstResult(startIndex).setMaxResults(10).getResultList();

        ArrayNode jcompanyAccounts = new ArrayNode(JsonNodeFactory.instance);
        Iterator iterator = companyAccounts.iterator();
        while (iterator.hasNext()) {
            CompanyAccount companyAccount = (CompanyAccount) iterator.next();
            JsonNode j = toJson(companyAccount);
            ObjectNode o = (ObjectNode) j;

            if (companyAccount.manager != null){
                o.put("action", "预存结账");
            } else if (companyAccount.admin != null){
                o.put("action", "管理员充值");
            } else if (companyAccount.ticket != null){
                o.put("action", "电子券收");
            } else {
                o.put("action", "支付宝充值");
            }
            jcompanyAccounts.add(o);

        }

        Map rst = new HashMap();
        rst.put("companyAccount", jcompanyAccounts);
        rst.put("totalNumbers", query.getResultList().size());
        return ok(toJson(rst));
    }

    //ke.zhang 2016-4-26 after prestore,register as companycustomer
    @Transactional
    public static Result postCompanyCustomers(Long managerId, Long customerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class,managerId);
        if(!isTokenValid(manager.token,manager.expiredWhen)){
            return unauthorized();
        }
        JsonNode json=request().body().asJson();
        Customer customer = JPA.em().find(Customer.class, customerId);
        Long balance = json.get("balance").asLong();
        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsCustomer");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsCustomer staticsCustomer = null;
        List<StaticsCustomer> staticsCustomers = query.getResultList();
        if (staticsCustomers.isEmpty()) {
            staticsCustomer = new StaticsCustomer(calendar.getTime(), manager.company);
        } else {
            staticsCustomer = staticsCustomers.get(0);
        }

        query = JPA.em().createNamedQuery("queryStaticsConsume");
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsConsume staticsConsume = null;
        List<StaticsConsume> staticsConsumes = query.getResultList();
        if (staticsConsumes.isEmpty()) {
            staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), 0L, 0L, 0L, 0L, 0L, 0L, balance, 0L);
        } else {
            staticsConsume = staticsConsumes.get(0);
            staticsConsume.recharge += balance;
        }
        JPA.em().persist(staticsConsume);

        query = JPA.em().createNamedQuery("queryCompanyCustomer");
        query.setParameter("company", manager.company);
        query.setParameter("customer", customer);
        CompanyCustomer companyCustomer = null;
        List<CompanyCustomer> companyCustomers = query.getResultList();
        if(companyCustomers.isEmpty()){
            companyCustomer = new CompanyCustomer(manager.company, customer, 0L, staticsCustomer);
            companyCustomer.times = 0L;
            JPA.em().persist(companyCustomer);
            staticsCustomer.regs += 1;
            JPA.em().persist(staticsCustomer);

            Recharge recharge = new Recharge(companyCustomer, balance);
            JPA.em().persist(recharge);
            RechargeInfo rechargeInfo = new RechargeInfo(companyCustomer.customer, manager.company, recharge);
            JPA.em().persist(rechargeInfo);
            RechargeLog record = new RechargeLog(manager, recharge);
            JPA.em().persist(record);
            Logger.info(toJson(companyCustomer).toString());
            return ok(toJson(companyCustomer));
        }else{
            return ok(toJson(companyCustomers.get(0)));
        }
    }

    @Transactional
    public static Result putCompanyCustomerDiscount(Long managerId, Long companyCustomerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (companyCustomer.company.id != manager.company.id) {
            return badRequest();
        }
        JsonNode json = request().body().asJson();
        int discount = json.get("discount").asInt();
        companyCustomer.discount = discount;
        JPA.em().persist(companyCustomer);
        return ok(toJson(companyCustomer));
    }

    @Transactional
    public static Result rechargeCompanyCustomer(Long managerId, Long companyCustomerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!companyCustomer.company.id.equals(manager.company.id)) {
            return badRequest();
        }
        JsonNode json = request().body().asJson();
        Double amount = json.get("recharge").asDouble();
        Recharge recharge = new Recharge(companyCustomer, Math.round(amount));
        JPA.em().persist(recharge);
        JPA.em().persist(companyCustomer);
        RechargeInfo rechargeInfo = new RechargeInfo(companyCustomer.customer, manager.company, recharge);
        JPA.em().persist(rechargeInfo);
        RechargeLog record = new RechargeLog(manager, recharge);
        JPA.em().persist(record);

        Calendar calendar = Calendar.getInstance();
        Query query = JPA.em().createNamedQuery("queryStaticsConsume");
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        query.setParameter("date", calendar.getTime());
        query.setParameter("company", manager.company);
        StaticsConsume staticsConsume = null;
        List<StaticsConsume> staticsConsumes = query.getResultList();
        if (staticsConsumes.isEmpty()) {
            staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), 0L, 0L, 0L, 0L, 0L, amount.longValue(), 0L, 0L);
        } else {
            staticsConsume = staticsConsumes.get(0);
            staticsConsume.recharge += amount.longValue();
        }
        JPA.em().persist(staticsConsume);
        return ok(toJson(companyCustomer));
    }

    @Transactional
    public static Result integralCompanyCustomer(Long managerId, Long companyCustomerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!companyCustomer.company.id.equals(manager.company.id)) {
            return badRequest();
        }
        JsonNode json = request().body().asJson();
        Double amount = json.get("amount").asDouble();
        Long id = json.get("ruleId").asLong();
        Long cId = json.get("consumeId").asLong();
        Long increase = 0L;

        IntegralStrategy integralStrategy = JPA.em().find(IntegralStrategy.class, id);
        Consume consume =JPA.em().find(Consume.class, cId);

        if (integralStrategy != null) {
            ArrayNode jintegrals = new ArrayNode(JsonNodeFactory.instance);
            if (amount >= (integralStrategy.minNumber) && amount < (integralStrategy.maxNumber)) {
                increase = Math.round(amount*integralStrategy.discount / 100) ;
                Integral integral = new Integral(companyCustomer, increase);
                JPA.em().persist(integral);
                consume.integralIncrease = increase;
                JPA.em().persist(consume);
                JsonNode j = toJson(integral);
                ObjectNode o = (ObjectNode) j;
                o.put("increase", increase);
                jintegrals.add(j);

                Calendar calendar = Calendar.getInstance();
                Query query = JPA.em().createNamedQuery("queryStaticsConsume");
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                query.setParameter("date", calendar.getTime());
                query.setParameter("company", manager.company);
                StaticsConsume staticsConsume = null;
                List<StaticsConsume> staticsConsumes = query.getResultList();
                if (staticsConsumes.isEmpty()) {
                    staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), 0L, 0L, amount.longValue(), amount.longValue(), 0L, 0L, increase, 0L);
                } else {
                    staticsConsume = staticsConsumes.get(0);
                    staticsConsume.amount += amount.longValue();
                    staticsConsume.netPaid += amount.longValue();
                    staticsConsume.integralIncrease += increase;
                }
                JPA.em().persist(staticsConsume);

            } else {
                return badRequest();
            }
            return ok(jintegrals);
        } else {
            return badRequest("no strategies or integral");
        }
    }

    @Transactional
    public static Result getCompanyCustomerConsumes(Long managerId, Long companyCustomerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!companyCustomer.company.id.equals(manager.company.id)) {
            return badRequest();
        }
        Query query = JPA.em().createNamedQuery("queryCompanyCustomerConsumes");
        query.setParameter("companyCustomer", companyCustomer);
        List<Consume> consumes = query.getResultList();
        return ok(toJson(consumes));
    }

    @Transactional
    public static Result postCompanyCustomerConsumes(Long managerId, Long companyCustomerId)
    {
        if (!isVersionMatch("2")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        CompanyCustomer companyCustomer = JPA.em().find(CompanyCustomer.class, companyCustomerId);
        if (!companyCustomer.company.id.equals(manager.company.id)) {
            return badRequest();
        }

        JsonNode json = request().body().asJson();
        Long reduce = json.get("iamount").asLong();
        String token = json.get("token").asText();
        Long amount = json.get("amount").asLong();

        // integral consumeption
        if (reduce != 0 ) {
            if (reduce.compareTo(companyCustomer.integralBalance) > 0) {
                return badRequest();
            }
            Long iamount = reduce;
            Integral integral = new Integral(companyCustomer, reduce, iamount);
            JPA.em().persist(integral);

            IntegralInfo integralInfo = new IntegralInfo(companyCustomer.customer, manager.company, integral);
            JPA.em().persist(integralInfo);
            IntegralLog record = new IntegralLog(manager, integral);
            JPA.em().persist(record);

            Query query = JPA.em().createNamedQuery("queryConfirmCodeToken");
            query.setParameter("manager", manager);
            query.setParameter("customer", companyCustomer.customer);
            query.setParameter("token", token);
            List<ConfirmCode> ccs = query.getResultList();
            if (ccs.isEmpty()) {
                return badRequest();
            }

            if (companyCustomer.customer.dynamicConfirmCode) {
                JPA.em().persist(companyCustomer.customer.changeConfirmCode());
            }

            Calendar calendar = Calendar.getInstance();
            query = JPA.em().createNamedQuery("queryStaticsConsume");
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            query.setParameter("date", calendar.getTime());
            query.setParameter("company", manager.company);
            StaticsConsume staticsConsume = null;
            List<StaticsConsume> staticsConsumes = query.getResultList();
            if (staticsConsumes.isEmpty()) {
                staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), 0L, 0L, 0L, 0L, 0L, 0L, 0L, reduce);
            } else {
                staticsConsume = staticsConsumes.get(0);
                staticsConsume.integralReduce += reduce;
            }
            JPA.em().persist(staticsConsume);

            return ok(toJson(integral));
        } else {
            Query query = JPA.em().createNamedQuery("queryCompanyAccountConfig");
            query.setParameter("company", manager.company);
            List<CompanyAccountConfig> companyAccountConfigs = query.getResultList();
            CompanyAccountConfig companyAccountConfig = null;
            if (companyAccountConfigs.isEmpty()) {
                return forbidden("no config");
            } else {
                companyAccountConfig = companyAccountConfigs.get(0);
            }

            if(manager.company.balance < Long.parseLong(companyAccountConfig.accountValue)) {
                return forbidden("balance not enough");
            }

            Long netPaid = amount * companyCustomer.discount / 100;
            if (netPaid.compareTo(companyCustomer.balance) > 0) {
                return badRequest();
            }


            query = JPA.em().createNamedQuery("queryConfirmCodeToken");
            query.setParameter("manager", manager);
            query.setParameter("customer", companyCustomer.customer);
            query.setParameter("token", token);
            List<ConfirmCode> ccs = query.getResultList();
            if (ccs.isEmpty()) {
                return badRequest();
            }

            Calendar calendar = Calendar.getInstance();
            query = JPA.em().createNamedQuery("queryStaticsCustomer");
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            query.setParameter("date", calendar.getTime());
            query.setParameter("company", companyCustomer.company);
            StaticsCustomer staticsCustomer = null;
            List<StaticsCustomer> staticsCustomers = query.getResultList();
            if (staticsCustomers.isEmpty()) {
                staticsCustomer = new StaticsCustomer(calendar.getTime(), manager.company);
            } else {
                staticsCustomer = staticsCustomers.get(0);
            }

            companyCustomer.amount += netPaid;
            companyCustomer.balance -= netPaid;
            companyCustomer.times += 1;
            JPA.em().persist(companyCustomer);

            staticsCustomer.amount += amount;
            staticsCustomer.netPaid += netPaid;
            JPA.em().persist(staticsCustomer);

            calendar = Calendar.getInstance();
            query = JPA.em().createNamedQuery("queryStaticsConsume");
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            query.setParameter("date", calendar.getTime());
            query.setParameter("company", manager.company);
            StaticsConsume staticsConsume = null;
            List<StaticsConsume> staticsConsumes = query.getResultList();
            if (staticsConsumes.isEmpty()) {
                staticsConsume = new StaticsConsume(manager.company, calendar.getTime(), 0L, 0L, amount, 0L, netPaid, 0L, 0L, 0L);
            } else {
                staticsConsume = staticsConsumes.get(0);
                staticsConsume.amount += amount;
                staticsConsume.netDeduction += netPaid;
            }
            JPA.em().persist(staticsConsume);

            Consume consume = new Consume(companyCustomer, staticsConsume, amount, 0L, netPaid, 2);
            JPA.em().persist(consume);
            if (companyCustomer.customer.dynamicConfirmCode) {
                JPA.em().persist(companyCustomer.customer.changeConfirmCode());
            }
            ConsumeLog record = new ConsumeLog(manager, consume);
            JPA.em().persist(record);

            ConsumeInfo consumeInfo = new ConsumeInfo(companyCustomer.customer, manager.company, consume);
            JPA.em().persist(consumeInfo);
            JsonNode companyProductsNode = json.get("companyProducts");
            if (companyProductsNode != null) {
                // iterate companyProducts to create CompanyProductConsume
                //  and iterate companyProduct.postSaleServices to create CompanyProductConsumeService
                //ArrayNode arrayNode = (ArrayNode)companyProductsNode;
                Iterator iterator = companyProductsNode.iterator();
                while (iterator.hasNext()) {
                    JsonNode companyProductNode = (JsonNode)iterator.next();
                    Long companyProductId = companyProductNode.get("companyProductId").asLong();
                    int quantity = companyProductNode.get("quantity").asInt();
                    String antiFakeNote = companyProductNode.get("antifakenote").asText();
                    CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, companyProductId);
                    CompanyProductConsume companyProductConsume = new CompanyProductConsume(companyProduct, consume, quantity);
                    if (antiFakeNote != null) {
                        companyProductConsume.antiFakeNote = antiFakeNote;
                    }
                    if (companyProduct.source != null) {
                        Query querySource = JPA.em().createNamedQuery("queryCompanyCustomer");
                        querySource.setParameter("company",companyProduct.source.company);
                        querySource.setParameter("customer",companyCustomer.customer);
                        List<CompanyCustomer> sourceCompanyCustomers = querySource.getResultList();
                        if(sourceCompanyCustomers.isEmpty()){
                            JPA.em().persist(new CompanyCustomer(companyProduct.source.company, companyCustomer.customer));
                        }
                    }
                    List<CompanyProductService> services = companyProduct.postSaleServices;
                    Iterator it = services.iterator();
                    while (it.hasNext()) {
                        CompanyProductService companyProductService = (CompanyProductService)it.next();
                        if (companyProductService.serviceType == 0) {
                            CompanyProductConsumeService service = new CompanyProductConsumeService(companyProductConsume, companyProductService.statement);
                            if (companyProductService.inherit != null) {
                                service.inherit = companyProductService.inherit.companyProduct.company;
                            }
                            JPA.em().persist(service);
                        } else {
                            CompanyProductConsumeService service = new CompanyProductConsumeService(companyProductConsume, companyProductService.statement, companyProductService.validDays);
                            if (companyProductService.inherit != null) {
                                service.inherit = companyProductService.inherit.companyProduct.company;
                            }
                            JPA.em().persist(service);
                        }
                    }
                    List<AntiFakePicture> pictures = new ArrayList<>();
                    if (companyProductNode.get("picture") != null && companyProductNode.get("picture").asLong() != 0) {
                        Long picture = companyProductNode.get("picture").asLong();
                        AntiFakePicture antiFakePicture = JPA.em().find(AntiFakePicture.class, picture);
                        antiFakePicture.companyProductConsume = companyProductConsume;
                        JPA.em().persist(antiFakePicture);
                        pictures.add(antiFakePicture);
                    }

                    if (companyProductNode.get("picture1") != null && companyProductNode.get("picture1").asLong() != 0) {
                        Long picture1 = companyProductNode.get("picture1").asLong();
                        AntiFakePicture antiFakePicture1 = JPA.em().find(AntiFakePicture.class, picture1);
                        antiFakePicture1.companyProductConsume = companyProductConsume;
                        JPA.em().persist(antiFakePicture1);
                        pictures.add(antiFakePicture1);
                    }

                    if (companyProductNode.get("picture2") != null && companyProductNode.get("picture2").asLong() != 0) {
                        Long picture2 = companyProductNode.get("picture2").asLong();
                        AntiFakePicture antiFakePicture2 = JPA.em().find(AntiFakePicture.class, picture2);
                        antiFakePicture2.companyProductConsume = companyProductConsume;
                        JPA.em().persist(antiFakePicture2);
                        pictures.add(antiFakePicture2);

                    }
                    Logger.info(String.valueOf(pictures.size()));
                    if (!pictures.isEmpty()) {
                        companyProductConsume.antiFakePictures.addAll(pictures);
                        Logger.info("add antifakepictures success");
                    }
                    JPA.em().persist(companyProductConsume);
                }
            }
            //use prestore charge
            if (companyAccountConfig.isAccount == true && companyAccountConfig.prestorePoint == true) {
                JPA.em().persist(new CompanyAccount(manager.company, manager, 0 - Long.parseLong(companyAccountConfig.accountValue), companyCustomer.customer.name));
            }

            JsonNode bookingsNode = json.get("bookings");
            if (bookingsNode != null) {
                Iterator iterator = bookingsNode.iterator();
                while (iterator.hasNext()) {
                    JsonNode bookingNode = (JsonNode)iterator.next();
                    Long bookingId = bookingNode.get("bookingId").asLong();
                    Booking booking = JPA.em().find(Booking.class, bookingId);
                    booking.consume = consume;
                    booking.status = 4;
                    JPA.em().persist(booking);
                }
            }

            return ok(toJson(consume));
        }
    }

    @Transactional
    public static Result getAllBookings(Long managerId, String customerName, Long after)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryAllBookingForCustomerName");
        query.setParameter("name", customerName);
        query.setParameter("company", manager.company);
        query.setParameter("after", new Date(after));
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result getNewBookings(Long managerId, String customerName, Long after)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryBookingForCustomerName");
        query.setParameter("name", customerName);
        query.setParameter("company", manager.company);
        query.setParameter("status", 0);
        query.setParameter("after", new Date(after));
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result getRejectedBookings(Long managerId, String customerName, Long after)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryBookingForCustomerName");
        query.setParameter("name", customerName);
        query.setParameter("company", manager.company);
        query.setParameter("status", 2);
        query.setParameter("after", new Date(after));
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result getAcceptedBookings(Long managerId, String customerName, Long after)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("queryBookingForCustomerName");
        query.setParameter("name", customerName);
        query.setParameter("company", manager.company);
        query.setParameter("status", 1);
        query.setParameter("after", new Date(after));
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result getLatestBookings(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
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

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -3);
        query = JPA.em().createNamedQuery("queryLatestBooking");
        query.setParameter("company", manager.company);
        query.setParameter("someTime", calendar.getTime());
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result getBookingsOfToday(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        Date earlier = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 2);
        Date later = calendar.getTime();
        Query query = JPA.em().createNamedQuery("queryBookingsOfToday");
        query.setParameter("company", manager.company);
        query.setParameter("earlier", earlier);
        query.setParameter("later", later);
        List<Booking> bookings = query.getResultList();
        return ok(toJson(bookings));
    }

    @Transactional
    public static Result getBooking(Long managerId, Long bookingId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Booking booking = JPA.em().find(Booking.class, bookingId);
        if (booking == null) {
            return notFound();
        } else {
            return ok(toJson(booking));
        }
    }

    @Transactional
    public static Result postBooking(Long managerId)
    {
        if (!isVersionMatch("2")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();
        String customerName = json.get("customerName").asText();
        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", customerName);
        List<Customer> customers = query.getResultList();
        Customer customer = null;
        CompanyCustomer companyCustomer = null;
        if (customers.isEmpty()) {
            String password = String.format("%06d", new Random().nextInt(999999));
            new Message(customerName, password, "90982").start();
            customer = new Customer(customerName, password);
            // create CompanyCustomer
            companyCustomer = new CompanyCustomer(manager.company, customer);
            JPA.em().persist(customer);
            JPA.em().persist(companyCustomer);
        } else {
            customer = customers.get(0);
            query = JPA.em().createNamedQuery("queryCompanyCustomer");
            query.setParameter("company", manager.company);
            query.setParameter("customer", customer);
            List<CompanyCustomer> companyCustomers = query.getResultList();
            if (companyCustomers.isEmpty()) {
                companyCustomer = new CompanyCustomer(manager.company, customer);
                JPA.em().persist(companyCustomer);
            } else {
                companyCustomer = companyCustomers.get(0);
            }
        }

        Date bookingTime = new Date(json.get("bookingTime").asLong());
        Booking booking = new Booking(companyCustomer, bookingTime);

        JsonNode jbookingProducts = json.get("bookingProducts");
        Iterator iterator = jbookingProducts.iterator();
        while (iterator.hasNext()) {
            JsonNode jbookingProduct = (JsonNode)iterator.next();
            CompanyProduct companyProduct = JPA.em().find(CompanyProduct.class, jbookingProduct.get("companyProductId").asLong());
            int quantity = jbookingProduct.get("quantity").asInt();
            booking.addBookingProduct(companyProduct, quantity);
        }
        JPA.em().persist(booking);
        return ok(toJson(booking));
    }

    @Transactional
    public static Result acceptBooking(Long managerId, Long bookingId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Booking booking = JPA.em().find(Booking.class, bookingId);
        JsonNode json = request().body().asJson();
        Long bookingTime = json.get("bookingTime").asLong();
        String companyNote = json.get("companyNote").asText();
        booking.prevTime = booking.bookingTime;
        booking.company_note = companyNote;
        booking.bookingTime = new Date(bookingTime);
        booking.status = 1;
        JPA.em().persist(booking);
        return ok(toJson(booking));
    }

    @Transactional
    public static Result rejectBooking(Long managerId, Long bookingId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        Booking booking = JPA.em().find(Booking.class, bookingId);
        booking.status = -1;
        JPA.em().persist(booking);
        return ok(toJson(booking));
    }

    @Transactional
    public static Result getLowLevelManagers(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (manager.level != 0) {
            return badRequest("only support super manager");
        }
        Query query = JPA.em().createNamedQuery("queryLowLevelManagers");
        query.setParameter("company", manager.company);
        List<Manager> managers = query.getResultList();
        return ok(toJson(managers));
    }

    @Transactional
    public static Result postLowLevelManagers(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (manager.level != 0) {
            return badRequest("only support super manager");
        }
        JsonNode json = request().body().asJson();
        String managerName = json.get("name").asText();
        String password = json.get("password").asText();
        int level = json.get("level").asInt();
        if (level != 1 && level != 2) {
            return badRequest("only level1 and level2 manager can be added!");
        }
        Manager lowLevelManager = new Manager(manager.company, managerName, password, level);
        JPA.em().persist(lowLevelManager);
        return ok(toJson(lowLevelManager));
    }

    @Transactional
    public static Result putLowLevelManagers(Long managerId, Long lowLevelManagerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (manager.level != 0) {
            return badRequest("only support super manager");
        }

        Manager lowLevelManager = JPA.em().find(Manager.class, lowLevelManagerId);
        if (!lowLevelManager.company.id.equals(manager.company.id)) {
            return badRequest("not same company");
        }
        if (lowLevelManager.level == 0) {
            return badRequest("only level1 or level2 manager can be modified");
        }
        JsonNode json = request().body().asJson();
        String managerName = json.get("name").asText();
        int level = json.get("level").asInt();
        if (level != 1 && level != 2) {
            return badRequest("only level1 and level2 manager can be modified!");
        }
        boolean isActive = json.get("isActive").asBoolean();
        lowLevelManager.name = managerName;
        lowLevelManager.level = level;
        lowLevelManager.isActive = isActive;
        JPA.em().persist(lowLevelManager);
        return ok(toJson(lowLevelManager));
    }

    @Transactional
    public static Result resetPasswordForLowLevelManager(Long managerId, Long lowLevelManagerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (manager.level != 0) {
            return badRequest("only support super manager");
        }
        Manager lowLevelManager = JPA.em().find(Manager.class, lowLevelManagerId);
        if (!lowLevelManager.company.id.equals(manager.company.id)) {
            return badRequest("not same company");
        }
        if (lowLevelManager.level == 0) {
            return badRequest("only level1 or level2 manager can be modified");
        }
        JsonNode json = request().body().asJson();
        String password = json.get("password").asText();
        JPA.em().persist(lowLevelManager.resetPassword(password));
        return ok(toJson(lowLevelManager));
    }

    @Transactional
    public static Result recharge(Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Manager manager = JPA.em().find(Manager.class, managerId);
        if (!isTokenValid(manager.token, manager.expiredWhen)) {
            return unauthorized();
        }
        if (manager.level != 0) {
            return badRequest("only support super manager");
        }

        JsonNode json = request().body().asJson();
        String totalFee = json.get("charge").asText();

        CompanyRecharge recharge = new CompanyAliRecharge(manager.company, totalFee);
        JPA.em().persist(recharge);
        CompanyRechargeLog record = new CompanyRechargeLog(manager, recharge);
        JPA.em().persist(record);

        String out_trade_no = recharge.id.toString();
        String subject = new String("company_charge");
        String body = new String("company_charge_body");

        Map<String, String> sParaTemp = new HashMap<String, String>();
        sParaTemp.put("service", AlipayConfig.service);
        sParaTemp.put("partner", AlipayConfig.partner);
        sParaTemp.put("seller_id", AlipayConfig.seller_id);
        sParaTemp.put("payment_type", AlipayConfig.payment_type);
        sParaTemp.put("notify_url", AlipayConfig.notify_url);
        sParaTemp.put("return_url", AlipayConfig.return_url);
        sParaTemp.put("anti_phishing_key", AlipayConfig.anti_phishing_key);
        sParaTemp.put("exter_invoke_ip", AlipayConfig.exter_invoke_ip);
        sParaTemp.put("out_trade_no", out_trade_no);
        sParaTemp.put("subject", subject);
        sParaTemp.put("total_fee", totalFee);
        sParaTemp.put("body", body);

        Map<String, String> sPara = AlipaySubmit.buildRequestPara(sParaTemp);

        return ok(toJson(sPara));
    }

    @Transactional
    public static Result handleAlipayNotify()
    {
        Logger.info("handleAlipayNotify");
        Map<String,String> params = new HashMap<String,String>();
        Map requestParams = request().queryString();
        for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }
        boolean verify_result = AlipayNotify.verify(params);
        if (verify_result) {
            if(requestParams.get("trade_status").equals("TRADE_FINISHED")){
            } else if (requestParams.get("trade_status").equals("TRADE_SUCCESS")){
                /*
                CompanyRecharge companyRecharge = JPA.em().find(CompanyRecharge.class, Long.valueOf(out_trade_no));
                if (companyRecharge.status != 1 && seller_id.equals(AlipayConfig.seller_id) && Double.valueOf(total_fee).equals(Double.valueOf(companyRecharge.totalFee))) {
                    companyRecharge.tradeNo = trade_no;
                    companyRecharge.tradeStatus = trade_status;
                    companyRecharge.status = 1;
                    JPA.em().persist(companyRecharge);
                    Company company = companyRecharge.company;
                    Double rechargeAmount = Double.valueOf(total_fee) * 100;
                    company.balance += rechargeAmount.longValue();
                    JPA.em().persist(company);
                }
                */
            }
            return ok("success");
        } else {
            return ok("fail");
        }
    }

    @Transactional
    public static Result handleAlipayReturn(String out_trade_no, String trade_no, String trade_status, String total_fee, String seller_id)
    {
        Logger.info("handleAlipayReturn");
        Logger.info("out_trade_no: " + out_trade_no);
        Logger.info("trade_no: " + trade_no);
        Logger.info("trade_status: " + trade_status);
        Logger.info("total_fee: " + total_fee);
        Logger.info("seller_id: " + seller_id);
        Map<String,String> params = new HashMap<String,String>();
        Map requestParams = request().queryString();
        for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }
        boolean verify_result = AlipayNotify.verify(params);
        if (verify_result) {
            if(trade_status.equals("TRADE_FINISHED") || trade_status.equals("TRADE_SUCCESS")){
                CompanyRecharge companyRecharge = JPA.em().find(CompanyAliRecharge.class, Long.valueOf(out_trade_no));
                if (companyRecharge.status != 1 && seller_id.equals(AlipayConfig.seller_id) && Double.valueOf(total_fee).equals(Double.valueOf(companyRecharge.totalFee))) {
                    companyRecharge.tradeNo = trade_no;
                    companyRecharge.tradeStatus = trade_status;
                    companyRecharge.status = 1;
                    JPA.em().persist(companyRecharge);
                    Company company = companyRecharge.company;
                    Double rechargeAmount = Double.valueOf(total_fee) * 100;
//                    company.balance += rechargeAmount.longValue();
                    JPA.em().persist(company);
                    CompanyAccount companyAccount = new CompanyAccount(company, rechargeAmount.longValue(), (String)params.get("buyer_email"));
                    JPA.em().persist(companyAccount);
                }
            }
            return ok("验证成功");
        } else {
            return ok("验证失败");
        }
    }
}

