package com.ecom.users.models;

import com.ecom.common.models.JpaBaseModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** One-time token sent in a password reset link. "deleted" is set once it has been used. */
@Getter
@Setter
@Entity
public class PasswordResetToken extends JpaBaseModel {
    @Column(name = "token_value", unique = true, nullable = false) // "value" is a reserved word in H2
    private String value;
    @ManyToOne
    private User user;
    private long expiryAt;
}
