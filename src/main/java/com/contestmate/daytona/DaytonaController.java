package com.contestmate.daytona;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/daytona")
public class DaytonaController {
    private final DaytonaService daytonaService;

    public DaytonaController(DaytonaService daytonaService) {
        this.daytonaService = daytonaService;
    }

    @PostMapping("/execute")
    public DaytonaExecutionResponse execute(@Valid @RequestBody DaytonaExecutionRequest request) {
        return daytonaService.executePython(request.getCode());
    }
}
