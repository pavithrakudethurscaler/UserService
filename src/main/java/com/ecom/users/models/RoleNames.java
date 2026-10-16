package com.ecom.users.models;

/** Role values stored in Role.value and returned to other services in the token validation response. */
public final class RoleNames {
    /** Every registered user */
    public static final String CUSTOMER = "CUSTOMER";
    /** Can manage the catalog and fulfil orders */
    public static final String ADMIN = "ADMIN";

    private RoleNames() {
    }
}
