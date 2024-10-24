package com.twm.bot.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 1. @Constraint：標誌這是一個自訂的驗證註解，並指定驗證器類（即 PasswordValidator）
// 2. @Target：指定這個註解可以應用在字段上（FIELD）
// 3. @Retention：指定這個註解應該在運行時保留（RUNTIME）
@Constraint(validatedBy = PasswordValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    // 4. message：當驗證失敗時返回的默認錯誤訊息
    String message() default "密碼最少為8碼，且同時包含至少一個大寫、小寫字母和數字";

    // 5. groups 和 payload：這是 JSR-303 規範的默認屬性，用於更靈活的驗證設置，通常不需要修改
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
