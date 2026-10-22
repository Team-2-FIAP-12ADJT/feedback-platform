package br.com.fiap.feedbackplatform.sendweeklyreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicação Spring Boot da função Lambda send-weekly-report, conforme ADRs 0003 e 0004.
 */
@SpringBootApplication
public class SendWeeklyReportApplication {

    /**
     * Ponto de entrada padrão Spring Boot.
     *
     * @param args argumentos da linha de comando
     */
    public static void main(String[] args) {
        SpringApplication.run(SendWeeklyReportApplication.class, args);
    }
}
