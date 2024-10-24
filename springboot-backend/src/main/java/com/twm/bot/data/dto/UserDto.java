package com.twm.bot.data.dto;

import com.twm.bot.model.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class UserDto {
    private String email;

    public static UserDto from(User user) {
        return UserDto.builder().email(user.getEmail()).build();
    }
}
