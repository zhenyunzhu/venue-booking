package com.venuebooking.module.checkin.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckinRequest {

    @NotBlank(message = "核销码不能为空")
    private String checkinCode;
}
