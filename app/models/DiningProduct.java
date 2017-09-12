package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("dining")
public class DiningProduct extends Product {
    public DiningProduct() {
    }

    public DiningProduct(String name, String unit, String model, String description) {
        super(name, unit, model, description);
    }
}

