package com.lightbend.lagom.internal.persistence.cdi

import com.lightbend.lagom.internal.javadsl.persistence.{AbstractPersistentEntityRegistry, ReadSideImpl}
import com.lightbend.lagom.javadsl.persistence.cdi.PersistenceScoped
import com.lightbend.lagom.javadsl.persistence.{AggregateEvent, PersistentEntity, PersistentEntityRegistry, ReadSideProcessor}
import javax.enterprise.event.Observes
import javax.enterprise.inject.spi._

class LagomPersistenceCdiExtension extends Extension {

  private var persistentEntities = Set.empty[Bean[_ <: PersistentEntity[_, _, _]]]
  private var readSideProcessors = Set.empty[Bean[_ <: ReadSideProcessor[_]]]
  private val context = new PersistenceContext()

  def processPersistentEntities[T](@Observes pb: ProcessBean[_ <: PersistentEntity[_, _, _]]) = {
    if (pb.getBean.getScope == classOf[PersistenceScoped]) {
      persistentEntities += pb.getBean
    }
  }

  def processReadSideProcessors[T](@Observes pb: ProcessBean[_ <: ReadSideProcessor[_]]) = {
    if (pb.getBean.getScope == classOf[PersistenceScoped]) {
      readSideProcessors += pb.getBean
    }
  }

  def registerContext(@Observes abd: AfterBeanDiscovery, beanManager: BeanManager) = {
    abd.addContext(context)
  }

  def registerEntitiesAndReadSides(@Observes adv: AfterDeploymentValidation, beanManager: BeanManager): Unit = {
    if (persistentEntities.nonEmpty) {
      val bean = beanManager.getBeans(classOf[PersistentEntityRegistry]).iterator().next()
        .asInstanceOf[Bean[PersistentEntityRegistry]]
      val entityRegistry = beanManager.getContext(bean.getScope).get(bean, beanManager.createCreationalContext(bean))
      entityRegistry match {
        case abs: AbstractPersistentEntityRegistry =>
          persistentEntities.foreach { bean =>
            abs.register(() => {
              context.newContext(beanManager,
                bean.asInstanceOf[Bean[PersistentEntity[AnyRef, AnyRef, AnyRef]]])
            })
          }
        case other => throw new DeploymentException("Can't register a CDI persistent entity with a non built in entity registry")
      }
    }

    if (readSideProcessors.nonEmpty) {
      val bean = beanManager.getBeans(classOf[ReadSideImpl]).iterator().next()
        .asInstanceOf[Bean[ReadSideImpl]]
      val readSide = beanManager.getContext(bean.getScope).get(bean, beanManager.createCreationalContext(bean))

      readSideProcessors.foreach { bean =>
        readSide.registerFactory(() => {
          context.newContext(beanManager, bean.asInstanceOf[Bean[ReadSideProcessor[DummyEvent]]])
        }, bean.getBeanClass)
      }
    }
  }

  private trait DummyEvent extends AggregateEvent[DummyEvent]
}



