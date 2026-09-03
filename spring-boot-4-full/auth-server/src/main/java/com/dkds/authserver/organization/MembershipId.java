package com.dkds.authserver.organization;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/// Composite primary key for Membership: (user_id, org_id)
@Getter
@Setter
@EqualsAndHashCode
public class MembershipId implements Serializable {
    private Long user;
    private Long organization;
}
