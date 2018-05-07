package play.api.inject.cdi.weld

import org.jboss.weld.environment.se.Weld
import play.api.inject.cdi.{CdiInjector, CdiPlayExtension}
import play.api.{Application, ApplicationLoader}

import scala.concurrent.Future

class WeldApplicationLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {

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


