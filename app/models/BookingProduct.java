package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
public class BookingProduct {

    @Id @GeneratedValue
    public Long id;

    @ManyToOne
    public CompanyProduct companyProduct;

    public int quantity;

    @ManyToOne
    @JsonIgnore
    public Booking booking;

    public BookingProduct() {
    }

    public BookingProduct(CompanyProduct companyProduct, int quantity, Booking booking) {
        this.companyProduct = companyProduct;
        this.quantity = quantity;
        this.booking = booking;
    }
}
