package br.com.fiap.feedbackplatform.sendweeklyreport.config;

import java.net.URI;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Configuração de infraestrutura e parâmetros da função send-weekly-report, conforme ADRs 0004, 0010, 0011, 0014, 0020 e 0023.
 */
@Configuration
public class WeeklyReportConfig {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final List<String> administrators;
    private final String senderEmail;
    private final String formattedSender;
    private final String tableName;
    private final String region;
    private final String dynamoDbEndpoint;
    private final String sesEndpoint;

    /**
     * Construtor com injeção de propriedades e validação na inicialização conforme ADRs 0020 e 0023.
     *
     * @param administratorsRaw string com os e-mails dos administradores separados por vírgula
     * @param tableName nome da tabela DynamoDB
     * @param region região AWS
     * @param dynamoDbEndpoint endpoint customizado do DynamoDB (opcional)
     * @param sesEndpoint endpoint customizado do SES (opcional)
     */
    public WeeklyReportConfig(
            @Value("${ADMINISTRATORS:${administrators:}}") String administratorsRaw,
            @Value("${evaluations.table:${EVALUATIONS_TABLE:evaluations}}") String tableName,
            @Value("${aws.region:${AWS_REGION:sa-east-1}}") String region,
            @Value("${evaluations.dynamodb.endpoint-override:${DYNAMODB_ENDPOINT:}}") String dynamoDbEndpoint,
            @Value("${evaluations.ses.endpoint-override:${SES_ENDPOINT:}}") String sesEndpoint) {

        this.administrators = validateAndParseAdministrators(administratorsRaw);
        this.senderEmail = this.administrators.get(0);
        this.formattedSender = "Feedback Platform <" + this.senderEmail + ">";
        this.tableName = tableName;
        this.region = region;
        this.dynamoDbEndpoint = dynamoDbEndpoint;
        this.sesEndpoint = sesEndpoint;
    }

    private static List<String> validateAndParseAdministrators(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Variável ADMINISTRATORS ausente ou vazia. A função deve falhar ao subir conforme ADR 0020.");
        }
        List<String> list = Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();

        if (list.isEmpty()) {
            throw new IllegalStateException("Nenhum administrador informado em ADMINISTRATORS. A função deve falhar ao subir conforme ADR 0020.");
        }

        for (String email : list) {
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalStateException("E-mail inválido na lista ADMINISTRATORS: '" + email + "'. A função deve falhar ao subir conforme ADR 0020.");
            }
        }
        return List.copyOf(list);
    }

    /**
     * Retorna a lista imutável de e-mails dos administradores, conforme ADR 0020.
     *
     * @return lista de e-mails dos administradores
     */
    public List<String> administrators() {
        return administrators;
    }

    /**
     * Retorna o e-mail do remetente, que é o primeiro endereço da lista, conforme ADR 0023.
     *
     * @return e-mail do remetente
     */
    public String senderEmail() {
        return senderEmail;
    }

    /**
     * Retorna o cabeçalho From formatado como 'Feedback Platform &lt;email&gt;', conforme ADR 0023.
     *
     * @return remetente formatado
     */
    public String formattedSender() {
        return formattedSender;
    }

    /**
     * Retorna o nome da tabela do DynamoDB, conforme ADR 0010.
     *
     * @return nome da tabela
     */
    public String tableName() {
        return tableName;
    }

    /**
     * Produz o relógio da aplicação para resolução temporal determinística nos testes.
     *
     * @return relógio UTC
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Produz o cliente DynamoDB SDK v2 configurado com transporte UrlConnectionHttpClient, conforme ADRs 0004 e 0010.
     *
     * @return cliente DynamoDbClient
     */
    @Bean
    @ConditionalOnMissingBean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder());

        if (dynamoDbEndpoint != null && !dynamoDbEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(dynamoDbEndpoint));
        }
        return builder.build();
    }

    /**
     * Produz o cliente Amazon SES SDK v2 configurado com transporte UrlConnectionHttpClient, conforme ADRs 0004 e 0014.
     *
     * @return cliente SesClient
     */
    @Bean
    @ConditionalOnMissingBean
    public SesClient sesClient() {
        var builder = SesClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder());

        if (sesEndpoint != null && !sesEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(sesEndpoint));
        }
        return builder.build();
    }
}
