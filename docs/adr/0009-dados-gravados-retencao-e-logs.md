# ADR 0009 — Dados gravados, retenção e logs

- **Status:** proposta
- **Data:** 2026-10-14

## Contexto

O enunciado pede "configurações de segurança relacionadas aos dados de clientes". Criptografia e IAM são da camada de
cloud; o que a aplicação decide é **quanto dado existe para proteger**. A Description é texto livre do Student e pode
conter nome, e-mail ou outra informação pessoal.

## Decisão

### O que a Evaluation grava

| Campo | Origem |
|---|---|
| `id` | UUID gerado pela `receive-evaluation` |
| `description` | Student |
| `score` | Student |
| `urgency` | Derivada do Score ([ADR 0001](0001-urgencia-derivada-da-nota.md)) |
| `submissionTime` | Instante da recepção, em UTC |
| `urgencyNoticeSentAt` | `send-urgency-notice` ([ADR 0005](0005-entrega-pelo-menos-uma-vez.md)), só em Critical Evaluation |

**Nada que identifique o Student**: nem IP, nem user agent.

### Retenção

**12 semanas**, com expiração automática pelo próprio banco. O Weekly Report só lê a semana anterior; a margem permite
rodar de novo um trimestre de relatórios.

### O que nunca vai para o log

- **A Description**, nem em erro de validação. O log registra `id`, `score`, `urgency` e o **tamanho** da Description.
- **O corpo de uma requisição recusada**: registra o campo inválido e o motivo, não o valor.
- **Endereços de e-mail dos Administrators**: registra "enviado para N destinatários".

## Alternativas consideradas

- **Retenção indefinida**: acumula dado pessoal em texto livre sem uso.
- **Expirar logo depois da semana relatada**: não sobra margem nem para rodar de novo o relatório na terça.

## Consequências

- Sem IP gravado, investigar uma enxurrada ([ADR 0006](0006-endpoint-anonimo.md)) depende dos logs de acesso do gateway,
  que são da infraestrutura e têm retenção curta.
- Um relatório de mais de 12 semanas atrás não pode ser gerado de novo.
