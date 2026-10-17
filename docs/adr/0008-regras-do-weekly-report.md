# ADR 0008 — Regras do Weekly Report

- **Status:** aceita
- **Data:** 2026-10-14

## Contexto

O enunciado pede um relatório semanal com "Descrição; Urgência; Data de envio; Quantidade de avaliações por dia;
Quantidade de avaliações por urgência" e a média das avaliações. Não diz que semana, em que fuso, quais Evaluations
listar, nem o que fazer quando não há nenhuma.

## Decisão

### Report Week

- **Semana de calendário, de segunda 00:00 a domingo 23:59:59, no fuso `America/Sao_Paulo`** — o identificador do fuso,
  não o offset `-03:00` fixo.
- **Roda na segunda às 08:00 de Brasília**, cobrindo a semana anterior já fechada. As 8 horas de folga garantem que uma
  Evaluation de domingo 23:59 já esteja gravada.
- **A semana é parâmetro** (ex.: `2026-W46`), não "agora menos 7 dias". O agendamento passa a semana anterior à data
  agendada; uma execução manual passa a semana desejada. Rodar de novo na terça dá o mesmo relatório de segunda.

### Conteúdo

- **Cabeçalho** com o intervalo ("Semana de 09/11 a 15/11/2026") e o total de Evaluations.
- **Average Score** com duas casas e vírgula decimal (`7,35`).
- **Contagem por Urgency**: as três sempre presentes, inclusive zeradas.
- **Contagem por dia**: os 7 dias sempre presentes, inclusive zerados, no fuso de Brasília.
- **Lista de Evaluations**: todas as da semana, ordenadas por Urgency (`CRITICAL` primeiro) e depois por Submission
  Time, com **teto de 500**. Acima disso, uma linha "e mais N avaliações não listadas".
  **O teto vale só para a lista: o Average Score e as contagens sempre consideram todas as Evaluations.**
- Cada item mostra Description (escapada), Urgency em português e Submission Time em `dd/MM/yyyy HH:mm`, horário de
  Brasília.
- E-mail em **HTML com versão em texto puro**.

### Semana sem Evaluations

- **O relatório sai assim mesmo.** Average Score aparece como "sem avaliações", nunca `0,00` nem `NaN`; contagens
  zeradas; lista vazia.
- **O assunto já diz o caso**: "Relatório semanal 09/11–15/11: nenhuma avaliação".

## Alternativas consideradas

- **Últimos 7 dias a partir da execução**: um atraso do agendamento faz uma hora entrar em dois relatórios ou em
  nenhum, e rodar de novo dá outro resultado.
- **Semana em UTC**: toda Evaluation enviada entre 21:00 e 23:59 de Brasília cairia no dia seguinte.
- **Listar só as Critical Evaluations**: interpreta o enunciado, que pede Description e Urgency sem filtro.
- **Lista em CSV anexo**: exige montar o e-mail em MIME bruto, por um formato que o enunciado não pede.
- **Não enviar numa semana vazia**: o Administrator não distingue "ninguém avaliou" de "o relatório quebrou".

## Consequências

- Um relatório que sempre chega é, ele próprio, o sinal de que o agendamento está vivo.
- O agendador precisa aceitar fuso; se não aceitar, o cron fica em UTC (segunda às 11:00) e precisa ser revisto se o
  horário de verão voltar.
