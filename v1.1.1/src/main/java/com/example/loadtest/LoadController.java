package com.example.loadtest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Load Test", description = "Endpoints for generating load")
public class LoadController {

    @GetMapping("/load")
    @Operation(summary = "Simple Load", description = "Returns a simple string. Fast response for high RPS testing.")
    public String checkLoad() {
        return "Load Test OK: " + System.currentTimeMillis();
    }

    @GetMapping("/cpu")
    @Operation(summary = "CPU Stress", description = "Performs a heavy calculation loop. Default 1M iterations.")
    public String cpuStress(
            @io.swagger.v3.oas.annotations.Parameter(description = "Number of loop iterations") @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1000000") int iterations) {

        double result = 0;
        // Use a loop variable for math to avoid synchronization locks of Random
        for (int i = 0; i < iterations; i++) {
            // Expensive math operations
            result += Math.tan(Math.atan(Math.sqrt(i * 1.01)));
        }
        return "CPU Load Task Completed. Iterations: " + iterations + ", Result: " + result;
    }

    @GetMapping("/memory")
    @Operation(summary = "Memory Stress", description = "Allocates a byte array to simulate memory usage.")
    public String memoryStress() {
        byte[] array = new byte[1024 * 1024]; // 1MB allocation
        // Use ThreadLocalRandom to avoid synchronized Random contention
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(array);
        return "Allocated 1MB of data.";
    }

    @GetMapping("/stats")
    @Operation(summary = "Get System Stats", description = "Returns node name, pod name, CPU load, and memory usage.")
    public java.util.Map<String, Object> getStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();

        // Hostname / Pod Name
        String podName = System.getenv("HOSTNAME");
        if (podName == null)
            podName = "Unknown-Pod";

        // Node Name (passed via Downward API)
        String nodeName = System.getenv("NODE_NAME");
        if (nodeName == null)
            nodeName = "Unknown-Node";

        // OS Bean for CPU/Memory
        java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory
                .getOperatingSystemMXBean();
        double systemLoad = osBean.getSystemLoadAverage(); // might be -1 on Windows
        // For a more robust CPU usage in container, we might just send load average or
        // random variation if not available

        // Memory
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();

        stats.put("nodeName", nodeName);
        stats.put("podName", podName);
        stats.put("cpuLoad", systemLoad < 0 ? 0 : systemLoad); // fallback to 0 if not available
        stats.put("usedMemoryBytes", usedMemory);
        stats.put("maxMemoryBytes", maxMemory);
        stats.put("timestamp", System.currentTimeMillis());

        return stats;
    }
}
