package controllers.stacks

import play.api.mvc.{Result, Controller}
import scalikejdbc._
import jp.t2v.lab.play2.stackc.{ScopedRequest, RequestAttributeKey, StackableController}

trait DBSessionElement extends StackableController {
    self: Controller =>

  case object DBSessionKey extends RequestAttributeKey

  abstract override def proceed[A](req: ScopedRequest[A])(f: ScopedRequest[A] => Result): Result = {
    val db = DB.connect()
    val tx = db.newTx
    tx.begin()
    super.proceed(req.set(DBSessionKey, (db, db.withinTxSession())))(f)
  }

  abstract override def cleanupOnSucceeded[A](req: ScopedRequest[A]): Unit = {
    try {
      req.getAs[(DB, DBSession)](DBSessionKey).map { case (db, session) =>
        db.currentTx.commit()
        session.close()
      }
    } finally {
      super.cleanupOnSucceeded(req)
    }
  }

  abstract override def cleanupOnFailed[A](req: ScopedRequest[A], e: Exception): Unit = {
    try {
      req.getAs[(DB, DBSession)](DBSessionKey).map { case (db, session) =>
        db.currentTx.rollback()
        session.close()
      }
    } finally {
      super.cleanupOnFailed(req, e)
    }
  }

  implicit def dbSession[A](implicit req: ScopedRequest): DBSession = req.getAs[(DB, DBSession)](DBSessionKey).get._2 // throw

}
