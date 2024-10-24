package com.twm.bot.data.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginForm {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;

    private String provider = "NATIVE";

    @NotBlank
    private String captchaId;

    @NotBlank
    private String captcha;
}
