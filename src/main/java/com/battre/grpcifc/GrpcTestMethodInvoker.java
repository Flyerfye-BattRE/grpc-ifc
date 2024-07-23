package com.battre.grpcifc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class GrpcTestMethodInvoker {
    private static final Logger logger = Logger.getLogger(GrpcTestMethodInvoker.class.getName());
    private final GrpcMethodInvokerBase grpcMethodInvokerBase;

    public GrpcTestMethodInvoker() {
        this.grpcMethodInvokerBase = new GrpcMethodInvokerBase();
    }

    public <ReqT, RespT> RespT invokeNonblock(
            Object target, String methodName, ReqT request) throws NoSuchMethodException {

        Method method = grpcMethodInvokerBase.getMethod(target.getClass(), methodName);
        return grpcMethodInvokerBase.invokeNonblock(
                method.getName(),
                request,
                (req, observer) -> {
                    try {
                        method.invoke(target, req, observer);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
