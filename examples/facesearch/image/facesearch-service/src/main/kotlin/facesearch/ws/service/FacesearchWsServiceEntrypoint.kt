package facesearch.ws.service

import com.google.gson.Gson
import org.apache.kafka.streams.kstream.KStream
import polaris.kafka.CommandUpdateWebsocket
import polaris.kafka.PolarisKafka
import polaris.kafka.SafeTopic
import polaris.kafka.websocket.Command
import polaris.kafka.websocket.Update
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder
import com.amazonaws.services.rekognition.AmazonRekognition
import com.amazonaws.services.rekognition.model.*
import facesearch.schema.*
import facesearch.schema.BoundingBox
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import java.util.*
import java.nio.ByteBuffer


fun main(args : Array<String>) {

    with(PolarisKafka("facesearch-searchFacesByImage")) {
        val commands = topic<String, Command>(
            "facesearch-commands",
            12,
            1)
        val updates = topic<String, Update>(
            "facesearch-updates",
            12,
            1)

        // Face Processing Stream
        //
        val searchFacesByImage =
            consumeCommandStreamMatching(commands, "FACE", "INFER", FaceInferCommand::class.java)
                .flatMapValues { _, faceInferCmd ->
                    // Rekognition
                    //
                    val rekog = AmazonRekognitionClientBuilder.defaultClient()

                    val rawBytes = Base64.getDecoder().decode(faceInferCmd.getImage())

                    try {
                        val rekogResult = rekog.searchFacesByImage(
                            SearchFacesByImageRequest()
                                .withCollectionId("dronecon")
                                .withMaxFaces(10)
                                .withFaceMatchThreshold(70.toFloat())
                                .withImage(
                                    Image().withBytes(
                                        ByteBuffer.wrap(rawBytes)
                                    )
                                )
                        )

                        listOf(
                            SearchedFacesByImage(
                                facesearch.schema.BoundingBox(
                                    rekogResult.searchedFaceBoundingBox.width,
                                    rekogResult.searchedFaceBoundingBox.height,
                                    rekogResult.searchedFaceBoundingBox.left,
                                    rekogResult.searchedFaceBoundingBox.top
                                ),
                                rekogResult.searchedFaceConfidence,
                                rekogResult.faceMatches.map { face ->
                                    println("Found matching external id ${face.face.externalImageId}")
                                    facesearch.schema.FaceMatch(
                                        face.similarity,
                                        facesearch.schema.Face(
                                            face.face.faceId,
                                            facesearch.schema.BoundingBox(
                                                face.face.boundingBox.width,
                                                face.face.boundingBox.height,
                                                face.face.boundingBox.left,
                                                face.face.boundingBox.top
                                            ),
                                            face.face.imageId,
                                            face.face.externalImageId,
                                            face.face.confidence
                                        )
                                    )
                                }
                            )
                        )
                    }
                    catch (e : Exception) {
                        // Ignore bad responses
                        //
                        emptyList<SearchedFacesByImage>()
                    }

                }

                .mapValues { result ->
                    val gson = Gson()
                    Update("FACE", "MATCHED_FACE", gson.toJson(result))
                }

                .to(updates.topic, updates.producedWith())

        start()
    }

    with(PolarisKafka("facesearch-detectLabels")) {
        val commands = topic<String, Command>(
            "facesearch-commands",
            12,
            1)
        val updates = topic<String, Update>(
            "facesearch-updates",
            12,
            1)

        val detectedLabels = topic<String, DetectedLabels>(
            "facesearch-detectedlabels",
            12,
            1)

        // Object processing
        //
        val detectLabels = consumeCommandStreamMatching(commands, "FACE", "INFER", FaceInferCommand::class.java)
            .flatMapValues { _, faceInferCmd ->
                // Rekognition
                //
                val rekog = AmazonRekognitionClientBuilder.defaultClient()

                val rawBytes = Base64.getDecoder().decode(faceInferCmd.getImage())

                try {
                    val rekogResult = rekog.detectLabels(
                        DetectLabelsRequest()
                            .withMaxLabels(10)
                            .withMinConfidence(70.toFloat())
                            .withImage(
                                Image().withBytes(
                                    ByteBuffer.wrap(rawBytes)
                                )
                            )
                    )

                    val l = listOf(DetectedLabels(
                        rekogResult.orientationCorrection,
                        rekogResult.labels.map { label ->
                            println("Found label ${label.name}")
                            facesearch.schema.Label(label.name, label.confidence)
                        }))
                    l
                }
                catch (e : Exception) {
                    // Ignore bad responses - not sure what is best to do?
                    //
                    println("Exception during detectLabels $e")
                    listOf<DetectedLabels>()
                }
            }

            .through(detectedLabels.topic, detectedLabels.producedWith())

            .mapValues { result ->
                val gson = Gson()
                Update("FACE", "FOUND_LABEL", gson.toJson(result))
            }

            .to(updates.topic, updates.producedWith())

        start()
    }

    with(PolarisKafka("facesearch-detectFaces")) {
        val commands = topic<String, Command>(
            "facesearch-commands",
            12,
            1)
        val updates = topic<String, Update>(
            "facesearch-updates",
            12,
            1)

        // Face Processing Stream
        //
        val searchFacesByImage =
            consumeCommandStreamMatching(commands, "FACE", "INFER", FaceInferCommand::class.java)
                .flatMapValues { _, faceInferCmd ->
                    // Rekognition
                    //
                    val rekog = AmazonRekognitionClientBuilder.defaultClient()

                    val rawBytes = Base64.getDecoder().decode(faceInferCmd.getImage())

                    try {
                        val rekogResult = rekog.detectFaces(
                            DetectFacesRequest()
                                .withAttributes(Attribute.ALL)
                                .withImage(
                                    Image().withBytes(
                                        ByteBuffer.wrap(rawBytes)
                                    )
                                )
                        )

                        listOf(
                            DetectedFaces(
                                rekogResult.orientationCorrection,
                                rekogResult.faceDetails.map { face ->
                                    facesearch.schema.FaceDetail(
                                        facesearch.schema.BoundingBox(
                                            face.boundingBox.width,
                                            face.boundingBox.height,
                                            face.boundingBox.left,
                                            face.boundingBox.top
                                        ),
                                        facesearch.schema.AgeRange(
                                            face.ageRange.low,
                                            face.ageRange.high
                                        ),
                                        facesearch.schema.Smile(
                                            face.smile.value,
                                            face.smile.confidence
                                        ),
                                        facesearch.schema.Eyeglasses(
                                            face.eyeglasses.value,
                                            face.eyeglasses.confidence
                                        ),
                                        facesearch.schema.Sunglasses(
                                            face.sunglasses.value,
                                            face.sunglasses.confidence
                                        ),
                                        facesearch.schema.Gender(
                                            face.gender.value,
                                            face.gender.confidence
                                        ),
                                        facesearch.schema.Beard(
                                            face.beard.value,
                                            face.beard.confidence
                                        ),
                                        facesearch.schema.Mustache(
                                            face.mustache.value,
                                            face.mustache.confidence
                                        ),
                                        facesearch.schema.EyeOpen(
                                            face.eyesOpen.value,
                                            face.eyesOpen.confidence
                                        ),
                                        facesearch.schema.MouthOpen(
                                            face.mouthOpen.value,
                                            face.mouthOpen.confidence
                                        ),
                                        face.emotions.map { emotion ->
                                            facesearch.schema.Emotion(
                                                emotion.type,
                                                emotion.confidence
                                            )
                                        },
                                        face.landmarks.map { landmark ->
                                            facesearch.schema.Landmark(
                                                landmark.type,
                                                landmark.x,
                                                landmark.y
                                            )
                                        },
                                        facesearch.schema.Pose(
                                            face.pose.roll,
                                            face.pose.yaw,
                                            face.pose.pitch
                                        ),
                                        facesearch.schema.ImageQuality(
                                            face.quality.brightness,
                                            face.quality.sharpness
                                        ),
                                        face.confidence
                                    )
                                }
                            )
                        )
                    }
                    catch (e : Exception) {
                        // Ignore bad responses
                        //
                        emptyList<facesearch.schema.DetectedFaces>()
                    }
                }

                .mapValues { result ->
                    val gson = Gson()
                    Update("FACE", "FOUND_FACE", gson.toJson(result))
                }

                .to(updates.topic, updates.producedWith())

        start()
    }

    with(PolarisKafka("facesearch-detectText")) {
        val commands = topic<String, Command>(
            "facesearch-commands",
            12,
            1)
        val updates = topic<String, Update>(
            "facesearch-updates",
            12,
            1)

        // Face Processing Stream
        //
        val searchFacesByImage =
            consumeCommandStreamMatching(commands, "FACE", "INFER", FaceInferCommand::class.java)
                .flatMapValues { _, faceInferCmd ->
                    // Rekognition
                    //
                    val rekog = AmazonRekognitionClientBuilder.defaultClient()

                    val rawBytes = Base64.getDecoder().decode(faceInferCmd.getImage())

                    try {
                        val rekogResult = rekog.detectText(
                            DetectTextRequest()
                                .withImage(
                                    Image().withBytes(
                                        ByteBuffer.wrap(rawBytes)
                                    )
                                )
                        )

                        listOf(
                            DetectedText(
                                rekogResult.textDetections.map { textDetection ->
                                    facesearch.schema.TextDetection(
                                        textDetection.detectedText,
                                        textDetection.type,
                                        textDetection.id,
                                        textDetection.parentId,
                                        textDetection.confidence,
                                        facesearch.schema.Geometry(
                                            facesearch.schema.BoundingBox(
                                                textDetection.geometry.boundingBox.width,
                                                textDetection.geometry.boundingBox.height,
                                                textDetection.geometry.boundingBox.left,
                                                textDetection.geometry.boundingBox.top
                                            ),
                                            textDetection.geometry.polygon.map { point ->
                                                facesearch.schema.Point(
                                                    point.x,
                                                    point.y
                                                )
                                            }

                                        )
                                    )
                                }
                            )
                        )
                    }
                    catch (e : Exception) {
                        // Ignore bad responses
                        //
                        emptyList<facesearch.schema.DetectedFaces>()
                    }
                }

                .mapValues { result ->
                    val gson = Gson()
                    Update("FACE", "FOUND_TEXT", gson.toJson(result))
                }

                .to(updates.topic, updates.producedWith())

        start()
    }

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

        start()
        commandUpdateWebsocket.join()
    }


}