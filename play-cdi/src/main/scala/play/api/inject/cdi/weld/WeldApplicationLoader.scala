package play.api.inject.cdi.weld

import org.jboss.weld.environment.se.Weld
import play.api.inject.cdi.{CdiInjector, CdiPlayExtension}
import play.api.{Application, ApplicationLoader, LoggerConfigurator}

import scala.concurrent.Future
import scala.util.control.NonFatal

class WeldApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {

    try {
      // Configure logging
      LoggerConfigurator(context.environment.classLoader).foreach { lc =>
        lc.configure(context.environment, context.initialConfiguration, Map.empty)
      }

      // Bootstrap weld
      val weld = new Weld()
      weld.setClassLoader(context.environment.classLoader)
      weld.addExtension(new CdiPlayExtension(context))
      val weldContainer = weld.initialize()

      // Register stop hook to shutdown weld
      context.lifecycle
        .addStopHook { () =>
          weldContainer.close()
          Future.successful(())
        }

      val application = new CdiInjector(weldContainer.getBeanManager).instanceOf[Application]
      application
    } catch {
      case NonFatal(e) =>
        try {
          // Shutdown the lifecycle if there was an exception during CDI initialization.
          // This ensures, for example, that the actor system will get shut down.
          context.lifecycle.stop()
        } catch {
          case NonFatal(e) =>
          // Ignore, it's probably caused by the same thing that the exception we're handling is caused by
        }
        throw e
    }
  }
}


