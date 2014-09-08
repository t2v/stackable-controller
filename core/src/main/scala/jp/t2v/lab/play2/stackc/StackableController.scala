package jp.t2v.lab.play2.stackc

import play.api.mvc._
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}
import scala.util.control.{NonFatal, ControlThrowable}

trait StackableController {
    self: Controller =>

  final class StackActionBuilder(params: Attribute[_]*) extends ActionBuilder[RequestWithAttributes] {
    def invokeBlock[A](req: Request[A], block: (RequestWithAttributes[A]) => Future[Result]): Future[Result] = {
      val request = new RequestWithAttributes(req, new TrieMap[RequestAttributeKey[_], Any] ++= params.map(_.toTuple))
      try {
        cleanup(request, proceed(request)(block))(StackActionExecutionContext(request))
      } catch {
        case e: ControlThrowable => cleanupOnSucceeded(request); throw e
        case NonFatal(e) => cleanupOnFailed(request, e); throw e
      }
    }
  }

  final def AsyncStack[A](p: BodyParser[A], params: Attribute[_]*)(f: RequestWithAttributes[A] => Future[Result]): Action[A] = new StackActionBuilder(params: _*).async(p)(f)
  final def AsyncStack(params: Attribute[_]*)(f: RequestWithAttributes[AnyContent] => Future[Result]): Action[AnyContent] = new StackActionBuilder(params: _*).async(f)
  final def AsyncStack(f: RequestWithAttributes[AnyContent] => Future[Result]): Action[AnyContent] = new StackActionBuilder().async(f)

  final def StackAction[A](p: BodyParser[A], params: Attribute[_]*)(f: RequestWithAttributes[A] => Result): Action[A] = new StackActionBuilder(params: _*).apply(p)(f)
  final def StackAction(params: Attribute[_]*)(f: RequestWithAttributes[AnyContent] => Result): Action[AnyContent] = new StackActionBuilder(params: _*).apply(f)
  final def StackAction(f: RequestWithAttributes[AnyContent] => Result): Action[AnyContent] = new StackActionBuilder().apply(f)

  def proceed[A](request: RequestWithAttributes[A])(f: RequestWithAttributes[A] => Future[Result]): Future[Result] = f(request)

  def cleanupOnSucceeded[A](request: RequestWithAttributes[A]): Unit = ()

  def cleanupOnFailed[A](request: RequestWithAttributes[A], e: Throwable): Unit = ()

  private def cleanup[A](request: RequestWithAttributes[A], result: Future[Result])(implicit ctx: ExecutionContext): Future[Result] = result andThen {
    case Success(p) => cleanupOnSucceeded(request)
    case Failure(e) => cleanupOnFailed(request, e)
  }

  protected object ExecutionContextKey extends RequestAttributeKey[ExecutionContext]

  protected def StackActionExecutionContext(implicit req: RequestWithAttributes[_]): ExecutionContext =
    req.get(ExecutionContextKey).getOrElse(play.api.libs.concurrent.Execution.defaultContext)

}


trait RequestAttributeKey[A] {

  def ->(value: A): Attribute[A] = Attribute(this, value)

}

case class Attribute[A](key: RequestAttributeKey[A], value: A) {

  def toTuple: (RequestAttributeKey[A], A) = (key, value)

}

class RequestWithAttributes[A](underlying: Request[A], attributes: TrieMap[RequestAttributeKey[_], Any]) extends WrappedRequest[A](underlying) {

  def get[B](key: RequestAttributeKey[B]): Option[B] = attributes.get(key).asInstanceOf[Option[B]]

  /** side effect! */
  def set[B](key: RequestAttributeKey[B], value: B): RequestWithAttributes[A] = {
    attributes.put(key, value)
    this
  }

}

