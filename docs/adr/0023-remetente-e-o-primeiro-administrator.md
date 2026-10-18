# ADR 0023 — O remetente do SES é o primeiro endereço da lista de Administrators

- **Status:** proposta
- **Data:** 2026-10-18

## Contexto

A [ADR 0014](0014-ses-para-funcoes-sns-para-alerta.md) deixou o remetente do Urgency Notice e do Weekly Report em
aberto: sem domínio próprio, seria um endereço verificado no SES. A preferência registrada até aqui era um e-mail do
grupo, não pessoal.

Todo e-mail sai de **um** remetente (`From`). O SES em sandbox só envia de uma identidade verificada, e a
[ADR 0020](0020-lista-de-administrators-no-github.md) já cria uma identidade verificada para cada Administrator.

## Decisão

**O remetente é o primeiro endereço da variável `ADMINISTRATORS`.** Nenhum e-mail do grupo, nenhum domínio próprio,
nenhuma variável a mais.

- O template ([ADR 0012](0012-iac-com-aws-sam.md)) tira o remetente da mesma lista que já gera os destinatários, as
  assinaturas do SNS e as identidades do SES: **uma lista, quatro usos**. A identidade do remetente é a que a ADR 0020 já
  cria.
- **Trocar o remetente é reordenar a lista** e rodar o deploy.
- O e-mail sai com o nome de exibição **"Feedback Platform"** antes do endereço, para o Administrator reconhecer a
  origem.
- A condição `ses:FromAddress` da [ADR 0019](0019-uma-role-por-funcao.md) e da
  [ADR 0022](0022-send-urgency-notice-le-a-marca.md) prende o envio a esse endereço.
- O endereço, como toda a lista, fica na *variable* do GitHub, fora do repositório público.

## Alternativas consideradas

- **E-mail novo do grupo** num provedor gratuito: mais uma conta para guardar a senha e o MFA, com o mesmo problema de
  autenticação de um endereço pessoal.
- **Domínio próprio** com Easy DKIM e DMARC: o único caminho em que o e-mail chega autenticado, mas exige registrar e
  pagar um domínio e manter os registros de DNS.
- **Uma variável só para o remetente**: permite escolher qualquer Administrator, mas é uma segunda lista para manter
  coerente com a primeira — o que a ADR 0020 evita.

## Consequências

- **O e-mail chega sem autenticação.** Um endereço de provedor (Gmail, Outlook, `@fiap.com.br`) não passa no DMARC
  quando sai pelo SES, porque a assinatura não é do domínio do remetente
  ([documentação da AWS](https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-dmarc.html)). O Urgency
  Notice e o Weekly Report podem cair no spam, ou ser recusados.
- **Mitigação:** cada Administrator cria na própria caixa uma regra que nunca manda para o spam e-mails desse remetente.
  A regra entra nas instruções de deploy e no roteiro de validação; um Urgency Notice no spam é um aviso perdido.
- **O primeiro Administrator recebe e-mail "de si mesmo"**, que o provedor tende a marcar como suspeito — a mesma
  regra resolve.
- Respostas ao Urgency Notice e ao Weekly Report vão para a caixa pessoal do primeiro Administrator.
- O Operational Alert não muda: sai pelo SNS, com remetente da AWS ([ADR 0014](0014-ses-para-funcoes-sns-para-alerta.md)).
