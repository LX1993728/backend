package models;

import java.util.*;

import javax.persistence.*;
import org.hibernate.annotations.Index;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class CompanyProductPrice {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    @JsonIgnore
    public CompanyProduct companyProduct;

    public Long price;

    @JsonIgnore
    public Date priceWhen;

    @ManyToOne
    @JsonIgnore
    public Manager setBy;

    public CompanyProductPrice() {
    }

    public CompanyProductPrice(CompanyProduct companyProduct, Long price, Manager manager) {
        this.companyProduct = companyProduct;
        this.price = price;
        Calendar calendar = Calendar.getInstance();
        this.priceWhen = calendar.getTime();
        this.setBy = manager;
    }
}

