package jp.t2v.lab.play2.txact

import play.api.mvc.{Result, Request, Controller}
import java.util.concurrent.ConcurrentHashMap
import models.Account

trait AuthController extends TransactionalController {
    self: Controller =>

  private val users: java.util.Map[Xid, Account] = new ConcurrentHashMap[Xid, Account]()

  abstract override def proceed[A](xid: Xid, req: Request[A])(f: Xid => Request[A] => Result): Result = {
    users.put(xid, null)
    super.proceed(xid, req)(f)
  }

  abstract override def cleanupOnSucceeded(xid: Xid): Unit = {
    try {
      users.remove(xid)
    } finally {
      super.cleanupOnSucceeded(xid)
    }
  }

  implicit def loggedIn(implicit xid: Xid): Account = users.get(xid)

}
