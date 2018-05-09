package com.lightbend.lagom.internal.server.cdi

import java.lang.annotation.Annotation

import com.lightbend.lagom.internal.javadsl.client.ServiceClientLoader
import com.lightbend.lagom.internal.javadsl.server.{JavadslServerBuilder, JavadslServicesRouter, ResolvedServices}
import com.lightbend.lagom.javadsl.api.{Service, ServiceInfo}
import com.lightbend.lagom.javadsl.server.LagomServiceRouter
import com.lightbend.lagom.javadsl.server.cdi.{LagomService, LagomServiceClient}
import javax.enterprise.context.spi.CreationalContext
import javax.enterprise.event.Observes
import javax.enterprise.inject.spi._
import javax.inject.Singleton
import play.api.inject.cdi.CdiInjector

import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

class LagomCdiExtension extends Extension {

  private val log = LoggerFactory.getLogger(this.getClass)

  private var services: Seq[(Class[_], Class[_])] = Nil
  private var serviceClients: Set[(LagomServiceClient, Class[_ <: Service])] = Set.empty

  def locateServices[T <: Service](@Observes @WithAnnotations(Array(classOf[LagomService])) pat: ProcessAnnotatedType[T]): Unit = {
    val serviceImpl = pat.getAnnotatedType.getJavaClass
    val lagomServiceAnnotation = pat.getAnnotatedType.getAnnotation(classOf[LagomService])
    val serviceDescriptor = if (lagomServiceAnnotation.descriptor() == classOf[Service]) {
      locateServiceDescriptor(serviceImpl.asInstanceOf[Class[_ <: Service]])
    } else if (lagomServiceAnnotation.descriptor().isAssignableFrom(serviceImpl)) {
      lagomServiceAnnotation.descriptor()
    } else {
      sys.error(s"${classOf[LagomService]} annotated bean $serviceImpl does not implement the descriptor interface ${lagomServiceAnnotation.descriptor()}")
    }

    services :+= (serviceDescriptor, serviceImpl)
  }

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

  def bindServicesAndClients(@Observes abd: AfterBeanDiscovery, beanManager: BeanManager): Unit = {
    // Play CDI injector is much more convenient to use
    val injector = new CdiInjector(beanManager)

    def bindProvider[T: ClassTag](provider: => T): Unit = {
      bindProviderClass(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])(provider)
    }

    def bindProviderClass[T](interface: Class[T], qualifier: Option[Annotation] = None)(provider: => T): Unit = {
      val configurator = abd.addBean[T]()
      configurator.types(interface)
      configurator.createWith((_: CreationalContext[T]) => provider)
      configurator.scope(classOf[Singleton])
      qualifier.foreach(configurator.addQualifier)
    }

    def bind[I: ClassTag, T <: I : ClassTag](): Unit = {
      val configurator = abd.addBean[T]()
      configurator.read(beanManager.createAnnotatedType(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]))
      configurator.types(implicitly[ClassTag[I]].runtimeClass)
      configurator.scope(classOf[Singleton])
    }

    if (services.nonEmpty) {
      bindProvider[ServiceInfo] {
        val serverBuilder = injector.instanceOf[JavadslServerBuilder]
        val serviceInterfaces = services.map(_._1)
        serverBuilder.createServiceInfo(serviceInterfaces.head, serviceInterfaces.tail)
      }
      bindProvider[ResolvedServices] {
        val serverBuilder = injector.instanceOf[JavadslServerBuilder]
        serverBuilder.resolveServices(services.map { case (descriptor, impl) => descriptor -> injector.instanceOf(impl) })
      }
      bind[LagomServiceRouter, JavadslServicesRouter]()
    }

    serviceClients.foreach {
      case (qualifier, client) =>
        bindProviderClass(client.asInstanceOf[Class[Service]], Some(qualifier)) {
          injector.instanceOf[ServiceClientLoader].loadServiceClient(client)
        }
    }
  }

  private def locateServiceDescriptor(serviceImpl: Class[_]): Class[_] = {
    val serviceInterfaces = serviceImpl.getInterfaces.toSeq.collect {
      case service if service != classOf[Service] && classOf[Service].isAssignableFrom(service) => service
    }

    serviceInterfaces match {
      case Nil if serviceImpl.getSuperclass == null =>
        sys.error(s"${classOf[LagomService]} annotated bean $serviceImpl does not implement a service descriptor")
      case Nil => locateServiceDescriptor(serviceImpl.getSuperclass)
      case Seq(service) => service
      case multiple =>
        sys.error(s"${classOf[LagomService]} annotated bean $serviceImpl implements multiple service descriptors, use the descriptor annotation value to disambiguate: " + multiple)
    }
  }
}
