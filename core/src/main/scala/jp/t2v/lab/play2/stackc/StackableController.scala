package jp.t2v.lab.play2.stackc

import play.api.mvc._
import java.util.UUID
import concurrent.ExecutionContext
import util.{Failure, Success}

trait StackableController {
   self: Controller =>

  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global

  def txAction[A](p: BodyParser[A])(f: Xid => Request[A] => Result): Action[A] = Action(p) { req =>
    val xid = generateXid
    try {
      doCleanup(xid)(proceed(xid, req)(f))
    } catch {
      case e: Exception => cleanupOnFailed(xid, e); throw e
    }
  }

  def txAction(f: Xid => Request[AnyContent] => Result): Action[AnyContent] = txAction(parse.anyContent)(f)

  def proceed[A](xid: Xid, req: Request[A])(f: Xid => Request[A] => Result): Result = f(xid)(req)

  def cleanupOnSucceeded(xid: Xid): Unit = ()

  def cleanupOnFailed(xid: Xid, e: Exception): Unit = cleanupOnSucceeded(xid)

  private def doCleanup(xid: Xid)(result: Result): Result = result match {
    case p: PlainResult => {cleanupOnSucceeded(xid); p}
    case AsyncResult(f) => AsyncResult {
      f andThen {
        case Success(r) => doCleanup(xid)(r)
        case Failure(e: Exception) => cleanupOnFailed(xid, e)
      }
    }
  }

  private def generateXid: Xid = Xid(UUID.randomUUID().toString)

}

case class Xid(token: String)
