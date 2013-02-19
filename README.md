## Play2 Stackable Action Composition

This module offers Action Composition Utilities to Play2.1 applications


## Target


This module targets the Scala version of Play2.1.x

This module has been tested on Play2.1.0


## Motivation

[Action Composition](http://www.playframework.com/documentation/2.1.0/ScalaActionsComposition) is somewhat limited in terms of composability.

For example, imagine that we want automatic DB transaction management, auth, and pjax functionality.


```scala
def TxAction(f: DBSession => Request[AnyContent] => Result): Action[AnyContent] = {
  Action { request =>
    DB localTx { session =>
      f(session)(request)
    }
  }
}

def AuthAction(authority: Authority)(f: User => DBSession => Request[AnyContent] => Result): Action[AnyContent] = {
  TxAction { session => request =>
    val user: Either[Result, User] = authorized(authority)(request)
    user.right.map(u => f(u)(session)(request)).merge
  }
}

type Template = Html => Html
def PjaxAction(f: Template => User => DBSession => Request[AnyContent] => Result): Action[AnyContent] = {
  AuthAction { user => session => request =>
    val template = if (req.headers.contains("X-Pjax")) views.html.pjaxTemplate else views.html.fullTemplate
    f(template)(user)(session)(request)
  }
}
```

```scala
def index = PjaxAction { template => user => session => request => 
  val messages = Message.findAll(session)
  Ok(views.hrml.index(messages)(template))
}
```

So far so good, but what if we need a new action that does both DB transaction management and pjax?

We have to create another PjaxAction.

```scala
def PjaxAction(f: Template => Request[AnyContent] => Result): Action[AnyContent] = {
  TxAction { session => request =>
    val template = if (req.headers.contains("X-Pjax")) views.html.pjaxTemplate else views.html.fullTemplate
    f(template)(session)(request)
  }
}
```

What a mess!


As an alternative, this module offers Composable Action composition using the power of traits.

## Example

1. First step, Create a sub trait of `StackableController` for every function.

    ```scala
    package controllers.stack

    import play.api.mvc.{Result, Controller}
    import jp.t2v.lab.play2.stackc.{RequestAttributeKey, RequestWithAtrributes, StackableController}
    import controllers.AuthConfigImpl
    import jp.t2v.lab.play20.auth.Auth

    trait AuthElement extends StackableController with AuthConfigImpl {
        self: Controller with Auth =>

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
    ```

    ```scala
    package controllers.stack

    import play.api.mvc.{Result, Controller}
    import scalikejdbc._
    import jp.t2v.lab.play2.stackc.{RequestWithAtrributes, RequestAttributeKey, StackableController}

    trait DBSessionElement extends StackableController {
        self: Controller =>

      case object DBSessionKey extends RequestAttributeKey

      abstract override def proceed[A](req: RequestWithAtrributes[A])(f: RequestWithAtrributes[A] => Result): Result = {
        val db = DB.connect()
        val tx = db.newTx
        tx.begin()
        super.proceed(req.set(DBSessionKey, (db, db.withinTxSession())))(f)
      }

      abstract override def cleanupOnSucceeded[A](req: RequestWithAtrributes[A]): Unit = {
        try {
          req.getAs[(DB, DBSession)](DBSessionKey).map { case (db, session) =>
            db.currentTx.commit()
            session.close()
          }
        } finally {
          super.cleanupOnSucceeded(req)
        }
      }

      abstract override def cleanupOnFailed[A](req: RequestWithAtrributes[A], e: Exception): Unit = {
        try {
          req.getAs[(DB, DBSession)](DBSessionKey).map { case (db, session) =>
            db.currentTx.rollback()
            session.close()
          }
        } finally {
          super.cleanupOnFailed(req, e)
        }
      }

      implicit def dbSession[A](implicit req: RequestWithAtrributes): DBSession = req.getAs[(DB, DBSession)](DBSessionKey).get._2 // throw

    }
    ```

    ```scala
    package controllers.stack

    import play.api.mvc.{Result, Controller}
    import play.api.templates.Html
    import jp.t2v.lab.play2.stackc.{RequestAttributeKey, RequestWithAtrributes, StackableController}
    import controllers.AuthConfigImpl
    import jp.t2v.lab.play20.auth.Auth

    trait PjaxElement extends StackableController with AuthConfigImpl {
        self: Controller with Auth =>


      type Template = Html => Html

      case object TemplateKey extends RequestAttributeKey

      abstract override def proceed[A](req: RequestWithAtrributes[A])(f: RequestWithAtrributes[A] => Result): Result = {
        val template = if (req.headers.contains("X-Pjax")) views.html.pjaxTemplate else views.html.fullTemplate
        super.proceed(req.set(TemplateKey, template))(f)
      }

      implicit def template[A](implicit req: RequestWithAtrributes[A]): User = req.getAs[Template](TemplateKey).get

    }
    ```

2. mix your traits into your Controller

    ```scala
    package controllers

    import play.api.mvc._
    import jp.t2v.lab.play20.auth.Auth
    import models._
    import views._
    import controllers.stack._

    object Application extends Controller with PjaxElement with DBSessionElement with AuthElement with Auth with AuthConfigImpl {

      def messages = StackAction(AuthorityKey -> NormalUser) { implicit req =>
        val messages = Message.findAll
        Ok(html.messages(messages)(loggedIn)(template))
      }

      def editMessage(id: MessageId) = StackAction(AuthorityKey -> Administrator) { implicit req =>
        val messages = Message.findAll
        Ok(html.messages(messages)(loggedIn)(template))
      }

    }
    ```

3. Mixin different combinations of traits, depending on the functionality that you need.

    ```scala
    package controllers

    import play.api.mvc._
    import jp.t2v.lab.play20.auth.Auth
    import models._
    import views._
    import controllers.stack._

    object NoAuthController extends Controller with PjaxElement with DBSessionElement {
      
      def messages = StackAction() { implicit req =>
        val messages = Message.findAll
        Ok(html.messages(messages)(GuestUser)(template))
      }

      def editMessage(id: MessageId) = StackAction() { implicit req =>
        val messages = Message.findAll
        Ok(html.messages(messages)(GuestUser)(template))
      }

    }
    ```

## How to use

Add a dependency declaration into your Build.scala or build.sbt file:

```scala
resolvers += "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "jp.t2v" %% "stackable-controller" % "0.1-SNAPSHOT"
```

