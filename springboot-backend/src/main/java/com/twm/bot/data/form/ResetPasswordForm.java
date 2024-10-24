package com.twm.bot.data.form;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ResetPasswordForm {
    private String token;

    @NotEmpty(message = "新密碼不可為空")
    private String newPassword;

    @NotEmpty(message = "驗證碼ID不可為空")
    private String captchaId;

    @NotEmpty(message = "驗證碼不可為空")
    private String captcha;


}
