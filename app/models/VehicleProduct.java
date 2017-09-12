package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("vehicle")
public class VehicleProduct extends Product {

    public String eanCode;

    public String materialCode;

    public String manufacturer;

    public String adaptableVehicle;

    public Long suggestedRetailPrice;


    public VehicleProduct() {
    }

    public VehicleProduct(String name, String unit, String model, String description, String eanCode, String materialCode, String manufacturer, String adaptableVehicle, Long suggestedRetailPrice) {
        super(name, unit, model, description);
        this.eanCode = eanCode;
        this.materialCode = materialCode;
        this.manufacturer = manufacturer;
        this.adaptableVehicle = adaptableVehicle;
        this.suggestedRetailPrice = suggestedRetailPrice;
    }
}

