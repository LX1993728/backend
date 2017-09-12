package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
public abstract class Expired {

    @Id @GeneratedValue
    public Long id;

    public Expired() {
    }
}

