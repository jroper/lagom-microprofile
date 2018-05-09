package play.api.inject.cdi

import javax.enterprise.context.spi.CreationalContext
import javax.enterprise.event.Observes
import javax.enterprise.inject.spi._
import javax.inject.{Inject, Provider, Singleton}
import play.api.inject._
import play.api.libs.Files.TemporaryFileReaperConfiguration
import play.api.routing.Router
import play.api.{ApplicationLoader, Configuration, Environment, OptionalSourceMapper}
import play.core.WebCommands
import play.core.j.{DefaultJavaContextComponents, JavaContextComponents}
import play.core.routing.GeneratedRouter
import play.inject.DelegateInjector

class CdiPlayExtension(context: ApplicationLoader.Context) extends Extension {

  private val filteredBindings: Set[Class[_]] = Set(
    classOf[DefaultApplicationLifecycle],
    classOf[ApplicationLifecycle],
    classOf[TemporaryFileReaperConfiguration]
  )
  private var eagerSingletonBindings: Seq[BindingKey[_]] = Nil

  def loadModules(@Observes abd: AfterBeanDiscovery, beanManager: BeanManager): Unit = {

    val injector = new CdiInjector(beanManager)

    val configuration = context.initialConfiguration

    // Read the modules
    val modules = {
      Modules.locate(context.environment, configuration)
        .collect {
          case playModule: Module => playModule
          case other => sys.error("Don't know how to load module " + other)
        }
    } :+ new CdiModule(context, injector)

    eagerSingletonBindings = modules.flatMap { module =>

      val bindings = if (module.isInstanceOf[CdiModule]) {
        // Don't filter bindings of the CDI module, since it is what provides the replacements
        module.bindings(context.environment, configuration)
      } else {
        val filtered = module.bindings(context.environment, configuration)
          .filterNot(binding => filteredBindings.contains(binding.key.clazz))
        // Exclude double JavaContextComponents binding
        if (module.isInstanceOf[BuiltinModule] && filtered.count(_.key == bind[JavaContextComponents]) == 2) {
          filtered.filterNot(_.key == bind[JavaContextComponents]) :+ bind[JavaContextComponents].to[DefaultJavaContextComponents]
        } else {
          filtered
        }
      }


      bindings.foreach { binding =>

        val beanConfigurator = abd.addBean[AnyRef]()

        // We're using the BeanConfigurator.read method, which overwrites other configuration, to read from the binding
        // target. We do this first, so that we can then overwrite that with whatever is found on the binding.
        binding.target match {

          // Self binding
          case None =>
            val annotatedType = beanManager.createAnnotatedType(binding.key.clazz.asInstanceOf[Class[AnyRef]])
            beanConfigurator.read(annotatedType)

          // Work around bug in Play where ExecutionContext is bound to an interface, not an implementation
          case Some(ConstructionTarget(implClass: Class[AnyRef])) if implClass.isInterface =>
            beanConfigurator.createWith { (_: CreationalContext[AnyRef]) =>
              injector.instanceOf(implClass)
            }

          // Binding from interface directly to an impl class
          case Some(ConstructionTarget(implClass)) =>
            val annotatedType = beanManager.createAnnotatedType(implClass.asInstanceOf[Class[AnyRef]])
            beanConfigurator.read(annotatedType)

          // Binding to a given provider
          case Some(ProviderTarget(provider: Provider[AnyRef])) =>
            // If it's not an anonymous class, we may need to inject it
            // These checks actually return false for Scala anonymous classes for some reason
            if (!provider.getClass.isAnonymousClass && !provider.getClass.isSynthetic) {

              // We need to get an injection target for the bean so we can inject it
              val providerType = beanManager.createAnnotatedType(provider.getClass.asInstanceOf[Class[Provider[AnyRef]]])
              val injectionTarget = beanManager.getInjectionTargetFactory(providerType)
                .createInjectionTarget(null)
              lazy val injectedProvider = {
                injectionTarget.inject(provider, beanManager.createCreationalContext(null))
                provider
              }

              beanConfigurator.createWith { (_: CreationalContext[AnyRef]) =>
                injectedProvider.get().asInstanceOf[AnyRef]
              }
            } else {
              beanConfigurator.createWith { (_: CreationalContext[AnyRef]) =>
                provider.get().asInstanceOf[AnyRef]
              }
            }

          // Binding to an injected provider
          case Some(ProviderConstructionTarget(providerImpl: Class[Provider[AnyRef]])) =>

            // Play already provides a binding for ConfigurationProvider, so don't bind it
            if (providerImpl != classOf[ConfigurationProvider]) {
              // Register the provider as a bean itself
              val providerBean = abd.addBean[Provider[AnyRef]]()
              val annotatedType = beanManager.createAnnotatedType(providerImpl.asInstanceOf[Class[Provider[AnyRef]]])
              providerBean.read(annotatedType)
            }

            beanConfigurator.createWith { (_: CreationalContext[AnyRef]) =>
              val instance = injector.instanceOf(providerImpl.asInstanceOf[Class[AnyRef]])
              instance.asInstanceOf[Provider[AnyRef]].get()
            }

          // Alias to another binding
          case Some(BindingKeyTarget(key: BindingKey[AnyRef])) =>
            beanConfigurator.createWith { (_: CreationalContext[AnyRef]) =>
              injector.instanceOf(key)
            }
        }

        // Set the bean class. The bean class is the class that provides the bean. In our case, it's the module.
        beanConfigurator.beanClass(module.getClass)

        // Override the types to just be the type for this binding
        beanConfigurator.types(binding.key.clazz)

        binding.key.qualifier.foreach {
          case QualifierClass(clazz) =>
            beanConfigurator.addQualifier(AnnotationTypeFactory.create(clazz))
          case QualifierInstance(annotation) =>
            beanConfigurator.addQualifier(annotation)
        }

        binding.scope.foreach(beanConfigurator.scope)
        // Ensure it's singleton if configured to be eagerly instantiated
        if (binding.eager) {
          beanConfigurator.scope(classOf[javax.inject.Singleton])
        }
      }

      bindings.collect {
        case eagerBinding if eagerBinding.eager => eagerBinding.key
      }
    }
  }

  def instantiateEagerSingletons(@Observes() adv: AfterDeploymentValidation, beanManager: BeanManager): Unit = {

    val injector = new CdiInjector(beanManager)

    // Start all the eager singletons
    eagerSingletonBindings.foreach { bindingKey =>
      injector.instanceOf(bindingKey)
    }
  }

}

private class CdiModule(context: ApplicationLoader.Context, injector: CdiInjector) extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[Injector].to(injector),
    bind[play.inject.Injector].to[DelegateInjector],
    bind[OptionalSourceMapper].to(new OptionalSourceMapper(context.sourceMapper)),
    bind[WebCommands].to(context.webCommands),
    bind[ApplicationLifecycle].to(context.lifecycle),
    bind[TemporaryFileReaperConfiguration].toProvider[TemporaryFileReaperConfigurationProvider]
  ) ++ routerBindings(environment, configuration)

  private def routerBindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Router.load(environment, configuration).collect {
      case generated if classOf[GeneratedRouter].isAssignableFrom(generated) => bind(generated).toSelf
    }.toSeq
  }
}



@Singleton
class TemporaryFileReaperConfigurationProvider @Inject()(configuration: Configuration) extends Provider[TemporaryFileReaperConfiguration] {
  lazy val get = TemporaryFileReaperConfiguration.fromConfiguration(configuration)
}
