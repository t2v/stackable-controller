package controllers.stack

import play.api.mvc.{Result, Controller}
import jp.t2v.lab.play2.stackc.{RequestAttributeKey, RequestWithAtrributes, StackableController}
import jp.t2v.lab.play20.auth.{Auth, AuthConfig}

trait AuthElement extends StackableController {
    self: Controller with Auth with AuthConfig =>

  case object AuthKey extends RequestAttributeKey
  case object AuthorityKey extends RequestAttributeKey

  abstract override def proceed[A](req: RequestWithAtrributes[A])(f: RequestWithAtrributes[A] => Result): Result = {
    (for {
      authority <- req.getAs[Authority](AuthorityKey).toRight(authorizationFailed(req)).right
      user      <- authorized(authority)(req).right
    } yield super.proceed(req.set(AuthKey, user))(f)).merge
  }

  implicit def loggedIn[A](implicit req: RequestWithAtrributes[A]): User = req.getAs[User](AuthKey).get

}
