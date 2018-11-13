package todo.item.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import protocol.todo.*;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;

public class TodoItemService {

    public static Properties defaultProperties = new Properties();
    public static String kafka_bootstrap_servers;
    public static String schema_registry_url;

    public static String todo_commands_topic;
    public static String todo_item_updates_topic;
    public static String todo_items_table;
    public static String todo_updates_topic;

    public static KafkaStreams streams;

    public static void startKafkaStreams() {
        String kafka_application_id = "todo-item-service";

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
        todo_item_updates_topic = System.getenv("todo_item_updates_topic");
        System.out.println("todo_item_updates_topic: " + todo_item_updates_topic);
        todo_items_table = System.getenv("todo_items_table");
        System.out.println("todo_items_table: " + todo_items_table);
        todo_updates_topic = System.getenv("todo_updates_topic");
        System.out.println("todo_updates_topic: " + todo_updates_topic);

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
        startCommandsService(builder, serdeConfig);

        // Boot up the streams
        //
        streams = new KafkaStreams(builder.build(), defaultProperties);
        System.out.println("Starting streams...");
        streams.start();
    }

    public static void startCommandsService(StreamsBuilder builder, Map<String, String> serdeConfig) {

        // Strings
        //
        Serde<String> stringSerde = Serdes.serdeFrom(String.class);

        // For general commands
        //
        final SpecificAvroSerde<TodoCommand> todoCommandSerde = new SpecificAvroSerde<>();
        todoCommandSerde.configure(serdeConfig, false);

        // For general updates
        //
        final SpecificAvroSerde<TodoUpdate> todoUpdateSerde = new SpecificAvroSerde<>();
        todoUpdateSerde.configure(serdeConfig, false);

        // For list updates
        //
        final SpecificAvroSerde<Item> itemSerde = new SpecificAvroSerde<>();
        itemSerde.configure(serdeConfig, false);

        final KStream<String, TodoCommand> commands =
                builder.stream(todo_commands_topic, Consumed.with(stringSerde, todoCommandSerde));

        final KStream<String, Item> itemUpdates =
            commands
                // Only interested in ITEM commands
                //
                .filter((String session, TodoCommand command) ->
                    command.getType().equals("ITEM"))
                // Ignore refresh commands for updates
                //
                .filter((String session, TodoCommand command) ->
                    !command.getCmd().equals("REFRESH"))
                // Map these to actual LIST commands (ie parse the data portion)
                //
                .flatMap((String session, TodoCommand command) -> {
                    // Use a flatmap because there might not be an actual update... could
                    // be badly formatted or something
                    //
                    LinkedList result = new LinkedList<KeyValue<String, Item>>();

                    // Here is the hard work of actually creating the List Update - and sticking the event on the
                    // topic
                    //
                    JsonParser dataParser = new JsonParser();
                    JsonObject data = dataParser.parse(command.getData()).getAsJsonObject();

                    // Need the list name - but could be null if naughty data
                    //
                    if (data.get("name") == null) {
                        return result;
                    }
                    if (data.get("list") == null) {
                        return result;
                    }

                    String itemName = data.get("name").getAsString();
                    String listName = data.get("list").getAsString();
                    Item itemUpdate = new Item();

                    switch (command.getCmd()) {
                        case "CREATE": {
                            itemUpdate.setName(itemName);
                            itemUpdate.setList(listName);
                            itemUpdate.setStatus(ItemStatus.ACTIVE);
                            break;
                        }
                        case "DELETE": {
                            itemUpdate.setName(itemName);
                            itemUpdate.setList(listName);
                            itemUpdate.setStatus(ItemStatus.DELETED);
                            break;
                        }
                    }

                    System.out.println("Put :Item on " + todo_item_updates_topic);

                    result.add(KeyValue.pair(session, itemUpdate));
                    return result;
                })
                // Pump to list update topic
                //
                .through(todo_item_updates_topic, Produced.with(stringSerde, itemSerde))
                ;

        final KStream<String, TodoUpdate> updates =
            itemUpdates
                // Then also deliver as a system level update to the todo-updates
                //
                .map((String session, Item itemUpdate) -> {
                    TodoUpdate update = new TodoUpdate();

                    update.setType("ITEM");
                    update.setAction(itemUpdate.getStatus().toString());

                    Gson gson = new Gson();
                    update.setData(gson.toJson(itemUpdate));

                    System.out.println("Put :Item on item-internal-updates");

                    return KeyValue.pair(session, update);
                })
                .through("item-internal-updates", Produced.with(stringSerde, todoUpdateSerde))
                ;

        // Items - ktable
        //
        final KTable<String, Item> itemTable =
            itemUpdates
                .map((String k, Item item) -> {
                    System.out.println("Tabling :Item " + item.getName() + " " + item.getList() + " " + item.getStatus().toString());
                    return KeyValue.pair(item.getName(), item);
                })
                .groupByKey(Serialized.with(stringSerde, itemSerde))
                .reduce((v1, v2) -> v2, Materialized.as(todo_items_table));

        final KStream<String, TodoUpdate> refreshes =
            commands
                // Only interested in ITEM commands
                //
                .filter((String session, TodoCommand command) ->
                        command.getType().equals("ITEM") && command.getCmd().equals("REFRESH"))
                .flatMap((String session, TodoCommand command) -> {
                    ReadOnlyKeyValueStore<String, Item> store = streams.store(todo_items_table, QueryableStoreTypes.keyValueStore());

                    KeyValueIterator<String, Item> values = store.all();

                    LinkedList refresh = new LinkedList<KeyValue<String, TodoUpdate>>();

                    while (values.hasNext()) {
                        KeyValue<String, Item> kv = values.next();

                        // Don't send update of deletes entries
                        //
                        if (kv.value.getStatus() != ItemStatus.ACTIVE)
                            continue;

                        TodoUpdate update = new TodoUpdate();

                        update.setType("ITEM");
                        update.setAction(kv.value.getStatus().toString());

                        Gson gson = new Gson();
                        update.setData(gson.toJson(kv.value));

                        refresh.add(KeyValue.pair(session, update));
                    }
                    System.out.println("Approximately " + store.approximateNumEntries() + " total items, and " + refresh.size() + " in update");
                    return refresh;
                })
                .through("item-internal-refreshes", Produced.with(stringSerde, todoUpdateSerde))
            ;

        refreshes
                .merge(updates)
                .through(todo_updates_topic, Produced.with(stringSerde, todoUpdateSerde))
        ;

    }

    public static void main(String[] args) throws Exception {
        startKafkaStreams();
    }

}
