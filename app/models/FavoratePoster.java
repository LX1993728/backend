package models;

import java.util.*;

import javax.persistence.*;

@Entity
@DiscriminatorValue("poster")
@NamedQueries({
  @NamedQuery(
     name="queryFavoratePosterByCustomerAndPoster",
     query="SELECT f FROM FavoratePoster f WHERE f.customer = :customer AND f.poster = :poster"),
})
public class FavoratePoster extends Favorate {

    @ManyToOne
    public Poster poster;

    public FavoratePoster() {
    }

    public FavoratePoster(Customer customer, Poster poster) {
        super(customer);
        this.poster = poster;
    }
}

