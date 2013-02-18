package jp.t2v.lab.play2.stackc

import play.api.mvc._
import scala.concurrent.ExecutionContext
import scala.util.{Try, Failure, Success}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

trait StackableController {
    self: Controller =>

  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  final def StackAction[A](p: BodyParser[A], params: (RequestAttributeKey, Any)*)(f: RequestWithAttributes[A] => Result): Action[A] = Action(p) { req =>
    val attributes = new ConcurrentHashMap[RequestAttributeKey, Any]()
    attributes.putAll(params.toMap.asJava)
    val request = RequestWithAttributes(req, attributes)
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

case class RequestWithAttributes[A](underlying: Request[A], attributes: java.util.Map[RequestAttributeKey, Any]) extends WrappedRequest[A](underlying) {

  def getAs[B](key: RequestAttributeKey): Option[B] = {
    Option(attributes.get(key)).flatMap { item =>
      Try(item.asInstanceOf[B]).toOption
    }
  }

  /** side effect! */
  def set(key: RequestAttributeKey, value: Any): RequestWithAttributes[A] = {
    attributes.put(key, value)
    this
  }

}
