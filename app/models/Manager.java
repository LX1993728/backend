package models;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mindrot.jbcrypt.BCrypt;

@Entity
@NamedQueries({
    @NamedQuery(
        name="managerAuthQuery",
        query="SELECT m FROM Manager m WHERE m.name = :managerName AND m.company.name = :companyName"
    ),
    @NamedQuery(
        name="findManagerByMId",
        query="SELECT m FROM Manager m WHERE m.mId = :mId"
    ),
    @NamedQuery(
        name="queryLowLevelManagers",
        query="SELECT m FROM Manager m WHERE m.company = :company AND m.level > 0"
    ),
})
public class Manager {

    @Id @GeneratedValue
    public Long id;

//    @JsonIgnore
    @Column(unique = true, nullable = false)
    public Long mId;


    public String name;

    @JsonIgnore
    public String password;

    @ManyToOne
    public Company company;

    public int level;   // 0: super, 1: level1, 2: level2

    @JsonIgnore
    public boolean isActive;

    @OneToMany(mappedBy="issuedBy")
    @JsonIgnore
    public List<Batch> batches;

    @OneToMany(mappedBy="issuedBy")
    @JsonIgnore
    public List<Ticket> issuedTickets;

    @JsonIgnore
    public String token;

    @JsonIgnore
    public Date expiredWhen;

    public Manager() {
    }

    public Manager(Company company, String name, String password, int level) {
        this.issuedTickets = new ArrayList<Ticket>();
        this.company = company;
        this.name = name;
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
        this.level = level;
        this.isActive = true;
        this.mId = new Long(new Random().nextInt(9999));
    }

    public Manager resetPassword(String password) {
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
        return this;
    }
}

