# ADR 0006 — `POST /avaliacao` anônimo, com limites na borda

- **Status:** proposta
- **Data:** 2026-10-14

## Contexto

O `POST /avaliacao` é a única superfície pública da plataforma: o enunciado não pede nenhuma rota de leitura, e o
Administrator recebe tudo por e-mail. O payload não traz identidade do Student, e o repositório é público.

O cenário de risco: um script envia milhares de Evaluations com Score 0, e cada uma vira um Urgency Notice na caixa
dos Administrators.

## Decisão

- **O endpoint é anônimo.**
- **O gateway limita a taxa de requisições e o tamanho do corpo.**
- **O risco de enxurrada de Urgency Notices fica declarado no README.** O login de Student fica registrado como a
  evolução que o resolveria.
- A "governança de acesso" do enunciado é tratada no acesso aos recursos da cloud (IAM), não na identidade do Student.

## Alternativas consideradas

- **API key do gateway, com cota**: a chave estaria na collection, no vídeo e no README de um repositório público. Não
  é segredo, só um limitador com outro nome.
- **Login de Student** (ex.: Cognito/JWT): resolve, mas exige cadastro, login e emissão de token, que o enunciado não
  pede.
- **Regra por IP no firewall de aplicação** (ex.: WAF): corta o script de um IP só, com custo fixo mensal. Não decidido;
  depende da conta.

## Consequências

- O limite de taxa freia, mas não impede, uma enxurrada: o alarme de volume do monitoramento é o que avisa.
- Os limites numéricos (requisições por segundo, tamanho do corpo) ficam para a rodada de deploy.
