package com.gym.fitness.module.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MemberAccountRequest {
    @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{3,49}$", message = "用户名须以字母开头，由4-50位字母、数字或_.-组成")
    private String username;

    @NotBlank @Size(min = 8, max = 72)
    private String password;
}
