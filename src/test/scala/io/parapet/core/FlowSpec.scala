package io.parapet.core

import cats.effect.{ContextShift, IO, Timer}
import cats.free.Free
import io.parapet.core.FlowSpec._
import io.parapet.core.Parapet._
import io.parapet.core.catsInstances.effect._
import io.parapet.core.catsInstances.flow._
import org.scalatest.{FlatSpec, _}

import scala.collection.mutable.{ListBuffer, Queue => SQueue}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class FlowSpec extends FlatSpec with Matchers with OptionValues {

  // m1 ~> p1
  "Flow send event" should "enqueue" in {
    val blackhole = new Blackhole()
    val program = TestEvent("1") ~> blackhole
    val env = interpret(program).unsafeRunSync()
    env.taskQueue.size shouldBe 1
    env.taskQueue.peek.value.evalEvent shouldBe TestEvent("1")
  }

  // m1 ~> p1 ++ m2 ~> p1
  "Flow composition of two seq flows" should "send events in program order" in {
    val blackhole = new Blackhole()
    val program = TestEvent("1") ~> blackhole ++ TestEvent("2") ~> blackhole
    val env = interpret(program).unsafeRunSync()
    env.taskQueue.size shouldBe 2
    env.taskQueue.pull.value.evalEvent shouldBe TestEvent("1")
    env.taskQueue.pull.value.evalEvent shouldBe TestEvent("2")
  }

  // par { m1 ~> p1 ++ m2 ~> p1 }
  "Par flow" should "send events in parallel" in {
    val blackhole = new Blackhole()
    val program =
      par {
        delay(3.seconds, TestEvent("1") ~> blackhole) ++ // <- delay first event
          TestEvent("2") ~> blackhole
      }

    val env = interpret(program).unsafeRunSync()
    env.taskQueue.size shouldBe 2
    env.taskQueue.pull.value.evalEvent shouldBe TestEvent("2")
    env.taskQueue.pull.value.evalEvent shouldBe TestEvent("1")
  }

  // m1 ~> p1 ++ par { m2 ~> p1 ++ m3 ~> p1 } ++ m4 ~> p1
  // valid executions:
  // (1) m1 -> m2 -> m3 -> m4
  // (2) m1 -> m3 -> m2 -> m4
  "Composition of seq and par flow" should "send events in program order" in {
    val blackhole = new Blackhole()
    val program =
      TestEvent("1") ~> blackhole ++
        par { TestEvent("2") ~> blackhole ++ TestEvent("3") ~> blackhole } ++
        TestEvent("4") ~> blackhole
    val env = interpret(program).unsafeRunSync()
    env.taskQueue.size shouldBe 4
    env.taskQueue.peek.value.evalEvent shouldBe TestEvent("1")
    env.taskQueue.peekLast.value.evalEvent shouldBe TestEvent("4")
  }

  "Send event" should "observe eval side effects" in {
    val blackhole = new Blackhole()
    var i = 0
    val program = eval {
      i = i + 1
    } ++ TestEvent(i.toString) ~> blackhole
    val env = interpret(program).unsafeRunSync()
    env.taskQueue.peek.value.evalEvent shouldBe TestEvent("1")
  }

  "Send event" should "observe suspend side effects" in {
    val blackhole = new Blackhole()
    var i = 0
    val program = suspend(IO{i = i + 1}) ++ TestEvent(i.toString) ~> blackhole
    val env = interpret(program).unsafeRunSync()
    env.taskQueue.peek.value.evalEvent shouldBe TestEvent("1")
  }

}

object FlowSpec {

  import cats.effect.IO._
  import cats.implicits._

  val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val ioTimer: Timer[IO] = IO.timer(ec)

  def interpret(f: Free[FlowOpOrEffect[IO, ?], Unit]): IO[TestEnv] = {
    val env = new TestEnv()
    val interpreter = ioFlowInterpreter(env) or ioEffectInterpreter
    f.foldMap[FlowState[IO, ?]](interpreter).runS(ListBuffer()).value.toList.sequence_.map(_ => env)
  }

  class TestEnv extends TaskQueueModule[IO] with CatsModule {
    override val taskQueue: IOQueue[Task[IO]] = new IOQueue[Task[IO]]()
    override val ctx: ContextShift[IO] = IO.contextShift(ec)
    override val timer: Timer[IO] = IO.timer(ec)
  }

  class IOQueue[A] extends Queue[IO, A] {
    val queue: SQueue[A] = new SQueue()

    override def enqueue(e: => A): IO[Unit] = IO(queue.enqueue(e))

    override def dequeue: IO[A] = IO(queue.dequeue())

    def peek: Option[A] = queue.headOption
    def peekLast: Option[A] = queue.lastOption

    def pull: Option[A] = Option(queue.dequeue())
    def size: Int = queue.size
  }

  case class TestEvent(id: String) extends Event

  class Blackhole extends Process[IO] {
    override val handle: Receive = {
      case _ => empty
    }
  }

  implicit class TaskOps[F[_]](task: Task[F]) {
    def evalEvent: Event = task.event()
  }

}