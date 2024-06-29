package com.battre.grpcifc;

import com.battre.stubs.services.LabSvcGrpc;
import com.battre.stubs.services.OpsSvcGrpc;
import com.battre.stubs.services.SpecSvcGrpc;
import com.battre.stubs.services.StorageSvcGrpc;
import com.battre.stubs.services.TriageSvcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.StreamObserver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class GrpcMethodInvoker {
    private static final Logger logger = Logger.getLogger(GrpcMethodInvoker.class.getName());

    private final DiscoveryClientAdapter discoveryClientAdapter;
    public GrpcMethodInvoker(DiscoveryClientAdapter discoveryClientAdapter) {
        this.discoveryClientAdapter = discoveryClientAdapter;
    }

    public <ReqT, RespT> void invokeMethod(
            String serviceName,
            ManagedChannel channel,
            String methodName,
            ReqT request,
            RespT response
    ) {
        try {
            // Create a stub for the specified service
            AbstractStub<?> stub = createStub(channel, serviceName);

            // Get the method object corresponding to the specified method name
            Method method = getMethod(stub.getClass(), methodName);

            // Call the method using reflection and pass the request object
            // @SuppressWarnings("unchecked")
            method.invoke(stub, request, response);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public <ReqT, RespT> RespT invokeNonblock(String serviceName, String methodName, ReqT request) {
        CompletableFuture<RespT> responseFuture = new CompletableFuture<>();
        StreamObserver<RespT> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(RespT response) {
                responseFuture.complete(response);
            }

            @Override
            public void onError(Throwable t) {
                logger.severe(methodName + "() errored: " + t.getMessage());
                responseFuture.completeExceptionally(t);
            }

            @Override
            public void onCompleted() {
                logger.info(methodName + "() completed");
            }
        };

        String serviceUrl = getServiceUrl(serviceName);
        ManagedChannel channel = createChannel(serviceUrl);

        invokeMethod(
                serviceName,
                channel,
                methodName,
                request,
                responseObserver
        );

        try {
            RespT response = responseFuture.get(10, TimeUnit.SECONDS);
            logger.info(methodName + "() response: " + response.toString());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // Reset the interrupted status
            logger.warning(methodName + "() Thread interrupted: " + e.getMessage());
        } catch (TimeoutException e) {
            logger.warning(methodName + "() Timeout waiting for response: " + e.getMessage());
        } catch (ExecutionException e) {
            // Get the actual cause of the exception
            Throwable cause = e.getCause();

            if (cause instanceof StatusRuntimeException) {
                StatusRuntimeException statusException = (StatusRuntimeException) cause;
                logger.severe(methodName + "() gRPC StatusRuntimeException: " + statusException.getStatus());
                statusException.printStackTrace();
            } else {
                logger.severe(methodName + "() ExecutionException: " + cause.getMessage());
                cause.printStackTrace();
            }
        } catch (Exception e) {
            // Handle any other unexpected exceptions
            logger.severe(methodName + "() Unexpected exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            channel.shutdown(); // Close the channel
        }

        return null;  // Return null or handle appropriately based on your application logic
    }

    private ManagedChannel createChannel(String serviceUrl) {
        // Extract host and port from service URL
        String[] parts = serviceUrl.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]); // gRPC port will be server port + 5

        // Create gRPC channel
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext() // For simplicity; use TLS for production
                .build();
    }

    private String getServiceUrl(String serviceName) {
        String serviceUrl = discoveryClientAdapter.getServiceUrl(serviceName);

        logger.info("For service name [" + serviceName + "] URL formed: " + serviceUrl);
        return serviceUrl;
    }

    // Method to get the method object for the specified method name
    private Method getMethod(Class<?> stubClass, String methodName) throws NoSuchMethodException {
        // Find the method with the specified name in the stub class
        for (Method method : stubClass.getMethods()) {
            // Check if method name matches
            if (!method.getName().equals(methodName)) {
                continue;
            }

            // Found matching method
            return method;
        }

        // Method not found
        throw new NoSuchMethodException("Method not found: " + methodName);
    }

    // Logic to create the stub based on the service name
    private AbstractStub<?> createStub(ManagedChannel channel, String serviceName) {
        switch (serviceName) {
            case "labsvc":
                return LabSvcGrpc.newStub(channel);
            case "opssvc":
                return OpsSvcGrpc.newStub(channel);
            case "specsvc":
                return SpecSvcGrpc.newStub(channel);
            case "storagesvc":
                return StorageSvcGrpc.newStub(channel);
            case "triagesvc":
                return TriageSvcGrpc.newStub(channel);
            default:
                throw new IllegalArgumentException("Unknown service name: " + serviceName);
        }
    }
}
