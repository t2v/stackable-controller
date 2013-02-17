package models

sealed abstract class Permission {

}

case object Administrator extends Permission
case object NormalUser extends Permission
