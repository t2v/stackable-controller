package controllers.stack

import play.api.mvc.{Result, Controller}
import jp.t2v.lab.play2.stackc.{RequestAttributeKey, RequestWithAttributes, StackableController}
import jp.t2v.lab.play20.auth.{Auth, AuthConfig}

trait AuthElement extends StackableController {
    self: Controller with Auth with AuthConfig =>

  case object AuthKey extends RequestAttributeKey[User]
  case object AuthorityKey extends RequestAttributeKey[Authority]

  override def proceed[A](req: RequestWithAttributes[A])(f: RequestWithAttributes[A] => Result): Result = {
    (for {
      authority <- req.get(AuthorityKey).toRight(authorizationFailed(req)).right
      user      <- authorized(authority)(req).right
    } yield super.proceed(req.set(AuthKey, user))(f)).merge
  }

  implicit def loggedIn[A](implicit req: RequestWithAttributes[A]): User = req.get(AuthKey).get

}
