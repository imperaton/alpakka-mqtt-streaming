package publish

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttClientSession, Mqtt}
import akka.stream.scaladsl.{BidiFlow, Flow, Keep, Sink, Source, TLSPlacebo, Tcp}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, WebSocketRequest, WebSocketUpgradeResponse}
import akka.stream._
import akka.util.ByteString

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import java.net.InetSocketAddress

object dummyPublisher extends App {

  // Start the Actor System
  implicit val actorSystem: ActorSystem = ActorSystem("alpakka-samples")
  implicit val ec: ExecutionContext = actorSystem.dispatcher


  val settings = MqttSessionSettings()
  val session = ActorMqttClientSession(settings)

  /* **** TCP-Connection ***********************************************************/
  // Uncomment to use tcp connection
  //
  // Use the standard Tcp flow which is ByteString --> ByteString
  /* val connection = Tcp().outgoingConnection("127.0.0.1", 1883)
  // Create the flow `mqttFlow` which take a mqtt-command as input and responds with an event.
  val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]],
                     Future[Tcp.OutgoingConnection]] =
    Mqtt
      .clientSessionFlow(session, ByteString("1"))
      .joinMat(connection)(Keep.right)*/
  /* *******************************************************************************/

  /* **** Websocket-Connection *****************************************************/
  // Uncomment to use websocket connection
  //
  // In Order to obtain the websockets ByteString --> ByteString flow we have to combine multiple layers:
  //     1. akka-http provides `webSocketClientLayer` which is bidirectional flow of the follwing form
  //                        +-------+
  //         ws.Message   ~>|       |~> SslTlsOutbound
  //                        |  (1)  |
  //         ws.Message   <~|       |<~ SslTlsInbound
  //                        +-------+
  //
  //    2. akka-streams provides an ssl layer
  //                        +-------+
  //       SslTlsOutbound ~>|       |~> ByteString
  //                        |  (2)  |
  //        SslTlsInbound <~|       |<~ ByteString
  //                        +-------+
  //
  //    3. The standard tcp flow is
  //                        +-------+
  //           ByteString ~>|  (3)  |~> ByteString
  //                        +-------+
  //
  //    By adding one more bidirectional layer which translates ByteStrings to ws.Messages (and the other
  //    way around) we get a flow ByteString --> ByteString
  //
  //                     +--+                +---+                    +---+                +---+
  //        ByteString ~>|  |~> ws.Message ~>|   |~> SslTlsOutbound ~>|   |~> ByteString ~>|   |
  //                     |  |                |(1)|                    |(2)|                |(3)|
  //        ByteString <~|  |<~ ws.Message <~|   |~> SslTlsInbound  <~|   |<~ ByteString <~|   |
  //                     +--+                +---+                    +---+                +---+
  val messageConverter: BidiFlow[ByteString, Message, Message, ByteString, NotUsed] =
    BidiFlow.fromFunctions[ByteString, Message, Message, ByteString] (
          BinaryMessage.Strict,
          (msg : Message) => msg.asBinaryMessage.getStrictData)
  val ws_request = WebSocketRequest(uri="ws://127.0.0.1:9001/mqtt", subprotocol = Some("mqtt"))
  val ws_layer = Http().webSocketClientLayer(ws_request)
  val connection: Flow[ByteString, ByteString, (Future[WebSocketUpgradeResponse], Future[Tcp.OutgoingConnection])] =
      messageConverter
      .atopMat(ws_layer)(Keep.right)
      .atop(TLSPlacebo())
      .joinMat(Tcp().outgoingConnection(new InetSocketAddress("127.0.0.1", 9001)))(Keep.both)
  // Create the flow `mqttFlow` which take a mqtt-command as input and responds with an event.
  val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]],
    (Future[WebSocketUpgradeResponse], Future[Tcp.OutgoingConnection])] =
    Mqtt
      .clientSessionFlow(session, ByteString("1"))
      .joinMat(connection)(Keep.right)
  /* *******************************************************************************/

  /* **** Test Websocket-Connection ************************************************/
  // // Breaks the Code below
  // val ((wsUpgrade, tcpConnection), flowClosed) =
  //   Source.single(ByteString("Strict hello world!"))
  //     .viaMat(connection)(Keep.right)
  //     .toMat(Sink.ignore)(Keep.both)
  //     .run()
  //
  // wsUpgrade.map(println)
  // tcpConnection.map(println)
  // Await.ready(flowClosed, Duration.Inf)
  /* *******************************************************************************/


  // Construct a source queue `mqttSink`. `mqttSinkDone` is a `Future` which completes with a List of events.
  val (mqttSink, mqttSinkDone) =
    Source
      .queue(100, OverflowStrategy.fail)
      .via(mqttFlow)
      .collect {
        case Right(event) =>
          event
      }
      .map { event =>
          println(s"Received event: $event")
          event
      }
      .toMat(Sink.seq)(Keep.both)
      .run()

  // Connect to mqtt-Broker
  val username = "mqttPublisher"
  val password = "ef039bc4-855d-4629-af67-53d0320ff331"
  mqttSink.offer(Command(Connect("alpakka", ConnectFlags.CleanSession, username, password)))

  // Publish Data
  val publishingDone = Source(1 to 10)
    .throttle(1, 1.second)
    .runForeach { x =>
      session ! Command(Publish(ControlPacketFlags.QoSAtLeastOnceDelivery, "/test/1", ByteString(s"ohi-$x")))
    }



  // Wait until all message are published
  publishingDone.onComplete(_ => {
    mqttSink.offer(Command(Disconnect))  // This leads to completion of `mqttSinkDone`
    mqttSinkDone.onComplete (events => {
      println(s"List of all Events: $events")
      mqttSink.complete()
      mqttSink.watchCompletion().foreach(_ => session.shutdown())
      actorSystem.terminate()
    })
  })

}
