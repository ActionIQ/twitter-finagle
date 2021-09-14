package com.twitter.finagle

import com.twitter.finagle.client.{StackClient, StdStackClient, Transporter}
import com.twitter.finagle.param.WithSessionPool
import com.twitter.finagle.postgresql.transport.Packet
import com.twitter.finagle.postgresql.{DelayedRelease, Params, Request, Response}
import com.twitter.finagle.transport.{Transport, TransportContext}
import java.net.SocketAddress

object PostgreSql {

  val defaultStack: Stack[ServiceFactory[Request, Response]] =
    StackClient
      .newStack[Request, Response]
      // We add a DelayedRelease module at the bottom of the stack to ensure
      // that the pooling levels above don't discard an active session.
      .replace(StackClient.Role.prepConn, DelayedRelease.module(StackClient.Role.prepConn))
      .replace(
        StackClient.Role.requestDraining,
        DelayedRelease.module(StackClient.Role.requestDraining))

  val defaultParams: Stack.Params = StackClient.defaultParams

  case class Client(
    stack: Stack[ServiceFactory[Request, Response]] = defaultStack,
    params: Stack.Params = defaultParams)
      extends StdStackClient[Request, Response, Client]
      with WithSessionPool[Client] {

    override type In = Packet
    override type Out = Packet
    override type Context = TransportContext

    type ClientTransport = Transport[In, Out] { type Context <: TransportContext }

    def withCredentials(u: String, p: Option[String]): Client =
      configured(Params.Credentials(u, p))

    def withDatabase(db: String): Client =
      configured(Params.Database(Some(db)))

    def newRichClient(dest: String): postgresql.Client =
      postgresql.Client(newClient(dest))

    override protected def newTransporter(
      addr: SocketAddress
    ): Transporter[Packet, Packet, TransportContext] =
      new postgresql.PgSqlTransporter(addr, params)

    override protected def newDispatcher(transport: ClientTransport): Service[Request, Response] =
      postgresql.ClientDispatcher.cached(transport, params)

    override protected def copy1(
      stack: Stack[ServiceFactory[Request, Response]] = this.stack,
      params: Stack.Params = this.params
    ): Client = copy(stack, params)
  }

  def client: Client = Client()
}
