package com.ecom.users.dtos;

import com.ecom.users.models.Role;
import com.ecom.users.models.User;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserResponseDto {
    private Long id;
    private String name;
    private String email;
    private List<String> roles;

    public static UserResponseDto from(User user) {
        UserResponseDto responseDto = new UserResponseDto();
        responseDto.setId(user.getId());
        responseDto.setName(user.getName());
        responseDto.setEmail(user.getEmail());
        responseDto.setRoles(user.getRoles().stream().map(Role::getValue).toList());
        return responseDto;
    }
}
