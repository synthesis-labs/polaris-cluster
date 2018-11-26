package facesearch.ws.service;

import com.google.gson.JsonParser;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import facesearch.ws.endpoint.WsEndpoint;
import facesearch.ws.endpoint.schema.WsUpdate;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class FacesearchWsService {

    public static KafkaProducer commandProducer;
    public static Properties defaultProperties = new Properties();
    public static String kafka_bootstrap_servers;
    public static String schema_registry_url;

    public static String todo_commands_topic;
    public static String todo_updates_topic;

    public static KafkaStreams streams;

    public static Server startWsServer() throws Exception {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Initialize javax.websocket layer
        ServerContainer wscontainer = WebSocketServerContainerInitializer.configureContext(context);

        // Add WebSocket endpoint to javax.websocket layer
        wscontainer.addEndpoint(WsEndpoint.class);

        server.start();
        server.dump(System.out);
        return server;
    }

    public static void configureKafka() {
        String kafka_application_id = "facesearch-ws-service";

        // All environmental configuration passed in from environment variables
        //
        kafka_bootstrap_servers = System.getenv("kafka_bootstrap_servers");
        schema_registry_url = System.getenv("schema_registry_url");
        System.out.println("kafka_bootstrap_servers: " + kafka_bootstrap_servers);
        System.out.println("schema_registry_url: " + schema_registry_url);

        // Streams config
        //
        defaultProperties.put(StreamsConfig.APPLICATION_ID_CONFIG, kafka_application_id);
        defaultProperties.put(StreamsConfig.CLIENT_ID_CONFIG, kafka_application_id + "-client");
        defaultProperties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka_bootstrap_servers);

        // How should we handle deserialization errors?
        //
        defaultProperties.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndFailExceptionHandler.class);

        // This is for producers really only
        //
        defaultProperties.put("key.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
        defaultProperties.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
        defaultProperties.put("schema.registry.url", schema_registry_url);

        // Example specific configuration
        //
        todo_commands_topic = System.getenv("todo_commands_topic");
        System.out.println("todo_commands_topic: " + todo_commands_topic);
        todo_updates_topic = System.getenv("todo_updates_topic");
        System.out.println("todo_updates_topic: " + todo_updates_topic);
    }

    public static void startKafkaStreams() {
        // Avro serde configs
        //
        final Map<String, String> serdeConfig =
                Collections.singletonMap(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                        schema_registry_url);

        // Process the stream
        //
        final StreamsBuilder builder = new StreamsBuilder();

        // Launch the single update service
        //
        startWsUpdatesService(builder, serdeConfig);

        // Create the command producer for the ws endpoint to use to deliver commands
        //
        commandProducer = new KafkaProducer(defaultProperties);

        // Boot up the streams
        //
        streams = new KafkaStreams(builder.build(), defaultProperties);
        System.out.println("Starting streams...");
        streams.start();
    }

    public static void startWsUpdatesService(StreamsBuilder builder, Map<String, String> serdeConfig) {

    }

    public static void main(String[] args) throws Exception {

        Server wsServer = startWsServer();

        // Configure Kafka
        //
        configureKafka();

        // Create the required topics
        //
        System.out.println("Creating topics that might not exist");
        AdminClient admin = AdminClient.create(defaultProperties);
        CreateTopicsResult result = admin.createTopics(Arrays.asList(
            new NewTopic(todo_commands_topic, 12, (short)1),
            new NewTopic(todo_updates_topic, 12, (short)1)
        ));
        try {
            result.all().get(60, TimeUnit.SECONDS);
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                System.out.println(e.getMessage());
            }
            else {
                throw e;
            }
        }


        startKafkaStreams();

        // Wait for Websocket server to terminate
        //
        wsServer.join();
    }

}
