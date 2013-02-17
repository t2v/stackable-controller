package models

import scalikejdbc.DBSession

case class Account(id: AccountId, name: String, hashedPassword: String) {

}

object Account {

  def findById(id: AccountId)(implicit session: DBSession): Option[Account] = Some(Account(1, "test", "test"))

}
