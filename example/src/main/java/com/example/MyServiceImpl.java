package com.example;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.server.cdi.LagomService;
import com.lightbend.lagom.javadsl.server.cdi.LagomServiceClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

@LagomService
@ApplicationScoped
public class MyServiceImpl implements MyService {

  private final MyService client;

  @Inject
  public MyServiceImpl(@LagomServiceClient MyService client) {
    this.client = client;
  }

  @Override
  public ServiceCall<NotUsed, String> sayHello(String name) {
    return notUsed -> CompletableFuture.completedFuture("Hello " + name);
  }

  @Override
  public ServiceCall<NotUsed, String> proxyHello(String name) {
    return notUsed -> client.sayHello("proxied " + name).invoke();
  }
}
