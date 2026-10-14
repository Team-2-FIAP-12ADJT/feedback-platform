# feedback-platform

Tech Challenge da **Fase 4** da pós-graduação FIAP ADJT — Cloud Computing, Serverless e Deploy em nuvem.

Plataforma de feedback de aulas: estudantes avaliam as aulas, administradores recebem aviso imediato dos
itens críticos e um relatório semanal com a média das avaliações.

> **Estado:** enunciado lido; **nenhuma decisão de arquitetura tomada e nada implementado.** As decisões
> abertas estão listadas abaixo e serão registradas como ADR em `docs/adr/` conforme o grupo decidir.

Enunciado: [`docs/ADJT - Fase 4 - Tech Challenge_rev.pdf`](<docs/ADJT - Fase 4 - Tech Challenge_rev.pdf>).

## O que o enunciado exige

| Bloco | Exigência |
|---|---|
| Regras | Serverless e cloud **obrigatórios**; mínimo de **2 funções**, com responsabilidade única — a separação é avaliada |
| Entrada | `POST /avaliação` com `{ "descricao": string, "nota": int 0–10 }` |
| Aviso de urgência (e-mail) | Descrição, urgência, data de envio |
| Relatório semanal | Média das avaliações; descrição, urgência, data de envio; quantidade por dia; quantidade por urgência |
| Ambiente | Segurança dos dados e governança de acesso; componentes de suporte (banco etc.); deploy automatizado dos componentes atualizáveis; monitoramento; notificação automática aos administradores para problemas críticos |
| Entrega | Repositório aberto + **vídeo** mostrando a aplicação, as funções serverless ativas e as configurações |
| Avaliação | Modelo de cloud e componentes explicados; funcionamento; código documentado; descrição com arquitetura, instruções de deploy, configuração do monitoramento e documentação das funções; configuração de nuvem e segurança explicadas |

## Decisões em aberto

1. **Urgência não vem na entrada.** O payload tem só `descricao` e `nota`, mas o e-mail e o relatório pedem
   "urgência" — ela precisa ser derivada da nota, com faixas a definir.
2. **O que é "crítico"** para disparar o aviso — consequência da faixa do item 1.
3. **Rota com acento** (`/avaliação`): acento em path costuma quebrar gateway e cliente HTTP; avaliar `/avaliacao`.
4. **Provedor de cloud** e a ferramenta de deploy automatizado (IaC + pipeline).
5. **Quantas funções e com que fronteira** — o mínimo é 2; a separação por responsabilidade é critério de avaliação.

O exemplo de JSON do enunciado tem vírgula sobrando: é ilustração, não contrato. O prazo de entrega está na
plataforma da FIAP, não no PDF.
