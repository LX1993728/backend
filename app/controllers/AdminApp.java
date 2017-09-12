package controllers;

import models.*;
import org.apache.commons.mail.SimpleEmail;
import utils.Util;
import views.html.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mindrot.jbcrypt.BCrypt;

import play.mvc.*;
import play.libs.*;
import play.db.jpa.*;
import play.Logger;
import static play.libs.Json.toJson;

import javax.persistence.*;

import java.util.*;
import java.io.*;
import java.security.MessageDigest;
import org.apache.commons.codec.binary.Base64;

public class AdminApp extends Controller {
    private static final String PLATFORM_VERSION = "3.5";
    private static final int MAX_LOGO_LENGTH = 100 * 1024;

    @Transactional
    public static Result adminLogin() throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        JsonNode json = request().body().asJson();
        String name = json.findPath("name").asText();
        String password = json.findPath("password").asText();

        Query query = JPA.em().createNamedQuery("adminAuthQuery");
        query.setParameter("adminName", name);
        List<Admin> admins = query.getResultList();
        if (admins.isEmpty()) {
            return unauthorized("unauthorized");
        } else {
            Admin admin = admins.get(0);
            if (BCrypt.checkpw(password, admin.password)) {
                Calendar calendar = Calendar.getInstance();
                MessageDigest messageDigest = MessageDigest.getInstance("MD5");
                messageDigest.update((UUID.randomUUID().toString().toUpperCase() + admin.name + calendar.getTimeInMillis()).getBytes());
                admin.token = Base64.encodeBase64String(messageDigest.digest());
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                admin.expiredWhen = calendar.getTime();
                EntityManager em = JPA.em();
                em.persist(admin);

                JsonNode jadmin = toJson(admin);
                ObjectNode o = (ObjectNode)jadmin;
                o.put("token", admin.token);
                return ok(o);
            } else {
                return unauthorized("unauthorized");
            }
        }
    }

    @Transactional
    public static Result adminLogout(Long id) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, id);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Calendar calendar = Calendar.getInstance();
        admin.expiredWhen = calendar.getTime();
        EntityManager em = JPA.em();
        em.persist(admin);
        return ok();
    }

    @Transactional
    public static Result postCompanyAccountConfigs(Long adminId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);

        Query query = JPA.em().createNamedQuery("queryCompanyAccountConfig");
        query.setParameter("company", company);
        List<CompanyAccountConfig> companyAccountConfigs = query.getResultList();
        CompanyAccountConfig companyAccountConfig = null;
        if (!companyAccountConfigs.isEmpty()) {
            JPA.em().remove(companyAccountConfigs.get(0));
        }

        JsonNode json = request().body().asJson();
        boolean isAccount = json.get("isAccount").asBoolean();

        if (isAccount == true) {
            boolean useticketPoint = json.get("useticketPoint").asBoolean();
            boolean prestorePoint = json.get("prestorePoint").asBoolean();
            boolean confirmcodePoint = json.get("confirmcodePoint").asBoolean();
            String accountValue = json.get("accountValue").asText();
            companyAccountConfig = new CompanyAccountConfig(company, isAccount, useticketPoint, prestorePoint, confirmcodePoint, accountValue);

        } else {
            companyAccountConfig = new CompanyAccountConfig(company, isAccount);
        }
        JPA.em().persist(companyAccountConfig);
        return ok(toJson(companyAccountConfig));
    }

    @Transactional
    @BodyParser.Of(value = BodyParser.AnyContent.class, maxLength = MAX_LOGO_LENGTH)
    public static Result postCompanies(Long id) throws Exception
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, id);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }

        Http.MultipartFormData body = request().body().asMultipartFormData();
        Map<String, String[]> form = body.asFormUrlEncoded();
        Logger.info("form.name: " + form.get("name")[0]);
        String companyName = form.get("name")[0];
        double longitude = Double.parseDouble(form.get("longitude")[0]);
        double latitude = Double.parseDouble(form.get("latitude")[0]);
        String address = form.get("address")[0];
        String telephone = form.get("telephone")[0];
        String email = form.get("email")[0];
        String description = form.get("description")[0];
        Company company = new Company(companyName, longitude, latitude, address, telephone, email, description);

        Http.MultipartFormData.FilePart logoFilePart = body.getFile("logo");
        if (logoFilePart != null) {
            String logoFileName = logoFilePart.getFilename();
            String logoContentType = logoFilePart.getContentType();
            File logoFile = logoFilePart.getFile();
            company.contentType = logoContentType;
            FileInputStream logoStream = new FileInputStream(logoFile);
            int totalLen = (int)logoFile.length();
            byte[] logo = new byte[totalLen];
            int offset = 0;
            while (offset < totalLen) {
                int readLen = logoStream.read(logo, offset, totalLen - offset);
                offset += readLen;
            }
            company.logo = logo;
        }
        JPA.em().persist(company);
        return ok();
    }

    @Transactional
    public static Result getCompanies(Long id) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, id);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Query query = JPA.em().createNamedQuery("findAllCompany");
        List<Company> companies = query.getResultList();
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode an = mapper.createArrayNode();
        Iterator<Company> it = companies.iterator();
        while (it.hasNext()) {
            Company company = it.next();
            JsonNode jcompany = toJson(company);
            ObjectNode o = (ObjectNode)jcompany;
            o.put("balance", company.balance);
            an.add(o);
        }
        return ok(an);
    }

    @Transactional
    public static Result putCompanies(Long adminId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);
        JsonNode json = request().body().asJson();
        
        JsonNode rechargeNode = json.get("recharge");
        if (rechargeNode != null) {   // recharge
            Long recharge = rechargeNode.asLong();
            CompanyAccount account = new CompanyAccount(company, admin, recharge, "原宝科技");
            company.addAccount(account);
            JPA.em().persist(company);
            ObjectNode jcompany = (ObjectNode)toJson(company);
            jcompany.put("balance", company.balance);
            return ok(jcompany);
        } else {
            String companyName = json.get("name").asText();
            double longitude = json.get("longitude").asDouble();
            double latitude = json.get("latitude").asDouble();
            String address = json.get("address").asText();
            String telephone = json.get("telephone").asText();
            String email = json.get("email").asText();
            String description = json.get("description").asText();
            int status = json.get("status").asInt();
            if(2 == status){
                Util util = new Util();
                String password = util.GenerateRandomNumber(6);
                company.status = 1;
                JPA.em().persist(company);
                Manager manager = new Manager(company, "admin", password, 0); //审核通过后的公司自动添加一个默认为admin的管理员
                JPA.em().persist(manager);
                try{
                    SimpleEmail postEmail = new SimpleEmail();
                    postEmail.setHostName("domino.elable.cn");
                    postEmail.setSmtpPort(30);
                    postEmail.setAuthentication("support", "yuanbaoSp100085");
                    postEmail.addTo(email, "尊敬的用户");
                    postEmail.setFrom("support@elable.cn", "原宝科技");
                    postEmail.setSubject("You new password");
                    postEmail.setMsg("你的审核已通过!公司ID：" + companyId + "管理员ID：" + manager.id + "密码：" + password + "\n" + "https://company.elable.cn");
                    postEmail.send();
                }catch (Exception e){
                    return status(200,"发送邮件失败");
                }
                return ok(toJson(company));
            }else{
                company.name = companyName;
                company.longitude = longitude;
                company.latitude = latitude;
                company.address = address;
                company.telephone = telephone;
                company.email = email;
                company.description = description;
                company.status = status;
                JPA.em().persist(company);
                return ok(toJson(company));
            }
        }
    }

    @Transactional
    public static Result putManufacturers(Long adminId, Long companyId){
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        JsonNode jsonNode = request().body().asJson();
        int companyType = jsonNode.get("type").asInt();

        Company company = JPA.em().find(Company.class, companyId);
        company.companyType = companyType;
        JPA.em().persist(company);
        return ok();
    }

    @Transactional
    public static Result postPortalCompanies(){
        JsonNode json = request().body().asJson();
        String name = json.get("name").asText();
        String address = json.get("address").asText();
        String telephone = json.get("telephone").asText();
        String email =json.get("email").asText();
        Company company = new Company(name,address,telephone,email);
        JPA.em().persist(company);
        return ok();
    }

    @Transactional
    @BodyParser.Of(value = BodyParser.AnyContent.class, maxLength = MAX_LOGO_LENGTH)
    public static Result postLogoOfCompany(Long adminId, Long companyId) throws Exception
    {
        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);
        Http.MultipartFormData body = request().body().asMultipartFormData();
        List<Http.MultipartFormData.FilePart> files = body.getFiles();
        
        Http.MultipartFormData.FilePart logoFilePart = body.getFile("logo");
        if (logoFilePart != null) {
            String logoFileName = logoFilePart.getFilename();
            String logoContentType = logoFilePart.getContentType();
            File logoFile = logoFilePart.getFile();
            company.contentType = logoContentType;
            FileInputStream logoStream = new FileInputStream(logoFile);
            int totalLen = (int)logoFile.length();
            byte[] logo = new byte[totalLen];
            int offset = 0;
            while (offset < totalLen) {
                int readLen = logoStream.read(logo, offset, totalLen - offset);
                offset += readLen;
            }
            company.logo = logo;
            JPA.em().persist(company);
            return ok();
        } else {
            return badRequest("upload failed!");
        }
    }

    @Transactional
    public static Result getManagers(Long adminId, Long companyId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Company company = JPA.em().find(Company.class, companyId);
        return ok(toJson(company.managers));
    }

    @Transactional
    public static Result postManagers(Long id, Long companyId) 
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, id);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();
        Company company = JPA.em().find(Company.class, companyId);
        String managerName = json.get("name").asText();
        String password = json.get("password").asText();
        int level = json.get("level").asInt();
        Manager manager = new Manager(company, managerName, password, level);
        JPA.em().persist(manager);
        return ok(toJson(manager));
    }

    @Transactional
    public static Result putManagers(Long adminId, Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Manager manager = JPA.em().find(Manager.class, managerId);
        JsonNode json = request().body().asJson();
        String managerName = json.get("name").asText();
        int level = json.get("level").asInt();
        boolean isActive = json.get("isActive").asBoolean();
        manager.name = managerName;
        manager.level = level;
        manager.isActive = isActive;
        JPA.em().persist(manager);
        return ok(toJson(manager));
    }

    @Transactional
    public static Result resetPasswordForManager(Long adminId, Long managerId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        Manager manager = JPA.em().find(Manager.class, managerId);
        JsonNode json = request().body().asJson();
        String password = json.get("password").asText();
        JPA.em().persist(manager.resetPassword(password));
        return ok(toJson(manager));
    }

    @Transactional
    public static Result resetPasswordForCustomer(Long adminId)
    {
        if (!isVersionMatch("1")) {
            return status(412, "version mismatch");
        }

        Admin admin = JPA.em().find(Admin.class, adminId);
        if (!isTokenValid(admin.token, admin.expiredWhen)) {
            return unauthorized();
        }
        JsonNode json = request().body().asJson();
        String customerName = json.findPath("name").asText();
        String password = json.findPath("password").asText();

        Query query = JPA.em().createNamedQuery("findCustomersByName");
        query.setParameter("customerName", customerName);
        List<Customer> customers = query.getResultList();
        if (customers.isEmpty()) {
            return notFound("user not found");
        } else {
            Customer customer = customers.get(0);
            JPA.em().persist(customer.resetPassword(password));
            return ok(toJson(customer));
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

