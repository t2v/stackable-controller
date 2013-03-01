package controllers.stack

import play.api.mvc.{Result, Controller}
import scalikejdbc._
import jp.t2v.lab.play2.stackc.{RequestWithAttributes, RequestAttributeKey, StackableController}

trait DBSessionElement extends StackableController {
    self: Controller =>

  case object DBSessionKey extends RequestAttributeKey[(DB, DBSession)]

  override def proceed[A](req: RequestWithAttributes[A])(f: RequestWithAttributes[A] => Result): Result = {
    val db = DB.connect()
    val tx = db.newTx
    tx.begin()
    super.proceed(req.set(DBSessionKey, (db, db.withinTxSession())))(f)
  }

  override def cleanupOnSucceeded[A](req: RequestWithAttributes[A]): Unit = {
    try {
      req.get(DBSessionKey).map { case (db, session) =>
        db.currentTx.commit()
        session.close()
      }
    } finally {
      super.cleanupOnSucceeded(req)
    }
  }

  override def cleanupOnFailed[A](req: RequestWithAttributes[A], e: Exception): Unit = {
    try {
      req.get(DBSessionKey).map { case (db, session) =>
        db.currentTx.rollback()
        session.close()
      }
    } finally {
      super.cleanupOnFailed(req, e)
    }
  }

  implicit def dbSession[A](implicit req: RequestWithAttributes[A]): DBSession = req.get(DBSessionKey).get._2 // throw

}
