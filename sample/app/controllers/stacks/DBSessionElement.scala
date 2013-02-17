package controllers.stacks

import play.api.mvc.{Result, Request, Controller}
import java.util.concurrent.ConcurrentHashMap
import scalikejdbc._
import jp.t2v.lab.play2.stackc.StackableController

trait DBSessionElement extends StackableController {
    self: Controller =>

  private val sessions: java.util.Map[Xid, (DB, DBSession)] = new ConcurrentHashMap[Xid, (DB, DBSession)]()

  abstract override def proceed[A](xid: Xid, req: Request[A])(f: Xid => Request[A] => Result): Result = {
    val db = DB.connect()
    val tx = db.newTx
    tx.begin()
    sessions.put(xid, (db, db.withinTxSession(tx)))
    super.proceed(xid, req)(f)
  }

  abstract override def cleanupOnSucceeded(xid: Xid): Unit = {
    try {
      val (db, session) = sessions.remove(xid)
      db.currentTx.commit()
      session.close()
    } finally {
      super.cleanupOnSucceeded(xid)
    }
  }

  abstract override def cleanupOnFailed(xid: Xid, e: Exception): Unit = {
    try {
      val (db, session) = sessions.remove(xid)
      db.currentTx.rollback()
      session.close()
    } finally {
      super.cleanupOnFailed(xid, e)
    }
  }

  implicit def dbSession(implicit xid: Xid): DBSession = sessions.get(xid)._2

}
