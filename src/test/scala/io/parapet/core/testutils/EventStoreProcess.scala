package io.parapet.core.testutils

import cats.effect.IO
import io.parapet.core.Process
import io.parapet.core.Event
import io.parapet.core.Event._
import io.parapet.instances.DslInstances.catsInstances.effect._
import io.parapet.instances.DslInstances.catsInstances.flow._

import scala.collection.mutable.ListBuffer

class EventStoreProcess(enableSystemEvents: Boolean = false)
    extends Process[IO] {
  override val name: String = "event-store-process"
  private val _events = ListBuffer[Event]()
  override val handle: Receive = {
    case e@(Start | Stop) => if (enableSystemEvents) eval(_events += e) else empty
    case e => eval(_events += e)
  }

  def events: Seq[Event] = collection.immutable.Seq(_events: _*)

}