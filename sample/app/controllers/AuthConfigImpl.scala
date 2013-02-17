package controllers

import jp.t2v.lab.play20.auth.AuthConfig
import play.api.mvc.{Result, RequestHeader, Controller}
import models._
import scala.reflect.classTag
import scala.reflect.ClassTag
import scalikejdbc._

trait AuthConfigImpl extends AuthConfig {
    self: Controller =>

  type Id = AccountId

  type User = Account

  type Authority = Permission

  val idTag: ClassTag[Id] = classTag[Id]

  val sessionTimeoutInSeconds: Int = 3600

  def resolveUser(id: Id): Option[User] = DB.localTx { implicit s => Account.findById(id) }

  def loginSucceeded(request: RequestHeader): Result = Ok("")

  def logoutSucceeded(request: RequestHeader): Result = Ok("")

  def authenticationFailed(request: RequestHeader): Result = Unauthorized("")

  def authorizationFailed(request: RequestHeader): Result = Forbidden("no permission")

  def authorize(user: User, authority: Authority): Boolean = true

  override lazy val cookieSecureOption: Boolean = play.api.Play.current.configuration.getBoolean("auth.cookie.secure").getOrElse(false)

}
