package com.twm.bot.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// 6. 實現 ConstraintValidator 接口，指定我們的註解類（ValidPassword）和需要驗證的字段類型（String）
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }
        // 7. 驗證邏輯：密碼必須至少包含8個字符，且包含大小寫字母和數字
        return password.length() >= 8 &&
                password.matches(".*[A-Z].*") && // 至少包含一個大寫字母
                password.matches(".*[a-z].*") && // 至少包含一個小寫字母
                password.matches(".*\\d.*");    // 至少包含一個數字
    }
}
