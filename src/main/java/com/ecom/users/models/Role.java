package com.ecom.users.models;

import jakarta.persistence.Column;
import com.ecom.common.models.JpaBaseModel;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Role extends JpaBaseModel {
    @Column(name = "role_value") // "value" is a reserved word in H2
    private String value;
}
