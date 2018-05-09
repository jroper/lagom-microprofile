package play.api.inject.cdi.weld

import org.jboss.weld.environment.se.Weld
import play.api.inject.cdi.{CdiInjector, CdiPlayExtension}
import play.api.{Application, ApplicationLoader, LoggerConfigurator}

import scala.concurrent.Future

class WeldApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {

    // Configure logging
    LoggerConfigurator(context.environment.classLoader).foreach { lc =>
      lc.configure(context.environment, context.initialConfiguration, Map.empty)
    }

    import scala.collection.JavaConverters._
    context.environment.classLoader.getParent.getResources("META-INF/beans.xml").asScala.foreach(println)
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
  }
}


