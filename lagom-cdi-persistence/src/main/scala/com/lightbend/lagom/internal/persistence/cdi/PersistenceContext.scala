package com.lightbend.lagom.internal.persistence.cdi

import java.lang.annotation.Annotation

import com.lightbend.lagom.internal.javadsl.persistence.Context
import com.lightbend.lagom.javadsl.persistence.cdi.PersistenceScoped
import javax.enterprise.context.ContextNotActiveException
import javax.enterprise.context.spi.{AlterableContext, Contextual, CreationalContext}
import javax.enterprise.inject.spi.{Bean, BeanManager}

class PersistenceContext extends AlterableContext {

  private val context = new ThreadLocal[ContextInstance[_]]()

  def newContext[C](beanManager: BeanManager, bean: Bean[C]): ContextInstance[C] = {
    new ContextInstance(beanManager, bean)
  }

  override def getScope: Class[_ <: Annotation] = classOf[PersistenceScoped]

  override def get[T](contextual: Contextual[T], creationalContext: CreationalContext[T]): T = {
    activeContext.get(contextual, creationalContext)
  }

  override def get[T](contextual: Contextual[T]): T = {
    activeContext.get(contextual)
  }

  override def isActive: Boolean = context.get() != null

  override def destroy(contextual: Contextual[_]): Unit = {
    activeContext.destroy(contextual)
  }

  private def activeContext: ContextInstance[_] = {
    context.get() match {
      case null => throw new ContextNotActiveException("PersistentEntityContext is not active")
      case ctx => ctx
    }
  }

  class ContextInstance[C](beanManager: BeanManager, bean: Bean[C]) extends AlterableContext with Context[C] {
    private var beans = Map.empty[Contextual[_], (Any, CreationalContext[_])]

    override def get[T](contextual: Contextual[T], creationalContext: CreationalContext[T]) = {
      beans.get(contextual) match {
        case Some(instance: T) => instance
        case None =>
          val instance = contextual.create(creationalContext)
          beans += (contextual -> (instance, creationalContext))
          instance
      }
    }

    override def get[T](contextual: Contextual[T]): T = {
      beans.get(contextual).orNull.asInstanceOf[T]
    }

    override def destroy(contextual: Contextual[_]): Unit = {
      beans.get(contextual) match {
        case Some((instance: AnyRef, creationalContext: CreationalContext[AnyRef])) =>
          beans -= contextual
          contextual.asInstanceOf[Contextual[AnyRef]].destroy(instance, creationalContext)
        case None => ()
      }
    }

    override def getScope: Class[_ <: Annotation] = classOf[PersistenceScoped]

    override def isActive: Boolean = true

    override def createContextualObject(): C = {
      apply {
        get(bean, beanManager.createCreationalContext(bean))
      }
    }

    def apply[T](callback: => T): T = {
      val old = context.get()
      try {
        context.set(this)
        callback
      } finally {
        if (old != null) {
          context.set(old)
        } else {
          context.remove()
        }
      }
    }

    def destroy(): Unit = {
      beans.foreach {
        case (contextual: Contextual[AnyRef], (instance: AnyRef, creationalContext: CreationalContext[AnyRef])) =>
          contextual.destroy(instance, creationalContext)
      }
      beans = Map.empty
    }
  }
}
