# Spring Boot Load Test Application

A lightweight Spring Boot application designed for testing Kubernetes cluster overload scenarios. It exposes endpoints to stress CPU, Memory, and Network throughput.

## Docker Hub Image
The Docker image is available at:
**[kagenopriest/loadtest-app:latest](https://hub.docker.com/r/kagenopriest/loadtest-app)**

## Features
- **High Throughput**: `GET /api/load` for testing network/ingress capacity.
- **CPU Stress**: `GET /api/cpu` for testing CPU limits and HPA.
- **Memory Stress**: `GET /api/memory` for testing memory limits and OOM kills.
- **Swagger UI**: `/swagger-ui.html` for easy API exploration.

## Quick Start (Docker)
```bash
docker run -p 8080:8080 kagenopriest/loadtest-app:latest
```

## Kubernetes Deployment
See [k8s-deployment.yaml](k8s-deployment.yaml) for a sample deployment with:
- 2 Replicas
- Pod Anti-Affinity (spreads pods across nodes)
- NodePort Service

## Load Testing Guide
See [TESTING_GUIDE.md](TESTING_GUIDE.md) for detailed instructions on using JMeter and Apache Benchmark with this application.

## Offline Usage
For offline clusters, you can export the image:
```bash
docker save -o loadtest-app.tar kagenopriest/loadtest-app:latest
```
