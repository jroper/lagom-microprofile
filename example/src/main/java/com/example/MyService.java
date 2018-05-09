package com.example;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface MyService extends Service {

  ServiceCall<NotUsed, String> sayHello(String name);
  ServiceCall<NotUsed, String> proxyHello(String name);

  @Override
  default Descriptor descriptor() {
    return named("my-service")
        .withCalls(
          restCall(Method.GET, "/hello/:name", this::sayHello),
          restCall(Method.GET, "/proxy/:name", this::proxyHello)
        ).withAutoAcl(true);
  }
}
