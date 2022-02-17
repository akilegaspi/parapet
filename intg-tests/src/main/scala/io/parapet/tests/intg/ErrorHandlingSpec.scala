package io.parapet.tests.intg

import io.parapet.{Envelope, Event, ProcessRef}
import io.parapet.core.Events._
import io.parapet.core.Process
import io.parapet.core.exceptions.EventHandlingException
import io.parapet.core.processes.DeadLetterProcess
import io.parapet.tests.intg.ErrorHandlingSpec._
import io.parapet.testutils.EventStore
import org.scalatest.OptionValues._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec

abstract class ErrorHandlingSpec[F[_]] extends AnyWordSpec with IntegrationSpec[F] {

  import dsl._

  "System" when {
    "process failed to handle event" should {
      "send Failure event to the sender" in {
        val clientEventStore = new EventStore[F, Failure]

        val faultyServer = createFaultyServer[F]

        val client = new Process[F] {
          def handle: Receive = {
            case Start => Request ~> faultyServer
            case f: Failure => withSender{ sender =>
              sender shouldBe ProcessRef.SystemRef
              eval(clientEventStore.add(ref, f))
            }
          }
        }

        unsafeRun(clientEventStore.await(1, createApp(ct.pure(Seq(client, faultyServer))).run))

        clientEventStore.size shouldBe 1
        clientEventStore.get(client.ref).headOption.value should matchPattern {
          case Failure(Envelope(client.`ref`, Request, faultyServer.`ref`), _: EventHandlingException) =>
        }
      }
    }
  }

  "System" when {
    "process doesn't have error handling" should {
      "send Failure event to dead letter" in {
        val eventStore = new EventStore[F, DeadLetter]
        val deadLetter = new DeadLetterProcess[F] {
          def handle: Receive = {
            case f: DeadLetter => eval(eventStore.add(ref, f))
          }
        }
        val server = new Process[F] {
          def handle: Receive = {
            case Request => eval(throw new RuntimeException("server is down"))
          }
        }
        val client = new Process[F] {
          def handle: Receive = {
            case Start => Request ~> server
          }
        }

        unsafeRun(eventStore.await(1, createApp(ct.pure(Seq(client, server)), Some(ct.pure(deadLetter))).run))

        eventStore.size shouldBe 1
        eventStore.get(deadLetter.ref).headOption.value should matchPattern {
          case DeadLetter(Envelope(client.`ref`, Request, server.`ref`), _: EventHandlingException) =>
        }

      }
    }
  }

  "System" when {
    "process failed to handle Failure event" should {
      "send Failure event to dead letter" in {
        val eventStore = new EventStore[F, DeadLetter]
        val deadLetter = new DeadLetterProcess[F] {
          def handle: Receive = {
            case f: DeadLetter => eval(eventStore.add(ref, f))
          }
        }
        val server = new Process[F] {
          def handle: Receive = {
            case Request => eval(throw new RuntimeException("server is down"))
          }
        }
        val client = new Process[F] {
          def handle: Receive = {
            case Start => Request ~> server
            case _: Failure => eval(throw new RuntimeException("client failed to handle error"))
          }
        }

        unsafeRun(eventStore.await(1, createApp(ct.pure(Seq(client, server)), Some(ct.pure(deadLetter))).run))

        eventStore.size shouldBe 1
        eventStore.get(deadLetter.ref).headOption.value should matchPattern {
          case DeadLetter(Envelope(client.`ref`, Request, server.`ref`), _: EventHandlingException) =>
        }
      }
    }
  }

}

object ErrorHandlingSpec {

  def createFaultyServer[F[_]]: Process[F] = new Process[F] {

    import dsl._

    def handle: Receive = {
      case Request => eval(throw new RuntimeException("server is down"))
    }
  }

  object Request extends Event

}
