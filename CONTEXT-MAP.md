# Context Map

Plataforma de feedback de aulas: estudantes avaliam as aulas, administradores recebem aviso imediato das avaliações
críticas e um relatório semanal (FIAP Tech Challenge — Fase 4).

Identificadores de código e termos canônicos são em inglês. Cada termo mapeia a palavra usada no enunciado para o
termo canônico, para manter a rastreabilidade entre spec e código.

## Contexts

- **Feedback** — é dono das Evaluations: recebe, classifica por Urgency, avisa os Administrators das críticas e
  consolida o Weekly Report

Há um contexto só. As funções serverless separam **responsabilidades** dentro dele (receber, avisar, relatar), não
linguagens diferentes: todas falam de Evaluation, Score e Urgency com o mesmo sentido.

## Language

### Evaluation

**Evaluation** (spec: _avaliação_, _feedback_):
O que um Student envia sobre uma aula: uma Description e um Score.
_Avoid_: Feedback, Review, Rating, Avaliação

**Score** (spec: _nota_):
Inteiro de 0 a 10 que o Student atribui à aula.
_Avoid_: Grade, Rating, Note, Nota

**Description** (spec: _descricao_):
Texto livre da Evaluation, escrito pelo Student.
_Avoid_: Comment, Message, Descrição

**Submission Time** (spec: _data de envio_):
Instante em que a plataforma recebeu a Evaluation. O Student não o informa.
_Avoid_: Created At, Timestamp, Sent Date, Data de envio

### Urgency

**Urgency** (spec: _urgência_):
Classe da Evaluation derivada do Score — `CRITICAL`, `MEDIUM` ou `LOW`. Nunca é informada pelo Student.
_Avoid_: Priority, Severity, Urgência

**Critical Evaluation** (spec: _itens críticos_):
Evaluation de Urgency `CRITICAL`.
_Avoid_: Critical Item, Complaint, Negative Feedback

### O que chega aos administradores

**Urgency Notice** (spec: _e-mail de aviso de urgência_, _notificações para itens críticos_):
Mensagem aos Administrators disparada por uma Critical Evaluation.
_Avoid_: Alert, Notification, Email

**Weekly Report** (spec: _relatório semanal_, _relatórios periódicos_):
Consolidado das Evaluations de uma Report Week enviado aos Administrators: o Average Score e as contagens por dia e
por Urgency.
_Avoid_: Summary, Digest, Report, Relatório

**Report Week** (spec: _semanal_):
Semana de calendário de segunda 00:00 a domingo 23:59:59, no horário de Brasília, coberta por um Weekly Report.
_Avoid_: Last 7 Days, Period

**Operational Alert** (spec: _notificações automáticas aos administradores para problemas críticos_):
Mensagem aos Administrators sobre uma falha da plataforma, não sobre uma Evaluation.
_Avoid_: Urgency Notice, Incident, Alarm

**Average Score** (spec: _média de avaliações_):
Média aritmética dos Scores das Evaluations do período.
_Avoid_: Mean Rating, Average Evaluation, Média

### Pessoas

**Student** (spec: _estudantes_, _alunos_):
Quem envia Evaluations.
_Avoid_: User, Customer, Aluno

**Administrator** (spec: _administradores_):
Quem recebe Urgency Notices e Weekly Reports.
_Avoid_: Admin, Manager, Gestor

## Relationships

- **Student → Feedback**: o Student envia a Evaluation; a Urgency e o Submission Time são atribuídos pela
  plataforma, nunca pelo Student
- **Feedback → Administrator (imediato)**: cada Critical Evaluation gera um Urgency Notice; Evaluation `MEDIUM` ou
  `LOW` não gera aviso
- **Feedback → Administrator (semanal)**: o Weekly Report agrupa pela Urgency gravada na Evaluation, não por uma
  recalculada do Score
- **Administrator ↛ Evaluation**: o Administrator só lê; nenhum fluxo do enunciado altera uma Evaluation recebida
