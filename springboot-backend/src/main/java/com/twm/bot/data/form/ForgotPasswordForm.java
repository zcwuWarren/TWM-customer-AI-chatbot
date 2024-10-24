package com.twm.bot.data.form;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordForm {

    @NotEmpty(message = "主帳號不可為空")
    private String mainAccount;

    @NotEmpty(message = "驗證碼ID不可為空")
    private String captchaId;

    @NotEmpty(message = "驗證碼不可為空")
    private String captcha;
}
