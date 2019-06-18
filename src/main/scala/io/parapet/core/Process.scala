package io.parapet.core

import io.parapet.core.Dsl.{Dsl, DslF, Effects, FlowOps}
import io.parapet.core.Process._
import io.parapet.syntax.flow._

trait Process[F[_]] {
  self =>
  type Program = DslF[F, Unit]
  type Receive = ReceiveF[F]

  protected final val flowDsl = implicitly[FlowOps[F, Dsl[F, ?]]]
  protected final val effectDsl = implicitly[Effects[F, Dsl[F, ?]]]

  val name: String = "default"
  val ref: ProcessRef = ProcessRef.jdkUUIDRef

  val handle: Receive

  def apply(e: Event,
            ifUndefined: => Program = flowDsl.empty): Program =
    if (handle.isDefinedAt(e)) handle(e)
    else ifUndefined

  // composition of this and `that` process
  // todo add tests
  def ++(that: Process[F]): Process[F] = new Process[F] {
    override val handle: Receive = {
      case e => self.handle(e) ++ that.handle(e)
    }
  }

  override def toString: String = s"process[name=$name, ref=$ref]"
}

object Process {

  type ReceiveF[F[_]] = PartialFunction[Event, DslF[F, Unit]]

  def apply[F[_]](receive: ProcessRef => ReceiveF[F]): Process[F] = new Process[F] {
    override val handle: Receive = receive(this.ref)
  }

  def named[F[_]](pName: String, receive: ProcessRef => ReceiveF[F]): Process[F] = new Process[F] {
    override val name: String = pName
    override val handle: Receive = receive(this.ref)
  }

}