package jp.t2v.lab.play2.stackc

import play.api.mvc._
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import scala.util.control.{NonFatal, ControlThrowable}

trait StackableController {
    self: Controller =>

  private object ExecutionContextKey extends RequestAttributeKey[ExecutionContext]

  final def StackAction[A](p: BodyParser[A], params: (RequestAttributeKey[_], Any)*)(f: RequestWithAttributes[A] => Result)(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Action[A] = Action(p) { req =>
    val request = new RequestWithAttributes(req, new TrieMap[RequestAttributeKey[_], Any] ++= params += (ExecutionContextKey -> ctx))
    try {
      cleanup(request, proceed(request)(f))
    } catch {
      case e: ControlThrowable => cleanupOnSucceeded(request); throw e
      case NonFatal(e: Exception) => cleanupOnFailed(request, e); throw e
    }
  }

  def StackAction(params: (RequestAttributeKey[_], Any)*)(f: RequestWithAttributes[AnyContent] => Result): Action[AnyContent] = StackAction(parse.anyContent, params: _*)(f)

  def StackAction(f: RequestWithAttributes[AnyContent] => Result): Action[AnyContent] = StackAction()(f)

  def proceed[A](request: RequestWithAttributes[A])(f: RequestWithAttributes[A] => Result): Result = f(request)

  def cleanupOnSucceeded[A](request: RequestWithAttributes[A]): Unit = ()

  def cleanupOnFailed[A](request: RequestWithAttributes[A], e: Exception): Unit = ()

  private def cleanup[A](request: RequestWithAttributes[A], result: Result)(implicit ctx: ExecutionContext): Result = result match {
    case p: PlainResult => {cleanupOnSucceeded(request); p}
    case AsyncResult(f) => AsyncResult {
      f andThen {
        case Success(r)            => cleanup(request, r)
        case Failure(e: Exception) => cleanupOnFailed(request, e)
      }
    }
  }

  protected def StackActionExecutionContext(implicit req: RequestWithAttributes[_]): ExecutionContext =
    req.get(ExecutionContextKey).getOrElse(ExecutionContext.Implicits.global)

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
