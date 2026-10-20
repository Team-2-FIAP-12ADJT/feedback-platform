package br.com.fiap.feedbackplatform.receiveevaluation;

import java.net.URI;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Sobe DynamoDB Local e cria a tabela particionada conforme ADR 0010. */
public class DynamoDbResource implements QuarkusTestResourceLifecycleManager {
    private static final String TABLE = "evaluations-test";
    private GenericContainer<?> container;

    /** Inicia o container e cria a tabela de teste. */
    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:3.3.2"))
                .withExposedPorts(8000)
                .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb");
        container.start();
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
        System.setProperty("aws.region", "us-east-1");
        String endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(8000);
        try (DynamoDbClient client = DynamoDbClient.builder().endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1).httpClientBuilder(UrlConnectionHttpClient.builder()).build()) {
            client.createTable(CreateTableRequest.builder().tableName(TABLE)
                    .keySchema(KeySchemaElement.builder().attributeName("submissionDay").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("submissionKey").keyType(KeyType.RANGE).build())
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("submissionDay").attributeType("S").build(),
                            AttributeDefinition.builder().attributeName("submissionKey").attributeType("S").build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                    .build());
        }
        return Map.of("evaluations.dynamodb.endpoint-override", endpoint,
                "evaluations.table", TABLE, "AWS_REGION", "us-east-1");
    }

    /** Para e remove o container. */
    @Override
    public void stop() { if (container != null) container.stop(); }
}
