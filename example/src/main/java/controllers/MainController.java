package controllers;

import play.mvc.Controller;
import play.mvc.Result;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MainController extends Controller {

  public Result index() {
    return ok("Hello world!");
  }

}
