package play.api.inject.cdi

import java.lang.annotation.Annotation

import javax.enterprise.inject.spi.{Bean, BeanManager}
import javax.inject.{Inject, Singleton}
import org.jboss.weld.exceptions.DeploymentException
import play.api.inject.{BindingKey, Injector, QualifierClass, QualifierInstance}

import scala.reflect.ClassTag

@Singleton
class CdiInjector @Inject() (beanManager: BeanManager) extends Injector {
  override def instanceOf[T: ClassTag]: T =
    lookupInstanceOf(implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]])

  override def instanceOf[T](clazz: Class[T]): T =
    lookupInstanceOf(clazz)

  override def instanceOf[T](key: BindingKey[T]): T = {
    val qualifiers: Array[Annotation] = key.qualifier match {
      case None => Array.empty
      case Some(QualifierInstance(qualifier)) => Array(qualifier)
      case Some(QualifierClass(qualifier)) => Array(AnnotationTypeFactory.create(qualifier))
    }
    lookupInstanceOf(key.clazz, qualifiers)
  }

  private def lookupInstanceOf[T](clazz: Class[T], qualifiers: Array[Annotation] = Array.empty) = {
    val beans = beanManager.getBeans(clazz, qualifiers: _*).iterator()
    if (beans.hasNext) {
      val bean = beans.next().asInstanceOf[Bean[T]]
      beanManager.getContext(bean.getScope)
        .get(bean, beanManager.createCreationalContext(bean))
    } else {
      if (qualifiers.nonEmpty) {
        throw new DeploymentException("Unable to get instance of bean of type " + clazz + " with qualifiers " + qualifiers.toList)
      } else {
        // Try and instantiate/inject it directly
        val annotatedType = beanManager.createAnnotatedType(clazz)
        val injectionTarget = beanManager.createInjectionTarget(annotatedType)
        val context = beanManager.createCreationalContext[T](null)
        val instance = injectionTarget.produce(context)
        injectionTarget.inject(instance, context)
        injectionTarget.postConstruct(instance)
        instance
      }
    }
  }
}
