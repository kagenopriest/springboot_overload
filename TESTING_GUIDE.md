# Load Testing Guide & Documentation

## Overview
This project provides a Dockerized Spring Boot application designed for testing Kubernetes cluster overload scenarios. It exposes specific endpoints to stress CPU, Memory, and Network throughput.

## Docker Image
- **Image**: `kagenopriest/loadtest-app:latest`
- **Port**: `8080`

## Quick Start
```powershell
docker run -p 8080:8080 kagenopriest/loadtest-app:latest
```

## Browser Verification
A recording of the local verification is verified below:
![Browser Verification Result](recording.webp)

## Endpoints

| Endpoint | Description | Use Case |
|---|---|---|
| `GET /api/load` | Returns a simple timestamp string. | High RPS / Throughput testing. |
| `GET /api/cpu` | Calculates square roots in a loop. | CPU-intensive load testing. |
| `GET /api/memory` | Allocates 1MB of memory per request. | Memory usage verification. |

## Overload Testing Strategy

### 1. High Throughput Test (Network/Connection Overhead)
**Target Endpoint**: `GET /api/load`
**Goal**: Test how many requests per second (RPS) the container and Kubernetes networking can handle.
**Tool Reference (Apache Benchmark)**:
```bash
ab -n 100000 -c 1000 http://localhost:8080/api/load
```

### 2. CPU Stress Test (Compute Overload)
**Target Endpoint**: `GET /api/cpu`
**Goal**: Max out the CPU limits to trigger HPA (Horizontal Pod Autoscaler).
**JMeter**: Use ~50-100 threads hitting this endpoint to pin CPU usage.

### 3. Memory Stress Test (OOM Kill Check)
**Target Endpoint**: `GET /api/memory`
**Goal**: Allocate memory rapidly.
**Warning**: Each request allocates 1MB. High concurrency here will quickly cause an OOM Kill.

## Offline Export
To move this image to your offline cluster:
1. **Save**: `docker save -o loadtest-app.tar kagenopriest/loadtest-app:latest`
2. **Transfer**: Move `loadtest-app.tar` to your cluster.
3. **Load**: `docker load -i loadtest-app.tar`
