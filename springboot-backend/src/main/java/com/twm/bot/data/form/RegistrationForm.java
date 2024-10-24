package com.twm.bot.data.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import com.twm.bot.validation.ValidPassword; // 引入自訂的密碼驗證註解
import lombok.Data;

@Data
public class RegistrationForm {
    @Email(message = "請輸入有效的電子郵件地址") // 1. 使用 @Email 註解來檢查 email 格式
    @NotBlank(message = "電子郵件地址不能為空") // 2. 使用 @NotBlank 確保這個欄位不為空
    private String email;

    @NotBlank(message = "密碼不能為空") // 3. 確保密碼不為空
    @ValidPassword // 4. 使用自訂的密碼驗證註解
    private String password;

    private String provider = "NATIVE";

    @NotBlank(message = "驗證碼 ID 不能為空") // 5. 驗證碼 ID 不為空
    private String captchaId;

    @NotBlank(message = "驗證碼不能為空") // 6. 驗證碼不為空
    private String captcha;
}
