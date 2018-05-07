package play.api.inject.cdi

import java.lang.annotation.Annotation

import javax.enterprise.inject.spi.{Bean, BeanManager}
import javax.inject.{Inject, Singleton}
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
    val beans = beanManager.getBeans(clazz, qualifiers: _*)
    val bean = beans.iterator.next().asInstanceOf[Bean[T]]
    beanManager.getContext(bean.getScope)
      .get(bean, beanManager.createCreationalContext(bean))
  }
}
