package facesearch.ws.service

import org.apache.kafka.streams.kstream.KStream
import polaris.kafka.CommandUpdateWebsocket
import polaris.kafka.PolarisKafka
import polaris.kafka.SafeTopic
import polaris.kafka.websocket.Command
import polaris.kafka.websocket.Update

fun KStream<K, V>.toPolarisStream(topic : SafeTopic<K, V>) {
    return this.to(topic.topic, topic.producedWith())
}

fun main(args : Array<String>) {

    with(PolarisKafka("facesearch-ws")) {
        val commands = topic<String, Command>(
            "facesearch-commands",
            12,
            1)
        val updates = topic<String, Update>(
            "facesearch-updates",
            12,
            1)

        val commandUpdateWebsocket = CommandUpdateWebsocket(
            8080,
            "/ws/updates",
            commands,
            consumeStream(updates))

        consumeStream(commands)
            .mapValues { command ->

                val update = Update()
                update.setType("TEST")
                update.setAction("STUFF")
                update.setData("{}")

                update
            }
            .toPolarisStream(updates)

        start()
        commandUpdateWebsocket.join()
    }
}