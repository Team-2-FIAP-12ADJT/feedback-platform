# ADR 0018 — Criptografia em repouso com as chaves padrão da AWS

- **Status:** aceita
- **Data:** 2026-10-17

## Contexto

O enunciado avalia a segurança dos dados. A Description pode conter dado pessoal do Student
([ADR 0009](0009-dados-gravados-retencao-e-logs.md)). Os serviços da plataforma já gravam criptografado por padrão; a
decisão é de quem é a chave:

| Opção | Quem controla a chave | Uso registrado no CloudTrail | Custo |
|---|---|---|---|
| Chave padrão, da própria AWS | A AWS, invisível na conta | Não | Nenhum |
| Chave gerenciada pela AWS na conta (`aws/dynamodb`, `aws/sqs`) | A AWS, visível na conta | Sim | Só as chamadas ao KMS |
| Chave própria no KMS | O grupo, por uma política da chave | Sim | US$ 1 por mês por chave, mais as chamadas |

## Decisão

**Criptografia em repouso com a chave padrão de cada serviço, sem chave própria no KMS.**

| Recurso | Em repouso |
|---|---|
| Tabela DynamoDB e seu Streams ([ADR 0010](0010-dynamodb-particionado-por-dia.md)) | Chave padrão da AWS |
| Filas SQS de falha ([ADR 0017](0017-tentativas-e-filas-de-falha.md)) | SSE-SQS, padrão das filas novas |
| Logs do CloudWatch | Criptografia padrão do serviço |
| Pacotes do deploy no S3 ([ADR 0013](0013-pipeline-deploy-automatico-oidc.md)) | SSE-S3, padrão dos buckets |
| Tópico SNS do Operational Alert ([ADR 0014](0014-ses-para-funcoes-sns-para-alerta.md)) | Sem criptografia de servidor: leva só a frase do alerta, e cifrá-lo exigiria chave própria liberada para o CloudWatch e o EventBridge |

- **Em trânsito, tudo por TLS:** o gateway só atende HTTPS, e as chamadas às APIs da AWS são HTTPS.
- **Quem lê o dado é decidido pelo IAM**, não pela chave: cada função tem a sua role, com acesso só ao que usa.

## Alternativas consideradas

- **Chave própria no KMS só na tabela**: controle explícito de quem decifra e cada uso registrado no CloudTrail, por cerca
  de US$ 1 por mês — controle que, neste projeto, duplicaria o que as roles das funções já restringem.
- **Chaves gerenciadas pela AWS na conta**: uso auditável sem custo fixo, mas a política da chave não é editável e não
  muda quem pode ler.

## Consequências

- Não há registro de cada uso da chave: a auditoria de acesso ao dado fica no CloudTrail das chamadas ao DynamoDB.
- Revogar o acesso ao dado é mudar a role de uma função, não a política de uma chave.
- Passar a chave própria depois exige recriar a tabela ou migrar os dados — com retenção de 12 semanas, a troca perde no
  máximo o que ainda não expirou, se não houver migração.
