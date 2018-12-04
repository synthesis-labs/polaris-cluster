package polaris.kafka

import com.google.gson.JsonParser
import facesearch.ws.endpoint.WsEndpoint
import facesearch.ws.endpoint.schema.*
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.kstream.KStream
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer
import polaris.kafka.websocket.Command
import polaris.kafka.websocket.Update
import java.io.IOException
import java.util.*
import javax.websocket.*
import javax.websocket.server.ServerEndpoint
import javax.websocket.server.ServerEndpointConfig

class CommandUpdateWebsocket(
        val port: Int,
        val path : String,
        val commandTopic : SafeTopic<String, Command>,
        val updateTopic : KStream<String, Update>) {

    val server : Server
    val connector : ServerConnector
    val context : ServletContextHandler
    val wscontainer : ServerContainer

    init {
        server = Server()
        connector = ServerConnector(server)
        connector.port = port
        server.addConnector(connector)
        context = ServletContextHandler(ServletContextHandler.SESSIONS)
        context.contextPath = "/"
        server.handler = context

        val endpointHandler = CommandUpdateEndpointHandler(commandTopic, updateTopic)

        val serverEndpointConfig =
            ServerEndpointConfig
                .Builder
                .create(
                    CommandUpdateEndpointHandler::class.java,
                    path)
                .configurator(CommandUpdateConfigurator(endpointHandler))
                .encoders(listOf(WsCommandEncoder::class.java))
                .decoders(listOf(WsCommandDecoder::class.java))
                .build()

        wscontainer = WebSocketServerContainerInitializer.configureContext(context)
        wscontainer.addEndpoint(serverEndpointConfig)

        // Start the command topic producer (because we don't source from a stream)
        //
        commandTopic.startProducer()

        server.start()
        server.dump(System.out)
    }

    fun join() {
        server.join()
    }
}

// This is purely here so we can pass stuff into our CommandUpdateEndpointHandler
//
class CommandUpdateConfigurator(
        val endpointHandler : CommandUpdateEndpointHandler)
    : ServerEndpointConfig.Configurator() {
    override fun <T : Any?> getEndpointInstance(endpointClass: Class<T>?): T {
        return endpointHandler as T
    }
}

@ServerEndpoint(value = "",
        decoders = [WsCommandDecoder::class],
        encoders = [WsCommandEncoder::class])
class CommandUpdateEndpointHandler(val commandTopic : SafeTopic<String, Command>,
                                   val updateTopic : KStream<String, Update>) {

    private val sessions = Collections.synchronizedSet(HashSet<Session>())

    init {
        println("Constructing CommandUpdateEndpointHandler")
        updateTopic.foreach {session, update ->
            val wsUpdate = WsUpdate()
            wsUpdate.type = update.getType()
            wsUpdate.action = update.getAction()

            val parser = JsonParser()
            wsUpdate.data = parser.parse(update.getData())

            println("Pushing update to websocket")

            // Should check if session is set and only send to that one... TODO
            //
            broadcast(wsUpdate)
        }
    }

    @OnOpen
    fun opened(session: Session) {
        sessions.add(session)
        session.maxBinaryMessageBufferSize = 10 * 1024 * 1024
        session.maxTextMessageBufferSize = 10 * 1024 * 1024
        println("Opened ${session.id}")
    }

    @OnMessage
    fun message(session : Session, command : WsCommand) {
        println("Message from ${session.id} -> ${command.type}, ${command.cmd}")

        val avroCommand = Command()
        avroCommand.setType(command.type)
        avroCommand.setCmd(command.cmd)

        // Optional data payload
        //
        if (command.data != null) {
            avroCommand.setData(command.data.toString())
        } else {
            avroCommand.setData("{}")
        }

        // Key is the websocket id (TODO it's crap right now, but need to figure out a unique one)
        //
        val record = ProducerRecord<String, Command>(commandTopic.topic, session.id, avroCommand)
        commandTopic.producer?.send(record)
    }

    @OnClose
    fun closed(session : Session) {
        sessions.remove(session)
        println("Closed ${session.id}")
    }

    @OnError
    fun error(session : Session, throwable : Throwable) {
        println("Error ${session.id} -> ${throwable.message}")
    }

    fun broadcast(message: WsUpdate) {
        val encoder = WsUpdateEncoder()
        for (session in sessions) {
            if (session.isOpen()) {
                try {
                    val rawJson = encoder.encode(message)
                    session.getBasicRemote().sendText(rawJson)
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: EncodeException) {
                    e.printStackTrace()
                }

            }
        }
    }
}
