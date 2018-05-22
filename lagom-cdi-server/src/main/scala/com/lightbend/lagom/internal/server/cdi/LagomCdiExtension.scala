package com.lightbend.lagom.internal.server.cdi

import java.lang.annotation.Annotation

import com.lightbend.lagom.internal.javadsl.server.{JavadslServerBuilder, JavadslServicesRouter, ResolvedServices}
import com.lightbend.lagom.javadsl.api.{Service, ServiceInfo}
import com.lightbend.lagom.javadsl.server.LagomServiceRouter
import com.lightbend.lagom.javadsl.server.cdi.LagomService
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

  def bindServices(@Observes abd: AfterBeanDiscovery, beanManager: BeanManager): Unit = {
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
