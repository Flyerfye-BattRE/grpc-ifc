package com.battre.grpcifc;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class GrpcMethodInvokerBase {
  private static final Logger logger = Logger.getLogger(GrpcMethodInvokerBase.class.getName());

  public <ReqT, RespT> RespT invokeNonblock(
      String methodName, ReqT request, BiConsumer<ReqT, StreamObserver<RespT>> invoker) {

    CompletableFuture<RespT> responseFuture = new CompletableFuture<>();
    StreamObserver<RespT> responseObserver =
        new StreamObserver<>() {
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

    // Invoke the method
    invoker.accept(request, responseObserver);

    try {
      RespT response = responseFuture.get(10, TimeUnit.SECONDS);
      logger.info(methodName + "() response: " + response.toString());
      return response;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // Reset the interrupted status
      logger.warning(methodName + "() Thread interrupted: " + e.getMessage());
    } catch (TimeoutException e) {
      logger.warning(methodName + "() Timeout waiting for response: " + e.getMessage());
    } catch (ExecutionException e) {
      // Get the actual cause of the exception
      Throwable cause = e.getCause();

      if (cause instanceof StatusRuntimeException) {
        StatusRuntimeException statusException = (StatusRuntimeException) cause;
        logger.severe(
            methodName + "() gRPC StatusRuntimeException: " + statusException.getStatus());
        statusException.printStackTrace();
      } else {
        logger.severe(methodName + "() ExecutionException: " + cause.getMessage());
        cause.printStackTrace();
      }
    } catch (Exception e) {
      // Handle any other unexpected exceptions
      logger.severe(methodName + "() Unexpected exception: " + e.getMessage());
      e.printStackTrace();
    }

    return null; // Return null or handle appropriately based on your application logic
  }

    // Method to get the method object for the specified method name
    public Method getMethod(Class<?> stubClass, String methodName) throws NoSuchMethodException {
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
}
