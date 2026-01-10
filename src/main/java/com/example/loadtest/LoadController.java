package com.example.loadtest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Random;

@RestController
@RequestMapping("/api")
@Tag(name = "Load Test", description = "Endpoints for generating load")
public class LoadController {

    private final Random random = new Random();

    @GetMapping("/load")
    @Operation(summary = "Simple Load", description = "Returns a simple string. Fast response for high RPS testing.")
    public String checkLoad() {
        return "Load Test OK: " + System.currentTimeMillis();
    }

    @GetMapping("/cpu")
    @Operation(summary = "CPU Stress", description = "Performs a small calculation to simulate CPU load.")
    public String cpuStress() {
        double result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sqrt(random.nextDouble());
        }
        return "CPU Load Task Completed. Result: " + result;
    }

    @GetMapping("/memory")
    @Operation(summary = "Memory Stress", description = "Allocates a byte array to simulate memory usage.")
    public String memoryStress() {
        byte[] array = new byte[1024 * 1024]; // 1MB allocation
        random.nextBytes(array);
        return "Allocated 1MB of data.";
    }
}
