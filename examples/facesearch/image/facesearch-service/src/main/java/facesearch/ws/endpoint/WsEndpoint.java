package facesearch.ws.endpoint;

import org.apache.kafka.clients.producer.ProducerRecord;

import facesearch.ws.endpoint.schema.*;
import facesearch.ws.service.FacesearchWsService;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ServerEndpoint(value = "/ws/updates",
        decoders = WsCommandDecoder.class,
        encoders = WsCommandEncoder.class)
public class WsEndpoint {

    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<Session>());

    @OnOpen
    public void onOpen(Session session) throws IOException {
        sessions.add(session);
        System.out.println("Websocket session id " + session.getId() + " opened");
    }

    @OnMessage
    public void onMessage(Session session, WsCommand command) throws IOException {
        // Handle new messages
        System.out.println("onMessage");

        System.out.println("Put :TodoCommand (" + command.type + ", " + command.cmd + ") on facesearch-commands");
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        sessions.remove(session);
        System.out.println("Websocket session id " + session.getId() + " closed");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.out.println("Websocket session id " + session.getId() + " error: " + throwable.getMessage());
    }

    public static void broadcast(WsUpdate message) {
        WsUpdateEncoder encoder = new WsUpdateEncoder();
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    String rawJson = encoder.encode(message);
                    session.getBasicRemote().sendText(rawJson);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (EncodeException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void send(WsUpdate message, String webSocketSessionId) {
        for (Session session : sessions) {
            if (session.isOpen() && session.getId() == webSocketSessionId) {
                try {
                    WsUpdateEncoder encoder = new WsUpdateEncoder();
                    String rawJson = encoder.encode(message);
                    session.getBasicRemote().sendText(rawJson);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (EncodeException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
