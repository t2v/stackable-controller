package models

import scalikejdbc._

case class Message(id: MessageId, body: String) {

}

object Message {

  def findAll(implicit session: DBSession): Seq[Message] = Nil

}
