package com.gym.fitness.module.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VenueRequest {

    @NotBlank(message = "场馆名称不能为空")
    @Size(max = 100, message = "场馆名称不能超过100个字符")
    private String name;

    @NotBlank(message = "场馆编码不能为空")
    @Size(max = 50, message = "场馆编码不能超过50个字符")
    private String code;

    private Long parentVenueId;

    @Size(max = 200, message = "地址不能超过200个字符")
    private String address;

    @Size(max = 20, message = "联系电话不能超过20个字符")
    private String contactPhone;

    @Email(message = "联系邮箱格式不正确")
    @Size(max = 100, message = "联系邮箱不能超过100个字符")
    private String contactEmail;

    private String status;
}
