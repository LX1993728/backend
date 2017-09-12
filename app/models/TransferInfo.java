package models;

import javax.persistence.*;

@Entity
@DiscriminatorValue("transfer")
public class TransferInfo extends Info{

    @OneToOne
    public Transfer transfer;

    public TransferInfo() {

    }

    public TransferInfo(Customer customer, Company company, Transfer transfer) {
        super(customer, company);
        this.transfer = transfer;
    }

}
