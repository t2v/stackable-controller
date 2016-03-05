package test

import org.specs2.mutable._

import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends Specification {
  
  "Application" should {
    
    "send 404 on a bad request" in {
      val app = FakeApplication()
      running(app) {
        route(app, FakeRequest(GET, "/boum")) must beSome.which (status(_) == NOT_FOUND)
      }
    }
    
    "render the index page" in {
      val app = FakeApplication()
      running(app) {
        val home = route(app, FakeRequest(GET, "/")).get
        
        status(home) must equalTo(OK)
        contentType(home) must beSome.which(_ == "text/html")
        contentAsString(home) must contain ("Your new application is ready.")
      }
    }
  }
}
