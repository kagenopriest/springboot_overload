package com.example.loadtest;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
@Tag(name = "Host Controller", description = "Manages nodes and distributes load")
public class HostController {

    private final List<String> nodes = new CopyOnWriteArrayList<>();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/nodes")
    @Operation(summary = "Add Node", description = "Register a new node (e.g., http://loadtest-node-headless:8080). Resolves DNS to find all Pod IPs if Headless Service.")
    public String addNode(@RequestBody String nodeUrlInput) {
        String nodeUrl = nodeUrlInput.trim();
        // Ensure protocol exists
        if (!nodeUrl.startsWith("http")) {
            nodeUrl = "http://" + nodeUrl;
        }

        try {
            // Parse URL
            java.net.URI uri = java.net.URI.create(nodeUrl);
            String hostname = uri.getHost();
            int port = uri.getPort();
            if (port == -1)
                port = 80; // Default to 80 if not specified

            // DNS Resolution
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(hostname);
            int addedCount = 0;
            StringBuilder discoveredIps = new StringBuilder();

            for (java.net.InetAddress addr : addresses) {
                String ip = addr.getHostAddress();
                // Construct the direct pod URL
                String resolvedUrl = "http://" + ip + ":" + port;

                if (!nodes.contains(resolvedUrl)) {
                    nodes.add(resolvedUrl);
                    addedCount++;
                    discoveredIps.append(ip).append(", ");
                }
            }

            if (addedCount == 0) {
                return "No new nodes added. (Found " + addresses.length + " IPs, but all were already registered).";
            }

            return "Discovery Successful. Added " + addedCount + " nodes: [" + discoveredIps.toString() + "]";

        } catch (Exception e) {
            // Fallback: If DNS fails or it's a raw IP, just add it directly
            if (!nodes.contains(nodeUrl)) {
                nodes.add(nodeUrl);
                return "DNS Resolution failed, added raw URL: " + nodeUrl;
            }
            return "Node already exists: " + nodeUrl;
        }
    }

    @GetMapping("/nodes")
    @Operation(summary = "List Nodes", description = "List all registered nodes")
    public List<String> getNodes() {
        return nodes;
    }

    @DeleteMapping("/nodes")
    @Operation(summary = "Clear Nodes", description = "Remove all nodes")
    public String clearNodes() {
        nodes.clear();
        return "Nodes cleared";
    }

    @PostMapping("/control/{action}")
    @Operation(summary = "Control Load", description = "Broadcast Start/Stop commands to all nodes. Action: start or stop")
    public String controlLoad(
            @PathVariable String action,
            @RequestParam(defaultValue = "cpu") String type,
            @RequestParam(required = false, defaultValue = "1") int cores,
            @RequestParam(required = false, defaultValue = "1024") int memoryMB) {

        if (nodes.isEmpty())
            return "No nodes registered! Please discover or add nodes first.";

        StringBuilder response = new StringBuilder();
        // Parallel stream could be used for faster dispatch, but loop is safer for now
        // heavily concurrent dispatch might need ExecutorService in real prod apps
        for (String node : nodes) {
            String url = node + "/api/load/" + action + "?type=" + type + "&cores=" + cores + "&memoryMB=" + memoryMB;
            try {
                // Fire and forget or wait for response?
                // For UI responsiveness, we wait, but user might want async.
                // Keeping it sync for simplicity as per existing design.
                String result = restTemplate.getForObject(url, String.class);
                response.append("Node [").append(node).append("]: ").append(result).append("\n");
            } catch (Exception e) {
                response.append("Node [").append(node).append("]: Failed - ").append(e.getMessage()).append("\n");
            }
        }
        return response.toString();
    }

    @GetMapping("/cluster-stats")
    @Operation(summary = "Get Cluster Stats", description = "Aggregate stats from all nodes")
    public List<Map<String, Object>> getClusterStats() {
        List<Map<String, Object>> statsList = new java.util.ArrayList<>();
        for (String node : nodes) {
            String url = node + "/api/status";
            try {
                Map<String, Object> stats = restTemplate.getForObject(url, Map.class);
                if (stats != null) {
                    stats.put("nodeUrl", node);
                    // Add a friendly name or IP derived from URL for UI
                    try {
                        java.net.URI uri = java.net.URI.create(node);
                        stats.put("display", uri.getHost());
                    } catch (Exception ex) {
                        stats.put("display", node);
                    }
                    statsList.add(stats);
                }
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("nodeUrl", node);
                error.put("display", node);
                error.put("error", "Unreachable");
                error.put("podName", "Unknown");
                statsList.add(error);
            }
        }
        return statsList;
    }
}
