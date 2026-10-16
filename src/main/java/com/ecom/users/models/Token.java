package com.ecom.users.models;

import jakarta.persistence.Column;
import com.ecom.common.models.JpaBaseModel;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Token extends JpaBaseModel {
    @Column(name = "token_value", unique = true, nullable = false) // "value" is a reserved word in H2
    private String value;
    @ManyToOne
    private User user;
    private long expiryAt;
}
