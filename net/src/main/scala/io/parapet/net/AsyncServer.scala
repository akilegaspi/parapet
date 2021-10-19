package io.parapet.net

import io.parapet.core.Dsl.DslF
import io.parapet.core.Events.{Start, Stop}
import io.parapet.{ProcessRef, Event}
import io.parapet.core.api.Cmd.netServer
import org.slf4j.LoggerFactory
import org.zeromq.{SocketType, ZContext, ZMQException, ZMsg}
import zmq.ZError

class AsyncServer[F[_]](override val ref: ProcessRef, address: String, sink: ProcessRef)
  extends io.parapet.core.Process[F] {

  import AsyncServer._
  import dsl._

  private lazy val zmqContext = new ZContext(1)
  private lazy val server = zmqContext.createSocket(SocketType.ROUTER)
  private val logger = LoggerFactory.getLogger(ref.value)
  private val info: String = s"server[ref=$ref, address=$address]:"

  private def init = eval {
    try {
      server.bind(address)
      logger.info(s"$info is listening $address")
    } catch {
      case e: Exception =>
        e match {
          case zmqError: ZMQException if zmqError.getErrorCode == 48 => logger.error(s"$info address '$address' in use")
          case _ => ()
        }
        throw e
    }
  }

  private def step: DslF[F, Either[Throwable, netServer.Message]] =
    if (zmqContext.isClosed) {
      eval {
        logger.debug("server is not running. stop receive loop")
        Left(new RuntimeException("server is not running")).withRight[netServer.Message]
      }
    } else {
      eval {
        val clientId = server.recvStr()
        val msgBytes = server.recv()
        logger.debug(s"$info received message from client: $clientId")
        Right(netServer.Message(clientId, msgBytes)).withLeft[Throwable]
      }.handleError(e => eval(Left(e).withRight[netServer.Message])).flatMap {
        case Right(msg) => msg ~> sink ++ step
        case Left(err: org.zeromq.ZMQException) if err.getErrorCode == ZError.ETERM =>
          eval(logger.error(s"$info zmq context has been terminated. stop receive loop", err)) ++
            eval(Left(err).withRight[netServer.Message])
        case Left(err) => eval(logger.error(s"$info has failed to receive a message", err)) ++ step
      }
    }

  private def loop: DslF[F, Unit] = flow {
    step.map(_ => ())
  }

  override def handle: Receive = {
    case Start => init ++ fork(loop)

    case netServer.Send(clientId, data) =>
      eval {
        if (zmqContext.isClosed) {
          eval(logger.error("server is not running"))
        } else {
          logger.debug(s"send message to clientId=$clientId")
          val msg = new ZMsg()
          msg.add(clientId)
          msg.add(data)
          msg.send(server)
        }
      }

    case Stop =>
      eval {
        if (!zmqContext.isClosed) {
          logger.debug("closing zmq context")
          server.close()
          zmqContext.close()
        } else {
          logger.debug("zmq context is already closed")
        }
      }.handleError(err => eval(logger.error("an error occurred while closing zmq context", err)))

  }
}

object AsyncServer {

  def apply[F[_]](ref: ProcessRef, address: String, sink: ProcessRef): AsyncServer[F] =
    new AsyncServer(ref, address, sink)
}