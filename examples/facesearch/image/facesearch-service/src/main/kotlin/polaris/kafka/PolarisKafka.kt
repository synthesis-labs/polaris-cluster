package polaris.kafka

import com.google.gson.Gson
import facesearch.schema.FaceInferCommand
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.LogAndFailExceptionHandler
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import polaris.kafka.websocket.Command
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

val KAFKA_BOOTSTRAP_SERVERS_ENVVAR = "kafka_bootstrap_servers"
val KAFKA_SCHEMA_REGISTRY_URL_ENVVAR = "schema_registry_url"

data class SafeTopic<K, V>(
        val topic : String,
        val keySerde : Serde<K>,
        val valueSerde : Serde<V>,
        val properties : Properties) {

    var producer : KafkaProducer<K, V>? = null

    fun consumedWith() : Consumed<K, V> {
        return Consumed.with(keySerde, valueSerde)
    }

    fun producedWith() : Produced<K, V> {
        return Produced.with(keySerde, valueSerde)
    }

    // Only call this is you want a kafka producer for this topic
    // - keeping in mind the constraint that you can only have a single
    // producer per topic per process
    //
    fun startProducer() {
        producer = KafkaProducer(properties)
    }
}

class PolarisKafka {
    val properties = Properties()
    val serdeConfig : Map<String, String>
    val adminClient : AdminClient
    val streamsBuilder : StreamsBuilder
    var streams : KafkaStreams? = null

    constructor(applicationId : String) {
        val kafka_bootstrap_servers = System.getenv(KAFKA_BOOTSTRAP_SERVERS_ENVVAR)
        val schema_registry_url = System.getenv(KAFKA_SCHEMA_REGISTRY_URL_ENVVAR)

        properties[StreamsConfig.APPLICATION_ID_CONFIG] = applicationId
        properties[StreamsConfig.CLIENT_ID_CONFIG] = "$applicationId-client"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafka_bootstrap_servers
        properties["schema.registry.url"] = schema_registry_url

        properties[StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG] = LogAndFailExceptionHandler::class.java

        properties["key.serializer"] = "io.confluent.kafka.serializers.KafkaAvroSerializer"
        properties["value.serializer"] = "io.confluent.kafka.serializers.KafkaAvroSerializer"

        serdeConfig = Collections.singletonMap(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                schema_registry_url)

        this.adminClient = AdminClient.create(properties)

        streamsBuilder = StreamsBuilder()
    }

    inline fun <reified K, V : SpecificRecord>topic(
            name : String,
            partitions : Int,
            replicas : Int) : SafeTopic<K, V> {
        // Ensure the topic exists
        //
        val result = adminClient.createTopics(Arrays.asList(
            NewTopic(name, partitions, replicas.toShort())
        ))
        try {
            result.all().get(60, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            if (e.cause is TopicExistsException) {
                println(e.message)
            } else {
                throw e
            }
        }

        // Configure the serdes
        //
        val keySerde = Serdes.serdeFrom(K::class.java)
        val valueSerde = SpecificAvroSerde<V>()

        keySerde.configure(serdeConfig, true)
        valueSerde.configure(serdeConfig, false)

        return SafeTopic(name, keySerde, valueSerde, properties)
    }

    fun <K, V>consumeStream(topic : SafeTopic<K, V>) : KStream<K, V> {
        return streamsBuilder.stream(topic.topic, topic.consumedWith())
    }

    fun <K, V : Command, D>consumeCommandStreamMatching(topic : SafeTopic<K, V>, type : String, cmd : String, classOfData : Class<D>) : KStream<K, D> {
        return streamsBuilder
            .stream(topic.topic, topic.consumedWith())
            .filter { _, command ->
                command.getType() == type && command.getCmd() == cmd
            }
            .mapValues { command ->
                // Parse the data to correct fields
                //
                val gson = Gson()
                val cmd = gson.fromJson(command.getData(), classOfData)

                cmd
            }
    }


    /*
    fun <K, V>produce(topic : SafeTopic<K, V>) : KStream<K, V> {
        return
    }
    */

    fun start() {
        println("Starting streams...")
        streams = KafkaStreams(streamsBuilder.build(), properties)
        streams?.start()
    }

    fun stop() {
        println("Stopping streams...")
        streams?.close()
    }
}

