package com.ecom.users.models;

import jakarta.persistence.Column;
import com.ecom.common.models.JpaBaseModel;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "users") // "user" is a reserved word in several databases
public class User extends JpaBaseModel {
    private String name;
    @Column(unique = true, nullable = false)
    private String email;
    private String hashedPassword;
    private boolean emailVerified;
    @ManyToMany(fetch = FetchType.EAGER)
    private List<Role> roles = new ArrayList<>();
}
