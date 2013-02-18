package controllers.stacks

import play.api.mvc.{Result, Request, Controller}
import java.util.concurrent.ConcurrentHashMap
import models.Account
import jp.t2v.lab.play2.stackc.{StackableController, Xid}

trait AuthElement extends StackableController {
    self: Controller =>

  private val users: java.util.Map[Xid, Account] = new ConcurrentHashMap[Xid, Account]()

  abstract override def proceed[A](xid: Xid, req: Request[A])(f: Xid => Request[A] => Result): Result = {
    users.put(xid, null)
    super.proceed(xid, req)(f)
  }

  abstract override def cleanupFinally(xid: Xid): Unit = {
    try {
      users.remove(xid)
    } finally {
      super.cleanupFinally(xid)
    }
  }

  implicit def loggedIn(implicit xid: Xid): Account = users.get(xid)

}
