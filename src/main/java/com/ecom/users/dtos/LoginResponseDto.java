package com.ecom.users.dtos;

import com.ecom.users.models.Token;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponseDto {
    private String token;
    private long expiryAt;
    private UserResponseDto user;

    public static LoginResponseDto from(Token token) {
        LoginResponseDto responseDto = new LoginResponseDto();
        responseDto.setToken(token.getValue());
        responseDto.setExpiryAt(token.getExpiryAt());
        responseDto.setUser(UserResponseDto.from(token.getUser()));
        return responseDto;
    }
}
