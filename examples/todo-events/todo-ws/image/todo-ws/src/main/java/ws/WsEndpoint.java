package ws;

import org.apache.kafka.clients.producer.ProducerRecord;
import protocol.*;
import protocol.list.ItemCommand;
import protocol.list.ItemCommandAction;
import protocol.list.ListCommand;
import protocol.list.ListCommandAction;
import stream.Blueprint;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ServerEndpoint(value = "/updates",
        decoders = WsCommandDecoder.class,
        encoders = WsCommandEncoder.class)
public class WsEndpoint {

    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<Session>());

    @OnOpen
    public void onOpen(Session session) throws IOException {
        // Get session and WebSocket connection
        sessions.add(session);
        System.out.println("onOpen");
    }

    @OnMessage
    public void onMessage(Session session, WsCommand command) throws IOException {
        // Handle new messages
        System.out.println("onMessage");

        // Dispatch command down to the correct underlying service
        // Kindy of stringy switchy - but because it's incoming JSON and I'm lazy
        //
        switch (command.type) {
            case "REFRESH": {
                Blueprint.queryListTable(session.getId());
                Blueprint.queryItemTable(session.getId());
                break;
            }
            case "LIST": {

                // Get the name of the list
                //
                String listName = command.data.getAsJsonObject().get("name").getAsString();

                ListCommand listServiceCommand = new ListCommand();
                listServiceCommand.setName(listName);

                switch (command.cmd) {
                    case "CREATE":
                        listServiceCommand.setAction(ListCommandAction.CREATE);
                        break;
                    case "DELETE":
                        listServiceCommand.setAction(ListCommandAction.DELETE);
                        break;
                    case "UPDATE":
                        listServiceCommand.setAction(ListCommandAction.UPDATE);
                        break;
                    case "MARK_COMPLETED":
                        listServiceCommand.setAction(ListCommandAction.MARK_COMPLETED);
                        break;
                    case "MARK_UNCOMPLETED":
                        listServiceCommand.setAction(ListCommandAction.MARK_UNCOMPLETED);
                        break;
                    default:
                        // TODO be better
                        //
                        throw new IOException("Invalid cmd field");
                }

                ProducerRecord record = new ProducerRecord("todo-list-commands", listServiceCommand);
                Blueprint.commandProducer.send(record);
                System.out.println("Put " + listServiceCommand.getAction() + " on LIST service");
                break;
            }
            case "ITEM": {

                // Get the name of the list
                //
                String itemName = command.data.getAsJsonObject().get("name").getAsString();
                String listName = command.data.getAsJsonObject().get("list").getAsString();

                ItemCommand itemServiceCommand = new ItemCommand();
                itemServiceCommand.setName(itemName);
                itemServiceCommand.setList(listName);

                switch (command.cmd) {
                    case "CREATE":
                        itemServiceCommand.setAction(ItemCommandAction.CREATE.CREATE);
                        break;
                    case "DELETE":
                        itemServiceCommand.setAction(ItemCommandAction.DELETE);
                        break;
                    case "UPDATE":
                        itemServiceCommand.setAction(ItemCommandAction.UPDATE);
                        break;
                    case "MARK_COMPLETED":
                        itemServiceCommand.setAction(ItemCommandAction.MARK_COMPLETED);
                        break;
                    case "MARK_UNCOMPLETED":
                        itemServiceCommand.setAction(ItemCommandAction.MARK_UNCOMPLETED);
                        break;
                    default:
                        // TODO be better
                        //
                        throw new IOException("Invalid cmd field");
                }

                ProducerRecord record = new ProducerRecord("todo-item-commands", itemServiceCommand);
                Blueprint.commandProducer.send(record);
                System.out.println("Put " + itemServiceCommand.getAction() + " on ITEM service");
                break;
            }
        }
    }

    @OnClose
    public void onClose(Session session) throws IOException {
        // WebSocket connection closes
        sessions.remove(session);
        System.out.println("onClose");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        // Do error handling here
        System.out.println("onError");
    }

    // Static method - I don't like this at all
    public static void broadcast(String message) {
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void send(String message, String webSocketSessionId) {
        for (Session session : sessions) {
            if (session.isOpen() && session.getId() == webSocketSessionId) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}