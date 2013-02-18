package jp.t2v.lab.play2.stackc

import play.api.mvc._
import concurrent.ExecutionContext
import util.{Failure, Success}
import reflect.ClassTag
import org.apache.commons.lang3.reflect.TypeUtils

trait StackableController {
   self: Controller =>

  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def txAction[A](p: BodyParser[A], params: (RequestAttributeKey, Any)*)(f: ScopedRequest[A] => Result): Action[A] = Action(p) { req =>
    val request = ScopedRequest(req, params.toMap)
    try {
      doCleanup(request, proceed(request)(f))
    } catch {
      case e: Exception => cleanupOnFailed(request, e); throw e
    }
  }

  def txAction(params: (RequestAttributeKey, Any)*)(f: ScopedRequest[AnyContent] => Result): Action[AnyContent] = txAction(parse.anyContent, params: _*)(f)

  def proceed[A](request: ScopedRequest[A])(f: ScopedRequest[A] => Result): Result = f(request)

  def cleanupOnSucceeded[A](request: ScopedRequest[A]): Unit = ()

  def cleanupOnFailed[A](request: ScopedRequest[A], e: Exception): Unit = ()

  private def doCleanup[A](request: ScopedRequest[A], result: Result): Result = result match {
    case p: PlainResult => {
      cleanupOnSucceeded(request)
      p
    }
    case AsyncResult(f) => AsyncResult {
      f andThen {
        case Success(r) => doCleanup(request, r)
        case Failure(e: Exception) =>
          cleanupOnFailed(request, e)
      }
    }
  }

}

trait RequestAttributeKey

case class ScopedRequest[A](underlying: Request[A], attributes: Map[RequestAttributeKey, Any]) extends WrappedRequest[A](underlying) {

  def getAs[B](key: RequestAttributeKey)(implicit ct: ClassTag[B]): Option[B] = {
    attributes.get(key).flatMap { item =>
      if (TypeUtils.isInstance(item, ct.runtimeClass)) Some(item.asInstanceOf[B]) else None
    }
  }

  def set(key: RequestAttributeKey, value: Any): ScopedRequest[A] = ScopedRequest[A](underlying, attributes + (key -> value))

}
