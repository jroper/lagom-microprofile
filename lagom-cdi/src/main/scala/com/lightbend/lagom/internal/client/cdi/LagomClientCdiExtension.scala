package com.lightbend.lagom.internal.client.cdi

import java.lang.annotation.Annotation

import com.lightbend.lagom.internal.javadsl.client.ServiceClientLoader
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.client.cdi.LagomServiceClient
import javax.enterprise.context.spi.CreationalContext
import javax.enterprise.event.Observes
import javax.enterprise.inject.spi._
import javax.inject.Singleton
import org.slf4j.LoggerFactory
import play.api.inject.cdi.CdiInjector

class LagomClientCdiExtension extends Extension {

  private val log = LoggerFactory.getLogger(this.getClass)

  private var serviceClients: Set[(LagomServiceClient, Class[_ <: Service])] = Set.empty

  def locateServiceClients[T, X <: Service](@Observes processInjectionPoint: ProcessInjectionPoint[T, X]): Unit = {
    val serviceClientAnnotation = processInjectionPoint.getInjectionPoint.getAnnotated.getAnnotation(classOf[LagomServiceClient])
    if (serviceClientAnnotation != null) {
      processInjectionPoint.getInjectionPoint.getType match {
        case serviceClass: Class[_] if serviceClass == classOf[Service] =>
          processInjectionPoint.addDefinitionError(new RuntimeException(s"Can't provide a Lagom service client for ${classOf[Service]}"))
        case serviceClass: Class[_] if classOf[Service].isAssignableFrom(serviceClass) =>
          serviceClients += serviceClientAnnotation -> serviceClass.asInstanceOf[Class[_ <: Service]]
        case other =>
          processInjectionPoint.addDefinitionError(new RuntimeException(s"${classOf[LagomServiceClient]} annotated injection point ${processInjectionPoint.getInjectionPoint} is not a Lagom service client."))
      }
    }
  }

  def bindClients(@Observes abd: AfterBeanDiscovery, beanManager: BeanManager): Unit = {
    // Play CDI injector is much more convenient to use
    val injector = new CdiInjector(beanManager)

    def bindProviderClass[T](interface: Class[T], qualifier: Option[Annotation] = None)(provider: => T): Unit = {
      val configurator = abd.addBean[T]()
      configurator.types(interface)
      configurator.createWith((_: CreationalContext[T]) => provider)
      configurator.scope(classOf[Singleton])
      qualifier.foreach(configurator.addQualifier)
    }

    serviceClients.foreach {
      case (qualifier, client) =>
        bindProviderClass(client.asInstanceOf[Class[Service]], Some(qualifier)) {
          injector.instanceOf[ServiceClientLoader].loadServiceClient(client)
        }
    }
  }
}
