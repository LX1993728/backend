package models;

import java.util.*;
import javax.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
  name value pair for configuration
    name                  type     comments
    CHARGE_RATE           int      RMB (fen)
    BROADCAST_INTERVAL    int      seconds
    NOTIFICATION_INTERVAL int      seconds
*/

@Entity
@NamedQueries({
    @NamedQuery(
        name="queryConfig",
        query="SELECT tc FROM TicketConfig tc WHERE tc.configName = :configName"
    ),
})
public class TicketConfig {

    @Id @GeneratedValue
    public Long id;

    @JsonIgnore
    public String configName;

    @JsonIgnore
    public String configValue;

    public TicketConfig() {
    }

    public TicketConfig(String configName, String configValue) {
        this.configName = configName;
        this.configValue = configValue;
    }
}

