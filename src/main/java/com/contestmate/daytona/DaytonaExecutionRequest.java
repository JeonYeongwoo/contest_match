package com.contestmate.daytona;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class DaytonaExecutionRequest {
    @NotBlank(message = "code is required")
    @Size(max = 50_000, message = "code must not exceed 50000 characters")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
