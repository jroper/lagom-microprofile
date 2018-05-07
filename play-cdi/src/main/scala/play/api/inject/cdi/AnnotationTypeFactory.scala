package play.api.inject.cdi

import java.lang.annotation.Annotation
import java.lang.reflect.{InvocationHandler, Method}

private[cdi] object AnnotationTypeFactory {
  def create[T <: Annotation](clazz: Class[T]): T = {
    java.lang.reflect.Proxy.newProxyInstance(
      clazz.getClassLoader, Array(clazz), new DynamicAnnotation(clazz)
    ).asInstanceOf[T]
  }

  private class DynamicAnnotation(annotation: Class[_ <: Annotation]) extends InvocationHandler {
    override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
      method.invoke(this, args)
    }

    override def hashCode(): Int = 0
    override def equals(other: Any): Boolean = {
      other match {
        case null => false
        case t: AnyRef if t eq this => true
        case sameAnnotation if sameAnnotation.getClass == annotation => true
        case _ => false
      }
    }
    override def toString: String = {
      s"@${annotation.getName}()"
    }
  }
}
