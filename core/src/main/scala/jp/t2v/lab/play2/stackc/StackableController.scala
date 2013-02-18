package jp.t2v.lab.play2.stackc

import play.api.mvc._
import concurrent.ExecutionContext
import util.{Try, Failure, Success}

trait StackableController {
    self: Controller =>

  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  final def StackAction[A](p: BodyParser[A], params: (RequestAttributeKey, Any)*)(f: RequestWithAttributes[A] => Result): Action[A] = Action(p) { req =>
    val request = RequestWithAttributes(req, params.toMap)
    try {
      cleanup(request, proceed(request)(f))
    } catch {
      case e: Exception => cleanupOnFailed(request, e); throw e
    }
  }

  def StackAction(params: (RequestAttributeKey, Any)*)(f: RequestWithAttributes[AnyContent] => Result): Action[AnyContent] = StackAction(parse.anyContent, params: _*)(f)

  def proceed[A](request: RequestWithAttributes[A])(f: RequestWithAttributes[A] => Result): Result = f(request)

  def cleanupOnSucceeded[A](request: RequestWithAttributes[A]): Unit = ()

  def cleanupOnFailed[A](request: RequestWithAttributes[A], e: Exception): Unit = ()

  private def cleanup[A](request: RequestWithAttributes[A], result: Result): Result = result match {
    case p: PlainResult => {cleanupOnSucceeded(request); p}
    case AsyncResult(f) => AsyncResult {
      f andThen {
        case Success(r)            => cleanup(request, r)
        case Failure(e: Exception) => cleanupOnFailed(request, e)
      }
    }
  }

}

trait RequestAttributeKey

case class RequestWithAttributes[A](underlying: Request[A], attributes: Map[RequestAttributeKey, Any]) extends WrappedRequest[A](underlying) {

  def getAs[B](key: RequestAttributeKey): Option[B] = {
    attributes.get(key).flatMap { item =>
      Try(item.asInstanceOf[B]).toOption
    }
  }

  def set(key: RequestAttributeKey, value: Any): RequestWithAttributes[A] = RequestWithAttributes[A](underlying, attributes + (key -> value))

}
