package io.parapet.core

package object exceptions {

  case class UnknownProcessException(message: String) extends RuntimeException(message)

  case class EventHandlingException(message: String = "", cause: Throwable = null) extends RuntimeException(message, cause)

  case class EventDeliveryException(message: String = "", cause: Throwable = null) extends RuntimeException(message, cause)

  case class EventQueueIsFullException(message: String) extends RuntimeException(message)

  case class EventMatchException(message: String) extends RuntimeException(message)

  case class UninitializedProcessException(message: String) extends RuntimeException(message)

  case class TerminatedProcessException(message: String) extends RuntimeException(message)

}