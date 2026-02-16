package com.honeycomb.grpc.server;

import com.honeycomb.grpc.proto.HealthCheckRequest;
import com.honeycomb.grpc.proto.HealthCheckResponse;
import com.honeycomb.grpc.proto.HoneycombHealthServiceGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;

/**
 * gRPC health check service for Honeycomb nodes.
 *
 * <p>Delegates to the Spring Boot {@link HealthEndpoint} so that the gRPC health
 * status is consistent with the HTTP actuator health endpoint. Clients can use
 * this for gRPC-native health checking and load-balancer integration.</p>
 *
 * @since 1.4.0
 */
@GrpcService
public class HealthGrpcService extends HoneycombHealthServiceGrpc.HoneycombHealthServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(HealthGrpcService.class);

    private final HealthEndpoint healthEndpoint;

    public HealthGrpcService(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        try {
            HealthComponent health = healthEndpoint.health();
            Status status = health.getStatus();

            HealthCheckResponse.ServingStatus servingStatus;
            if (Status.UP.equals(status)) {
                servingStatus = HealthCheckResponse.ServingStatus.SERVING;
            } else if (Status.DOWN.equals(status) || Status.OUT_OF_SERVICE.equals(status)) {
                servingStatus = HealthCheckResponse.ServingStatus.NOT_SERVING;
            } else {
                servingStatus = HealthCheckResponse.ServingStatus.UNKNOWN;
            }

            responseObserver.onNext(HealthCheckResponse.newBuilder()
                    .setStatus(servingStatus)
                    .setDetails(status.getCode())
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC health check failed: {}", e.getMessage());
            responseObserver.onNext(HealthCheckResponse.newBuilder()
                    .setStatus(HealthCheckResponse.ServingStatus.NOT_SERVING)
                    .setDetails("Health check error: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }
}
