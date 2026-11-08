package com.flashguard.controller;

import com.flashguard.service.DemoDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
@Tag(name = "Demo")
public class DemoController {

    private final DemoDataService demoDataService;

    public DemoController(DemoDataService demoDataService) {
        this.demoDataService = demoDataService;
    }

    @PostMapping("/reset")
    @Operation(summary = "Xóa toàn bộ dữ liệu và tạo lại 2 đợt Flash Sale mẫu (chỉ dùng khi demo)")
    public Map<String, Integer> reset() {
        return demoDataService.reset();
    }
}
