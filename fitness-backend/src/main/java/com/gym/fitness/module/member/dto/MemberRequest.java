package com.gym.fitness.module.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MemberRequest {

    @NotBlank(message = "会员编号不能为空")
    @Size(max = 50, message = "会员编号不能超过50个字符")
    private String memberNo;

    @NotBlank(message = "会员姓名不能为空")
    @Size(max = 50, message = "会员姓名不能超过50个字符")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^[0-9+\\- ]{6,20}$", message = "手机号格式不正确")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    @Size(max = 18, message = "证件号码不能超过18个字符")
    private String idCard;

    private String gender;
    private LocalDate birthday;

    @Size(max = 200, message = "地址不能超过200个字符")
    private String address;

    private Long salesUserId;
    private Long coachId;
    private Long venueId;
    private String status;
    private Boolean blacklistFlag;
}
