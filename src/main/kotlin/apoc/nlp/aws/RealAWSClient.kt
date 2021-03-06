package apoc.nlp.aws

import apoc.ai.service.AWSClient
import apoc.result.MapResult
import apoc.util.JsonUtil
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.comprehend.AmazonComprehendClientBuilder
import com.amazonaws.services.comprehend.model.BatchDetectEntitiesRequest
import com.amazonaws.services.comprehend.model.BatchDetectEntitiesResult
import com.amazonaws.services.comprehend.model.BatchDetectKeyPhrasesRequest
import com.amazonaws.services.comprehend.model.BatchDetectSentimentRequest
import org.neo4j.graphdb.Node
import org.neo4j.logging.Log

class RealAWSClient(config: Map<String, Any>, private val log: Log) : AWSClient {
    private val apiKey = config["key"].toString()
    private val apiSecret = config["secret"].toString()
    private val region = config.getOrDefault("region", "us-east-1").toString()
    private val language = config.getOrDefault("language", "en").toString()
    private val nodeProperty = config.getOrDefault("nodeProperty", "text").toString()

    private val awsClient = AmazonComprehendClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(BasicAWSCredentials(apiKey, apiSecret)))
            .withRegion(region)
            .build()

     override fun entities(data: Any): BatchDetectEntitiesResult? {
         val convertedData = convertInput(data)
         val batch = BatchDetectEntitiesRequest().withTextList(convertedData).withLanguageCode(language)
         return awsClient.batchDetectEntities(batch)
    }

     fun sentiment(data: Any, config: Map<String, Any?>): List<MapResult> {
        val convertedData = convertInput(data)
        var batch = BatchDetectSentimentRequest().withTextList(convertedData)
        var batchDetectEntities = awsClient.batchDetectSentiment(batch)

        val allData = batchDetectEntities.resultList
        val batchToRetry = batchDetectEntities.errorList.map { convertedData[it.index] }
        batch = BatchDetectSentimentRequest().withTextList(batchToRetry)
        batchDetectEntities = awsClient.batchDetectSentiment(batch)
        allData += batchDetectEntities.resultList

        return allData.map { MapResult(JsonUtil.OBJECT_MAPPER!!.convertValue(it, Map::class.java) as Map<String, Any?>) }
    }

     fun keyPhrases(data: Any, config: Map<String, Any?>): List<MapResult> {
        val convertedData = convertInput(data)
        var batch = BatchDetectKeyPhrasesRequest().withTextList(convertedData)
        var batchDetectEntities = awsClient.batchDetectKeyPhrases(batch)

        val allData = batchDetectEntities.resultList
        val batchToRetry = batchDetectEntities.errorList.map { convertedData[it.index] }
        batch = BatchDetectKeyPhrasesRequest().withTextList(batchToRetry)
        batchDetectEntities = awsClient.batchDetectKeyPhrases(batch)
        allData += batchDetectEntities.resultList

        return allData.map { MapResult(JsonUtil.OBJECT_MAPPER!!.convertValue(it, Map::class.java) as Map<String, Any?>) }
    }

     fun vision(data: Any, config: Map<String, Any?>): List<MapResult> {
        throw UnsupportedOperationException("Rekognition is not yet implemented")
    }

    private fun convertInput(data: Any): List<String> {
        return when (data) {
            is Node -> listOf(data.getProperty(nodeProperty).toString())
            is List<*> -> data.map { node -> (node as Node).getProperty(nodeProperty).toString() }
            else -> throw java.lang.RuntimeException("Class ${data::class.java.name} not supported")
        }
    }
}