package controllers.stacks

import play.api.mvc.{Result, Controller}
import jp.t2v.lab.play2.stackc.{RequestAttributeKey, ScopedRequest, StackableController}
import controllers.AuthConfigImpl
import jp.t2v.lab.play20.auth.Auth

trait AuthElement extends StackableController with AuthConfigImpl {
    self: Controller with Auth =>

  case object AuthKey extends RequestAttributeKey
  case object AuthorityKey extends RequestAttributeKey

  abstract override def proceed[A](req: ScopedRequest[A])(f: ScopedRequest[A] => Result): Result = {
    (for {
      authority <- req.getAs[Authority](AuthorityKey).toRight(authorizationFailed(req)).right
      user      <- authorized(authority)(req).right
    } yield super.proceed(req.set(AuthKey, user))(f)).merge
  }

  implicit def loggedIn[A](implicit req: ScopedRequest[A]): User = req.getAs[User](AuthKey).get

}
