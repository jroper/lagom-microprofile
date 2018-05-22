package com.example;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.cdi.LagomService;
import com.lightbend.lagom.javadsl.client.cdi.LagomServiceClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@LagomService
@ApplicationScoped
public class MyServiceImpl implements MyService {

  private final MyService client;
  private final PersistentEntityRegistry registry;

  @Inject
  public MyServiceImpl(@LagomServiceClient MyService client, PersistentEntityRegistry registry) {
    this.client = client;
    this.registry = registry;
  }

  @Override
  public ServiceCall<NotUsed, String> sayHello(String name) {
    return notUsed ->
        registry.refFor(MyPersistentEntity.class, name)
            .ask(new MyCommand(name))
            .thenApply(times -> "Hello " + name + ", you've been said hello to " + times + " times\n");
  }

  @Override
  public ServiceCall<NotUsed, String> proxyHello(String name) {
    return notUsed -> client.sayHello("proxied " + name).invoke();
  }
}
