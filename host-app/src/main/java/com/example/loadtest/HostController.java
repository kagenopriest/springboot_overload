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
    @Operation(summary = "Add Node", description = "Register a new node (e.g., http://192.168.1.5:8080). Resolves DNS to find all Pod IPs if Headless Service.")
    public String addNode(@RequestBody String nodeUrlInput) {
        String nodeUrl = nodeUrlInput.trim();
        if (!nodeUrl.startsWith("http")) {
            nodeUrl = "http://" + nodeUrl;
        }

        try {
            // Extract hostname from URL
            java.net.URI uri = java.net.URI.create(nodeUrl);
            String hostname = uri.getHost();
            int port = uri.getPort() == -1 ? 80 : uri.getPort();

            // Resolve DNS
            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(hostname);
            int addedCount = 0;

            for (java.net.InetAddress addr : addresses) {
                String ip = addr.getHostAddress();
                String resolvedUrl = "http://" + ip + ":" + port;

                // Avoid duplicates and adding itself (if accidentally discovering host)
                if (!nodes.contains(resolvedUrl)) {
                    nodes.add(resolvedUrl);
                    addedCount++;
                }
            }
            return "Found " + addresses.length + " IPs. Added " + addedCount + " new nodes.";

        } catch (Exception e) {
            // Fallback for raw IP or failure
            if (!nodes.contains(nodeUrl)) {
                nodes.add(nodeUrl);
                return "Node added (DNS resolution failed): " + nodeUrl;
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
    public String controlLoad(@PathVariable String action, @RequestParam(defaultValue = "cpu") String type) {
        if (nodes.isEmpty())
            return "No nodes registered!";

        StringBuilder response = new StringBuilder();
        // Broadcast to all nodes in parallel (simple loop for now is fine for low node
        // count)
        for (String node : nodes) {
            String url = node + "/api/load/" + action + "?type=" + type;
            try {
                String result = restTemplate.getForObject(url, String.class);
                response.append(node).append(": ").append(result).append("\n");
            } catch (Exception e) {
                response.append(node).append(": Failed - ").append(e.getMessage()).append("\n");
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
                    statsList.add(stats);
                }
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("nodeUrl", node);
                error.put("error", "Unreachable");
                error.put("podName", "Unknown");
                statsList.add(error);
            }
        }
        return statsList;
    }
}
