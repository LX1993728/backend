package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mindrot.jbcrypt.BCrypt;

@Entity
@NamedQuery(
     name="adminAuthQuery",
     query="SELECT a FROM Admin a WHERE a.name = :adminName"
)
public class Admin {
    @Id @GeneratedValue
    public Long id;

    @Column(unique=true)
    public String name;

    @JsonIgnore
    public String password;

    @JsonIgnore
    public String token;

    @JsonIgnore
    public Date expiredWhen;

    public Admin() {
    }

    public Admin(String name, String password) {
        this.name = name;
        this.password = BCrypt.hashpw(password, BCrypt.gensalt());
    }
}

