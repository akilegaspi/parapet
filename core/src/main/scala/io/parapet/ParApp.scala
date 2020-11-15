package io.parapet

import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.typesafe.scalalogging.Logger
import io.parapet.core.Dsl.{DslF, WithDsl}
import io.parapet.core.DslInterpreter.Interpreter
import io.parapet.core.Parapet.ParConfig
import io.parapet.core.processes.DeadLetterProcess
import io.parapet.core.{Context, EventLog, Parallel, Process, ProcessRef, Scheduler}
import io.parapet.syntax.FlowSyntax
import org.slf4j.LoggerFactory

import scala.language.{higherKinds, implicitConversions, reflectiveCalls}

trait ParApp[F[_]] extends WithDsl[F] with FlowSyntax[F] {

  type FlowOp[A] = io.parapet.core.Dsl.FlowOp[F, A]
  type Program = DslF[F, Unit]

  lazy val logger = Logger(LoggerFactory.getLogger(getClass.getCanonicalName))

  val config: ParConfig = ParConfig.default

  implicit def contextShift: ContextShift[F]

  implicit def ct: Concurrent[F]

  implicit def parallel: Parallel[F]

  implicit def timer: Timer[F]

  val eventLog: EventLog[F] = EventLog.stub

  def processes(args: Array[String]): F[Seq[Process[F]]]

  def deadLetter: F[DeadLetterProcess[F]] = ct.pure(DeadLetterProcess.logging)

  def interpreter(context: Context[F]): Interpreter[F]

  def unsafeRun(f: F[Unit]): Unit

  def run(args: Array[String]): F[Unit] = {
    for {
      ps <- processes(args)
      _ <- if (ps.isEmpty) {
        ct.raiseError[Unit](new RuntimeException("Initialization error:  at least one process must be provided"))
      } else ct.unit
      context <- Context(config, eventLog)
      scheduler <- Scheduler.apply[F](config.schedulerConfig, context, interpreter(context))
      _ <- context.start(scheduler)
      dlProcess <- deadLetter
      _ <- context.registerAll(ProcessRef.SystemRef, ps.toList :+ dlProcess)
      _ <- scheduler.start
    } yield ()

  }

  def main(args: Array[String]): Unit = {
    unsafeRun(run(args))
  }
}