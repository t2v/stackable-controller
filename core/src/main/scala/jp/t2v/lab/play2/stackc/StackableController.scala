package jp.t2v.lab.play2.stackc

import play.api.mvc._
import concurrent.ExecutionContext
import util.{Failure, Success}
import reflect.ClassTag
import org.apache.commons.lang3.reflect.TypeUtils

trait StackableController {
    self: Controller =>

  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  final def StackAction[A](p: BodyParser[A], params: (RequestAttributeKey, Any)*)(f: RequestWithAtrributes[A] => Result): Action[A] = Action(p) { req =>
    val request = RequestWithAtrributes(req, params.toMap)
    try {
      cleanup(request, proceed(request)(f))
    } catch {
      case e: Exception => cleanupOnFailed(request, e); throw e
    }
  }

  def StackAction(params: (RequestAttributeKey, Any)*)(f: RequestWithAtrributes[AnyContent] => Result): Action[AnyContent] = StackAction(parse.anyContent, params: _*)(f)

  def proceed[A](request: RequestWithAtrributes[A])(f: RequestWithAtrributes[A] => Result): Result = f(request)

  def cleanupOnSucceeded[A](request: RequestWithAtrributes[A]): Unit = ()

  def cleanupOnFailed[A](request: RequestWithAtrributes[A], e: Exception): Unit = ()

  private def cleanup[A](request: RequestWithAtrributes[A], result: Result): Result = result match {
    case p: PlainResult => {
      cleanupOnSucceeded(request)
      p
    }
    case AsyncResult(f) => AsyncResult {
      f andThen {
        case Success(r) => cleanup(request, r)
        case Failure(e: Exception) =>
          cleanupOnFailed(request, e)
      }
    }
  }

}

trait RequestAttributeKey

case class RequestWithAtrributes[A](underlying: Request[A], attributes: Map[RequestAttributeKey, Any]) extends WrappedRequest[A](underlying) {

  def getAs[B](key: RequestAttributeKey)(implicit ct: ClassTag[B]): Option[B] = {
    attributes.get(key).flatMap { item =>
      if (TypeUtils.isInstance(item, ct.runtimeClass)) Some(item.asInstanceOf[B]) else None
    }
  }

  def set(key: RequestAttributeKey, value: Any): RequestWithAtrributes[A] = RequestWithAtrributes[A](underlying, attributes + (key -> value))

}
