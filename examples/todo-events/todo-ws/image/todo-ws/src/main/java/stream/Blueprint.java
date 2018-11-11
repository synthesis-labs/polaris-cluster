package stream;

import com.google.gson.Gson;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import protocol.list.*;
import ws.WsEndpoint;
import ws.WsUpdate;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class Blueprint {

    private static Properties defaultProperties = new Properties();
    private static String kafka_bootstrap_servers;
    private static String schema_registry_url;

    private static String todo_list_commands;
    private static String todo_lists;

    private static String todo_item_commands;
    private static String todo_items;

    public static KafkaStreams streams;
    public static KafkaProducer commandProducer;

    public static void configure() {
        String kafka_application_id = "todo-ws";

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
                LogAndContinueExceptionHandler.class);

        // This is for producers really only
        //
        defaultProperties.put("key.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
        defaultProperties.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");
        defaultProperties.put("schema.registry.url", schema_registry_url);

        // Example specific configuration
        //
        todo_list_commands = System.getenv("todo_list_commands");
        System.out.println("todo_list_commands: " + todo_list_commands);
        todo_lists = System.getenv("todo_lists");
        System.out.println("todo_lists: " + todo_lists);

        todo_item_commands = System.getenv("todo_item_commands");
        System.out.println("todo_item_commands: " + todo_item_commands);
        todo_items = System.getenv("todo_items");
        System.out.println("todo_items: " + todo_items);
    }

    public static void init() {
        configure();
        commandProducer = new KafkaProducer(defaultProperties);

        // Start up the streams
        //
        startStreams();
    }

    public static void startStreams() {
        // Avro serde configs
        //
        final Map<String, String> serdeConfig =
                Collections.singletonMap(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                        schema_registry_url);

        // Process the stream
        //
        final StreamsBuilder builder = new StreamsBuilder();

        // Build individual streams - Yes these could be hosted in seperate microservices
        //
        startTodoListsService(builder, serdeConfig);
        startTodoItemsService(builder, serdeConfig);
        startWsUpdatesService(builder, serdeConfig);

        //
        // More...
        //

        // Boot up the streams
        //
        streams = new KafkaStreams(builder.build(), defaultProperties);
        System.out.println("Starting streams...");
        streams.start();
    }

    public static void startTodoListsService(StreamsBuilder builder, Map<String, String> serdeConfig) {

        final SpecificAvroSerde<ListCommand> listCommandSerde = new SpecificAvroSerde<>();
        listCommandSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<List> listSerde = new SpecificAvroSerde<>();
        listSerde.configure(serdeConfig, false);

        final KStream<String, ListCommand> listCommands =
                builder.stream(todo_list_commands, Consumed.with(Serdes.serdeFrom(String.class), listCommandSerde));

        listCommands
            .map((String k, ListCommand v) -> {
                List listUpdate = new List();
                switch (v.getAction()) {
                    case CREATE:
                        listUpdate.setName(v.getName());
                        listUpdate.setStatus(ListStatus.ACTIVE);
                        break;
                    case DELETE:
                        listUpdate.setName(v.getName());
                        listUpdate.setStatus(ListStatus.DELETED);
                        break;
                    case UPDATE:
                        // Nothing to update yet?
                        break;
                    case MARK_COMPLETED:
                        // Nothing to update yet?
                        break;
                    case MARK_UNCOMPLETED:
                        // Nothing to update yet?
                        break;
                }
                return KeyValue.pair(k, listUpdate);
            })
            .to(todo_lists, Produced.with(Serdes.serdeFrom(String.class), listSerde));
    }

    public static void startTodoItemsService(StreamsBuilder builder, Map<String, String> serdeConfig) {

        final SpecificAvroSerde<ItemCommand> itemCommandSerde = new SpecificAvroSerde<>();
        itemCommandSerde.configure(serdeConfig, false);

        final SpecificAvroSerde<Item> itemSerde = new SpecificAvroSerde<>();
        itemSerde.configure(serdeConfig, false);

        final KStream<String, ItemCommand> itemCommands =
                builder.stream(todo_item_commands,
                        Consumed.with(Serdes.serdeFrom(String.class), itemCommandSerde));

        itemCommands
                .map((String k, ItemCommand v) -> {
                    Item itemUpdate = new Item();
                    switch (v.getAction()) {
                        case CREATE:

                            // Should probably check that the list exists?

                            itemUpdate.setName(v.getName());
                            itemUpdate.setList(v.getList());
                            itemUpdate.setStatus(ItemStatus.ACTIVE);
                            break;
                        case DELETE:
                            itemUpdate.setName(v.getName());
                            itemUpdate.setList(v.getList());
                            itemUpdate.setStatus(ItemStatus.DELETED);
                            break;
                        case UPDATE:
                            // Nothing to update yet?
                            break;
                        case MARK_COMPLETED:
                            // Nothing to update yet?
                            break;
                        case MARK_UNCOMPLETED:
                            // Nothing to update yet?
                            break;
                    }
                    return KeyValue.pair(k, itemUpdate);
                })
                .to(todo_items, Produced.with(Serdes.serdeFrom(String.class), itemSerde));
    }


    public static void startWsUpdatesService(StreamsBuilder builder, Map<String, String> serdeConfig) {

        Serde<String> stringSerde = Serdes.serdeFrom(String.class);

        // For lists
        //
        final SpecificAvroSerde<List> listSerde = new SpecificAvroSerde<>();
        listSerde.configure(serdeConfig, false);
        final KStream<String, List> lists =
                builder.stream(todo_lists, Consumed.with(stringSerde, listSerde));

        // For items
        //
        final SpecificAvroSerde<Item> itemSerde = new SpecificAvroSerde<>();
        itemSerde.configure(serdeConfig, false);
        final KStream<String, Item> items =
                builder.stream(todo_items, Consumed.with(stringSerde, itemSerde));

        // Lists - Emit updates to Websocket (this should be in the todo-ws service)
        //
        lists.foreach((String k, List list) -> {
            Gson gson = new Gson();
            WsUpdate update = new WsUpdate();

            update.type = "LIST";
            update.action = list.getStatus().toString();
            update.data = gson.toJsonTree(list);

            String json = gson.toJson(update);

            System.out.println("Emitting WS update");
            WsEndpoint.broadcast(json);
        });

        // Items - Emit updates to Websocket (this should be in the todo-ws service)
        //
        items.foreach((String k, Item item) -> {
            Gson gson = new Gson();
            WsUpdate update = new WsUpdate();

            update.type = "ITEM";
            update.action = item.getStatus().toString();
            update.data = gson.toJsonTree(item);

            String json = gson.toJson(update);

            System.out.println("Emitting WS update");
            WsEndpoint.broadcast(json);
        });

        // Lists - ktable
        //
        final KTable<String, List> listTable =
            lists
                .map((String k, List list) -> {
                    System.out.println("KTabling " + list.getName() + " " + list.getStatus().toString());
                    return KeyValue.pair(list.getName(), list);
                })
                .groupByKey(Serialized.with(stringSerde, listSerde))
                .reduce((v1, v2) -> v2, Materialized.as("listTableKeyStore"));

        // Items - ktable
        //
        final KTable<String, Item> itemTable =
                items
                        .map((String k, Item item) -> {
                            System.out.println("KTabling " + item.getName() + " " + item.getStatus().toString());
                            return KeyValue.pair(item.getList() + "-" + item.getName(), item);
                        })
                        .groupByKey(Serialized.with(stringSerde, itemSerde))
                        .reduce((v1, v2) -> v2, Materialized.as("itemTableKeyStore"));
    }

    public static void queryListTable(String forWebsocket) {
        ReadOnlyKeyValueStore<String, List> store = streams.store("listTableKeyStore", QueryableStoreTypes.keyValueStore());

        KeyValueIterator<String, List> values = store.all();

        Gson gson = new Gson();
        java.util.List<WsUpdate> updates = new java.util.LinkedList<WsUpdate>();

        while (values.hasNext()) {
            KeyValue<String, List> kv = values.next();

            // Don't send update of deletes entries
            //
            if (kv.value.getStatus() != ListStatus.ACTIVE)
                continue;

            WsUpdate update = new WsUpdate();

            update.type = "LIST";
            update.action = kv.value.getStatus().toString();
            update.data = gson.toJsonTree(kv.value);

            updates.add(update);
        }

        System.out.println("Approximately " + store.approximateNumEntries() + " total lists, and " + updates.size() + " in update");

        String json = gson.toJson(updates);
        System.out.println("Emitting WS update");
        WsEndpoint.send(json, forWebsocket);
    }

    public static void queryItemTable(String forWebsocket) {
        ReadOnlyKeyValueStore<String, Item> store = streams.store("itemTableKeyStore", QueryableStoreTypes.keyValueStore());
        System.out.println("Approximately " + store.approximateNumEntries() + " total items");

        KeyValueIterator<String, Item> values = store.all();

        Gson gson = new Gson();
        java.util.List<WsUpdate> updates = new java.util.LinkedList<WsUpdate>();

        while (values.hasNext()) {
            KeyValue<String, Item> kv = values.next();

            // Don't send update of deletes entries
            //
            if (kv.value.getStatus() != ItemStatus.ACTIVE)
                continue;

            WsUpdate update = new WsUpdate();

            update.type = "ITEM";
            update.action = kv.value.getStatus().toString();
            update.data = gson.toJsonTree(kv.value);

            updates.add(update);
        }

        System.out.println("Approximately " + store.approximateNumEntries() + " total items, and " + updates.size() + " in update");

        String json = gson.toJson(updates);
        System.out.println("Emitting WS update");
        WsEndpoint.send(json, forWebsocket);
    }

}
