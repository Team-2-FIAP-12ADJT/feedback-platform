# ADR 0004 — Java 25, framework escolhido pelo gatilho, monorepo Maven

- **Status:** aceita
- **Data:** 2026-10-14

## Contexto

O curso é Java, e o grupo quer usar Spring Boot e Quarkus conforme a função. Dois frameworks em três funções custam
dois builds, dois estilos de teste e dois jeitos de configurar, então a escolha precisa seguir um critério explicável.

A AWS Lambda oferece Java 25 como runtime gerenciado desde novembro de 2025, com suporte a SnapStart. Java 26, usado na
Fase 3, não existe como runtime gerenciado.

## Decisão

- **Java 25** em todos os módulos.
- **O framework é escolhido pelo gatilho:**
  - **Quarkus** na `receive-evaluation`: é a única função em que alguém espera a resposta, então o cold start aparece
    para o Student e no vídeo.
  - **Spring Boot** na `send-urgency-notice` e na `send-weekly-report`: rodam em segundo plano, e compartilham o módulo
    `email-support` (montagem do HTML, escape da Description, rótulos em português, versão em texto puro).
- **SnapStart** nas três funções.
- **Monorepo Maven multi-módulo:**

```
feedback-platform/                 ← pom agregador, sem parent de framework
├── domain/                        ← Java puro, sem nenhuma dependência
├── receive-evaluation/            ← Quarkus (importa o quarkus-bom)
├── email-support/                 ← Spring: montagem, escape e rótulos dos e-mails
├── send-urgency-notice/           ← Spring Boot (importa o spring-boot-dependencies)
└── send-weekly-report/            ← Spring Boot
```

## Alternativas consideradas

- **Um framework só**: mais simples, mas descarta o que o grupo pediu.
- **Framework escolhido pelo dono de cada função**: divide o trabalho, mas o critério não é técnico e não se defende na
  avaliação.
- **Handler puro, sem framework**: o menor cold start, mas escreve à mão o que o framework faria.

## Consequências

- O pom raiz **não pode** ter o `spring-boot-starter-parent` como parent; cada aplicação importa o próprio BOM.
- O `domain` não depende de nada, nem de Jackson. O próprio Maven garante isso, sem ArchUnit.
- SnapStart só vale em versão publicada ou alias, nunca no `$LATEST`: o deploy precisa publicar versão.
