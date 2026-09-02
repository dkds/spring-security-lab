package com.dkds.authserver.organization;

import lombok.*;

import java.io.Serializable;

/// Composite primary key for Membership: (user\_id, org\_id)
@Getter
@Setter
public class MembershipId implements Serializable {
    private Long user;
    private Long organization;
}
