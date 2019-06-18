package io.parapet.core.intg

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.IO
import io.parapet.core.Event._
import io.parapet.core.{Event, Process, ProcessRef}
import io.parapet.instances.DslInstances.catsInstances.effect._
import io.parapet.instances.DslInstances.catsInstances.flow._
import io.parapet.core.intg.EventDeliverySpec._
import io.parapet.implicits._
import org.scalatest.FlatSpec
import org.scalatest.Matchers.{empty => _, _}

class EventDeliverySpec extends FlatSpec with IntegrationSpec {

  "Event sent to each process" should "be eventually delivered" in {
    val counter = new AtomicInteger()
    val numOfProcesses = 1000
    val processes =
      createProcesses(numOfProcesses, () => counter.incrementAndGet())
    val program = processes.foldLeft(empty)((acc, p) => acc ++ QualifiedEvent(p.ref) ~> p)
    run(program ++ terminate, processes: _*)

    counter.get() shouldBe numOfProcesses
  }

  "Unmatched event" should "be ignored" in {
    val p = Process[IO](_ => {
      case Start => empty
    })

    val program = UnknownEvent ~> p ++ terminate

    run(program, p)
  }

}

object EventDeliverySpec {

  case class QualifiedEvent(pRef: ProcessRef) extends Event

  object UnknownEvent extends Event

  def createProcesses(numOfProcesses: Int, cb: () => Unit): Seq[Process[IO]] = {
    (0 until numOfProcesses).map { i =>
      new Process[IO] {
        override val name: String = s"p-$i"
        override val handle: Receive = {
          case QualifiedEvent(pRef) =>
            if (pRef != ref)
             eval(throw new RuntimeException(s"unexpected process ref. expected: $ref, actual: $pRef"))
            else eval(cb())
        }
      }
    }
  }
}