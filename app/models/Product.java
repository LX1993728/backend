package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "TYPE", discriminatorType=DiscriminatorType.STRING)
@NamedQueries({
    @NamedQuery(
        name="findProductByKey",
        query="SELECT p FROM Product p WHERE p.name LIKE :key"
    ),
})
public abstract class Product {

    @Id @GeneratedValue
    public Long id;

    public String name;
    public String unit;   // 单位, 份／套
    public String model;   // 规格：10吋／大份／中份／小份
    public String description;


    public String contentType;
    @Lob
    @Basic(fetch=FetchType.LAZY)
    @JsonIgnore
    public byte[] logo;

    @OneToMany(mappedBy = "product")
    @JsonIgnore
    public List<StaticsProductHits> staticsProductHitses;

    public Product() {
    }

    public Product(String name, String unit, String model, String description) {
        this.name = name;
        this.unit = unit;
        this.model = model;
        this.description = description;
    }
}

