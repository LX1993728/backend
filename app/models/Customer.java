package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mindrot.jbcrypt.BCrypt;
import play.Logger;

@Entity
@NamedQueries({
        @NamedQuery(
                name="findCustomersByName",
                query="SELECT c FROM Customer c WHERE name=:customerName"
        ),
        @NamedQuery(
                name="findCustomersByOpenId",
                query="SELECT c FROM Customer c WHERE c.weChatUser.openid=:openid AND c.weChatUser.isEnabled =true"
        )
 }
)

public class Customer {
    //static final String NEW_USER_CONTENT = "3位确认码：%s，客户端下载：%s";
    static public final String PASSWORD_CHANGE_CODE_CONTENT = "验证码：%s";

    @Id @GeneratedValue
    public Long id;

    @Column(unique=true)
    public String name;

    @JsonIgnore
    public String password;

    @JsonIgnore
    public String confirmCode;

    @JsonIgnore
    public boolean dynamicConfirmCode;

    @OneToMany(mappedBy="customer", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Ticket> tickets;

    @JsonIgnore
    public String token;

    @JsonIgnore
    public Date expiredWhen;

    @JsonIgnore
    public boolean smsEnabled;

    @JsonIgnore
    public Date passwordChangeRequestDate;

    @JsonIgnore
    public int passwordChangeLeft;    // 3 times per day

    @JsonIgnore
    public String passwordChangeCode;

    @JsonIgnore
    @OneToOne(targetEntity = WeChatUser.class)
    public WeChatUser weChatUser;

    @OneToMany(mappedBy="customer", cascade=CascadeType.PERSIST)
    @JsonIgnore
    public List<Favorate> favorates;

    public Customer() {
    }

    public Customer(String name, String password) {
        this.tickets = new ArrayList<Ticket>();
        this.name = name;
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
        this.confirmCode = String.format("%03d", new Random().nextInt(999));
        this.dynamicConfirmCode = false;  // false by default because there is no app install for this customer
        this.smsEnabled = true;
        //String content = String.format("{\"to\":\"%1s\",\"appId\":\"8a48b5514830387801483053f0cb0029\", \"templateId\":\"1\",\"datas\":[\"%2s\",\"%3s\"]}", name, password, this.confirmCode);
        //String content = String.format(NEW_USER_CONTENT, this.confirmCode, "http://www.elable.net/download");
        //Logger.info(content);
        //new Message(name, content).start();
    }

    public Customer addTicket(Ticket ticket) {
        this.tickets.add(ticket);
        return this;
    }

    public Customer changeConfirmCode() {
        this.confirmCode = String.format("%03d", new Random().nextInt(999));
        return this;
    }

    public Customer resetPassword(String password) {
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
        this.passwordChangeCode = null;
        return this;
    }
}

