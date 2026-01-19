package com.example.loadtest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
@Tag(name = "Load Test", description = "Endpoints for generating load")
public class LoadController {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private String getPodName() {
        String podName = System.getenv("HOSTNAME");
        if (podName == null) {
            try {
                podName = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                podName = "Unknown";
            }
        }
        return podName;
    }

    @GetMapping("/load/start")
    @Operation(summary = "Start Load", description = "Starts continuous CPU/Memory stress.")
    public String startLoad(
            @RequestParam(defaultValue = "cpu") String type,
            @RequestParam(defaultValue = "1") int cores,
            @RequestParam(defaultValue = "1024") int memoryMB) {

        if (running.get()) {
            // Stop previous to restart with new params (simple reset logic)
            stopLoad();
        }
        running.set(true);

        if ("cpu".equalsIgnoreCase(type)) {
            // Cap cores at available processors to prevent total freeze if requested >
            // available
            int available = Runtime.getRuntime().availableProcessors();
            int targetCores = Math.min(cores, available);
            if (targetCores < 1)
                targetCores = 1;

            for (int i = 0; i < targetCores; i++) {
                executor.submit(() -> {
                    while (running.get()) {
                        double result = 0;
                        for (int j = 0; j < 1000; j++)
                            result += Math.tan(Math.atan(Math.sqrt(j * 1.01)));
                    }
                });
            }
            return "CPU Stress Scenarios Started on " + targetCores + " cores (Requested: " + cores + "). Pod: "
                    + getPodName();
        } else {
            // Memory stress
            int targetBytes = memoryMB * 1024 * 1024;
            executor.submit(() -> {
                List<byte[]> memory = new ArrayList<>();
                long allocated = 0;
                while (running.get() && allocated < targetBytes) {
                    try {
                        int chunkSize = 10 * 1024 * 1024; // 10MB chunks
                        if (allocated + chunkSize > targetBytes)
                            chunkSize = (int) (targetBytes - allocated);

                        memory.add(new byte[chunkSize]);
                        allocated += chunkSize;
                        Thread.sleep(500); // Gradual fill
                    } catch (OutOfMemoryError | InterruptedException e) {
                        // Hold what we have
                        try {
                            Thread.sleep(1000);
                        } catch (Exception ex) {
                        }
                    }
                }
                // Keep holding memory until stopped
                while (running.get()) {
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                }
                memory.clear();
            });
            return "Memory Stress Started: Target " + memoryMB + "MB. Pod: " + getPodName();
        }
    }

    @GetMapping("/load/stop")
    @Operation(summary = "Stop Load", description = "Stops all stress tasks.")
    public String stopLoad() {
        boolean wasRunning = running.getAndSet(false);
        return wasRunning ? "Load Stopped on " + getPodName() : "Load was not running on " + getPodName();
    }

    @GetMapping("/status")
    @Operation(summary = "System Status", description = "Returns CPU and Memory usage.")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory
                .getOperatingSystemMXBean();

        status.put("cpuUsage", osBean.getCpuLoad() * 100);
        status.put("memoryUsage", (osBean.getTotalMemorySize() - osBean.getFreeMemorySize()) / (1024 * 1024));
        status.put("totalMemory", osBean.getTotalMemorySize() / (1024 * 1024));
        status.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        status.put("podName", getPodName());
        status.put("loading", running.get());

        return status;
    }
}
