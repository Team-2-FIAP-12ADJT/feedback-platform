# Pontos em aberto

O que ainda não foi decidido, ou foi decidido e ainda não feito. Cada item sai daqui quando vira ADR, documento ou
entrega. As decisões tomadas estão em [`docs/adr/`](adr/) e no [README](../README.md#decisões-tomadas).

Atualizado em 2026-10-14.

## 1. Bloqueante: conta de cloud

**Rodar em conta AWS própria do grupo ou no AWS Academy Learner Lab da FIAP?** Aguardando confirmação com a FIAP se
a entrega exige os créditos dela.

O que muda entre as duas:

| | Conta própria | Learner Lab |
|---|---|---|
| IAM com privilégio mínimo por função | Uma role por função | Uma `LabRole` única para tudo — não é possível criar role |
| Deploy pelo GitHub Actions | Login na AWS sem chave guardada no repositório | Credencial expira a cada sessão de 4 h e precisa ser recolocada no secret antes de cada deploy |
| Custo | Cartão de alguém do grupo; free tier, com orçamento e alarme | Sem custo; o lab morre quando o orçamento acaba |
| Regiões | Qualquer uma | Só `us-east-1` e `us-west-2` |

Se for Learner Lab, a role única e o deploy semiautomático entram no README como limitações declaradas.

**A verificar no Learner Lab**, se for o caso: SES (envio de e-mail) liberado e fora do sandbox? SnapStart disponível?

## 2. Rodada de deploy — ainda não discutida

Os dois caminhos acima são AWS; o que muda entre eles está marcado.

| Ponto | O que decidir | Depende de |
|---|---|---|
| Banco | Qual banco grava as Evaluations, e como a consulta por Report Week é feita sem varrer a tabela. Precisa suportar expiração automática (12 semanas, ADR 0009) | — |
| Gravação → aviso | Mecanismo pelo qual a gravação da Evaluation dispara a `send-urgency-notice` (ADR 0003), e se o filtro "só `CRITICAL`" fica na assinatura do evento ou na função | Banco |
| DLQ | Fila de mensagens que falharam da `send-urgency-notice`, número de tentativas | Gravação → aviso |
| Serviço de e-mail | Envio do Urgency Notice e do Weekly Report em HTML. Verificação dos endereços dos Administrators enquanto o serviço estiver em sandbox | Conta |
| Canal do Operational Alert | Mecanismo independente do serviço de e-mail das funções (ADR 0007), com a lista configurada no monitoramento | — |
| Agendador | Precisa aceitar fuso `America/Sao_Paulo` (ADR 0008); senão, cron em UTC | — |
| Gateway | Limite de requisições por segundo e de tamanho do corpo (ADR 0006); retenção dos logs de acesso | — |
| Monitoramento | Métricas, limites e alarmes dos 5 gatilhos do Operational Alert (ADR 0007) | Canal do Operational Alert |
| IAM | Uma role por função com privilégio mínimo | **Conta** |
| Segurança dos dados | Criptografia em repouso do banco e das filas | Banco |
| IaC | Ferramenta (Terraform, CDK, SAM…) | — |
| Pipeline | GitHub Actions: build, teste, deploy; autenticação na AWS | **Conta**, IaC |
| SnapStart | Publicação de versão e alias a cada deploy (ADR 0004) | IaC, **Conta** |
| Lista de Administrators | Onde fica a configuração da lista fixa por ambiente, fora do repositório | Serviço de e-mail |
| Ambientes | Um só (demonstração) ou dev + demonstração | Conta, custo |
| Custo | Orçamento mensal e alarme | Conta |

## 3. Decidido, ainda não registrado em documento próprio

- **Lista de Administrators:** fixa por ambiente, na configuração e fora do repositório; uma lista só para Urgency
  Notice, Weekly Report e Operational Alert; a função falha ao subir se estiver vazia. Vira instrução de deploy no
  README. Cadastro de Administrators está fora de escopo: o avaliador assiste ao vídeo, não usa o ambiente.
- **Estratégia de teste:** registrada no README §Testes; o roteiro ponta a ponta vira `docs/validacao-manual.md`
  quando houver o que validar.

## 4. Processo do grupo

- [ ] Avisar o grupo no canal com o link das ADRs 0001–0009. Sem objeção em 48 h, viram `aceita`.
- [ ] Convidar o grupo para o repositório.
- [ ] Proteger a `main`.
- [ ] Dividir o trabalho entre os integrantes.

## 5. Entregáveis ainda não produzidos

O enunciado avalia a descrição do projeto com arquitetura, instruções de deploy, configuração do monitoramento e
documentação das funções, além do vídeo.

- [ ] Desenho da arquitetura, depois da rodada de deploy.
- [ ] Instruções de deploy no README.
- [ ] Configuração do monitoramento documentada.
- [ ] Documentação de cada função.
- [ ] `docs/validacao-manual.md` e collection Postman, que servem também de roteiro do vídeo.
- [ ] Vídeo: aplicação funcionando, funções ativas, configurações.
- [ ] Antes de entregar: provar o build em outro SO, ou com `core.autocrlf=true` (lição da Fase 3).
