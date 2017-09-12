package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("ads")
public class AdvertisementInfo extends Info {

    @ManyToOne
    public Advertisement advertisement;

    public AdvertisementInfo() {
    }

    public AdvertisementInfo(Customer customer, Company company, Advertisement advertisement) {
        super(customer, company);
        this.advertisement = advertisement;
    }
}

