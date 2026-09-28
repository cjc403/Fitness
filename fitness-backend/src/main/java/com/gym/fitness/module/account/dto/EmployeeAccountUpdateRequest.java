package com.gym.fitness.module.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmployeeAccountUpdateRequest {
    @NotBlank @Size(max = 50)
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Email @Size(max = 100)
    private String email;

    /** 角色白名单只在 AccountService.STAFF_ROLES / MANAGER_CREATABLE_ROLES 维护，避免多处重复导致不一致。 */
    @NotBlank
    private String roleCode;

    private Long venueId;
}
