package facesearch.ws.endpoint

import facesearch.ws.endpoint.schema.WsCommand
import facesearch.ws.endpoint.schema.WsCommandDecoder
import facesearch.ws.endpoint.schema.WsCommandEncoder
import javax.websocket.*
import javax.websocket.server.ServerEndpoint

@ServerEndpoint(value = "/ws/updates",
        decoders = [WsCommandDecoder::class],
        encoders = [WsCommandEncoder::class])
class WsEndpoint {
    @OnOpen
    fun opened(session : Session) {
        println("Opened ${session.id}")
    }

    @OnMessage
    fun message(session : Session, command : WsCommand) {
        println("Message from ${session.id} -> ${command.type}, ${command.cmd}")
    }

    @OnClose
    fun closed(session : Session) {
        println("Closed ${session.id}")
    }

    @OnError
    fun error(session : Session, throwable : Throwable) {
        println("Error ${session.id} -> ${throwable.message}")
    }
}