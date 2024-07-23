package com.battre.grpcifc;

import com.battre.stubs.services.LabSvcGrpc;
import com.battre.stubs.services.OpsSvcGrpc;
import com.battre.stubs.services.SpecSvcGrpc;
import com.battre.stubs.services.StorageSvcGrpc;
import com.battre.stubs.services.TriageSvcGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.AbstractStub;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class GrpcMethodInvoker {
  private static final Logger logger = Logger.getLogger(GrpcMethodInvoker.class.getName());

  private final DiscoveryClientAdapter discoveryClientAdapter;
  private final GrpcMethodInvokerBase grpcMethodInvokerBase;

  public GrpcMethodInvoker(DiscoveryClientAdapter discoveryClientAdapter) {
    this.discoveryClientAdapter = discoveryClientAdapter;
    this.grpcMethodInvokerBase = new GrpcMethodInvokerBase();
  }

  public <ReqT, RespT> void invokeMethod(
      String serviceName, ManagedChannel channel, String methodName, ReqT request, RespT response) {
    try {
      // Create a stub for the specified service
      AbstractStub<?> stub = createStub(channel, serviceName);

      // Get the method object corresponding to the specified method name
      Method method = grpcMethodInvokerBase.getMethod(stub.getClass(), methodName);

      // Call the method using reflection and pass the request object
      // @SuppressWarnings("unchecked")
      method.invoke(stub, request, response);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  public <ReqT, RespT> RespT invokeNonblock(String serviceName, String methodName, ReqT request) {
    return grpcMethodInvokerBase.invokeNonblock(
            methodName,
            request,
            (req, observer) -> {
              String serviceUrl = getServiceUrl(serviceName);
              ManagedChannel channel = createChannel(serviceUrl);
              try {
                invokeMethod(
                        serviceName, channel, methodName, req, observer);
              } finally {
                channel.shutdown();
              }
            });
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
