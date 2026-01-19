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
    @Operation(summary = "Add Node", description = "Register a new node manually (e.g., http://192.168.1.5:8080).")
    public String addNode(@RequestBody String nodeUrlInput) {
        String nodeUrl = nodeUrlInput.trim();
        if (!nodeUrl.startsWith("http")) {
            nodeUrl = "http://" + nodeUrl;
        }

        if (!nodes.contains(nodeUrl)) {
            nodes.add(nodeUrl);
            return "Node added: " + nodeUrl;
        }
        return "Node already exists: " + nodeUrl;
    }

    @PostMapping("/nodes/discover")
    @Operation(summary = "Discover Nodes", description = "Discover nodes via DNS (Headless Service)")
    public String discoverNodes(@RequestBody String serviceUrlInput) {
        String serviceUrl = serviceUrlInput.trim();
        if (!serviceUrl.startsWith("http"))
            serviceUrl = "http://" + serviceUrl;

        try {
            java.net.URI uri = java.net.URI.create(serviceUrl);
            String hostname = uri.getHost();
            int port = uri.getPort();
            if (port == -1)
                port = 80;

            java.net.InetAddress[] addresses = java.net.InetAddress.getAllByName(hostname);
            int added = 0;
            StringBuilder sb = new StringBuilder();

            for (java.net.InetAddress addr : addresses) {
                String ip = addr.getHostAddress();
                String resolvedUrl = "http://" + ip + ":" + port;
                if (!nodes.contains(resolvedUrl)) {
                    nodes.add(resolvedUrl);
                    added++;
                    sb.append(ip).append(", ");
                }
            }
            if (added == 0)
                return "No new nodes found from " + hostname + " (Found " + addresses.length + " IPs)";
            return "Discovered " + added + " nodes: " + sb.toString();
        } catch (Exception e) {
            return "Discovery failed: " + e.getMessage();
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
