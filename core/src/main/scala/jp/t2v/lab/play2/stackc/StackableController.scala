package jp.t2v.lab.play2.stackc

import play.api.mvc._
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Failure, Success}
import scala.util.control.{NonFatal, ControlThrowable}

trait StackableController {
    self: Controller =>

  final class StackActionBuilder(params: (RequestAttributeKey[_], Any)*) extends ActionBuilder[RequestWithAttributes] {
    def invokeBlock[A](req: Request[A], block: (RequestWithAttributes[A]) => Future[SimpleResult]): Future[SimpleResult] = {
      val request = new RequestWithAttributes(req, new TrieMap[RequestAttributeKey[_], Any] ++= params)
      try {
        cleanup(request, proceed(request)(block))(StackActionExecutionContext(request))
      } catch {
        case e: ControlThrowable => cleanupOnSucceeded(request); throw e
        case NonFatal(e) => cleanupOnFailed(request, e); throw e
      }
    }
  }

  final def AsyncStack[A](p: BodyParser[A], params: (RequestAttributeKey[_], Any)*)(f: RequestWithAttributes[A] => Future[SimpleResult]): Action[A] = new StackActionBuilder(params: _*).async(p)(f)
  final def AsyncStack(params: (RequestAttributeKey[_], Any)*)(f: RequestWithAttributes[AnyContent] => Future[SimpleResult]): Action[AnyContent] = new StackActionBuilder(params: _*).async(f)
  final def AsyncStack(f: RequestWithAttributes[AnyContent] => Future[SimpleResult]): Action[AnyContent] = new StackActionBuilder().async(f)

  final def StackAction[A](p: BodyParser[A], params: (RequestAttributeKey[_], Any)*)(f: RequestWithAttributes[A] => SimpleResult): Action[A] = new StackActionBuilder(params: _*).apply(p)(f)
  final def StackAction(params: (RequestAttributeKey[_], Any)*)(f: RequestWithAttributes[AnyContent] => SimpleResult): Action[AnyContent] = new StackActionBuilder(params: _*).apply(f)
  final def StackAction(f: RequestWithAttributes[AnyContent] => SimpleResult): Action[AnyContent] = new StackActionBuilder().apply(f)

  def proceed[A](request: RequestWithAttributes[A])(f: RequestWithAttributes[A] => Future[SimpleResult]): Future[SimpleResult] = f(request)

  def cleanupOnSucceeded[A](request: RequestWithAttributes[A]): Unit = ()

  def cleanupOnFailed[A](request: RequestWithAttributes[A], e: Throwable): Unit = ()

  private def cleanup[A](request: RequestWithAttributes[A], result: Future[SimpleResult])(implicit ctx: ExecutionContext): Future[SimpleResult] = result andThen {
    case Success(p) => cleanupOnSucceeded(request)
    case Failure(e) => cleanupOnFailed(request, e)
  }

  protected object ExecutionContextKey extends RequestAttributeKey[ExecutionContext]

  protected def StackActionExecutionContext(implicit req: RequestWithAttributes[_]): ExecutionContext =
    req.get(ExecutionContextKey).getOrElse(play.api.libs.concurrent.Execution.defaultContext)

}

trait RequestAttributeKey[A]

class RequestWithAttributes[A](underlying: Request[A], attributes: TrieMap[RequestAttributeKey[_], Any]) extends WrappedRequest[A](underlying) {

  def get[B](key: RequestAttributeKey[B]): Option[B] =
    attributes.get(key).flatMap { item =>
      try Some(item.asInstanceOf[B]) catch {
        case _: ClassCastException => None
      }
    }

  /** side effect! */
  def set[B](key: RequestAttributeKey[B], value: B): RequestWithAttributes[A] = {
    attributes.put(key, value)
    this
  }

}

