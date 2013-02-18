package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth
import models._
import views._
import controllers.filters._

object Application extends Controller with DBSessionElement with AuthElement with Auth with AuthConfigImpl {
  
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def messages = ScopedAction(AuthorityKey -> NormalUser) { implicit req =>
    val messages = Message.findAll
    Ok(html.messages(messages)(loggedIn))
  }

  def editMessage(id: MessageId) = ScopedAction(AuthorityKey -> Administrator) { implicit req =>
    val messages = Message.findAll
    Ok(html.messages(messages)(loggedIn))
  }

}