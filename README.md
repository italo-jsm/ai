# AI — ambiente local

Requisitos: JDK 21, Docker com Compose e Ollama local.

1. Inicie o Docker e crie o banco PostgreSQL com pgvector:

   ```bash
   docker compose up -d --wait
   ```

   Banco: `ai`; usuario: `ai`; senha local: `ai_local`; porta: `5433`.
   Os dados persistem no volume `postgres_data`.

2. Inicie o Ollama no host (pelo aplicativo ou `ollama serve`) e prepare os modelos:

   ```bash
   ollama list
   ollama pull nomic-embed-text
   ```

   O modelo de chat `gemma4:12b-mlx` deve estar disponivel com esse nome no Ollama.
   O modelo `nomic-embed-text` gera os embeddings de 768 dimensoes para o pgvector.
   A aplicacao nao baixa modelos automaticamente.

3. Inicie a aplicacao:

   ```bash
   ./gradlew bootRun
   ```

O Ollama e acessado em `http://localhost:11434`. O Spring AI inicializa sua tabela
vetorial; o Liquibase cria as tabelas de memória e o Hibernate valida o esquema.

As variaveis `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_PORT`
podem ser exportadas no terminal antes de iniciar tanto o Compose quanto a aplicacao.
`OLLAMA_BASE_URL` permite alterar o endereco do Ollama.

Para parar o banco preservando os dados: `docker compose down`.


### Memórias do agente

O chat extrai e salva as memórias no PostgreSQL. Cada memória possui UUID,
usuário, data de criação, tipo, sujeito, predicado, valor e motivo.
A gravação é transacional. Extrações vazias não geram registros.
A migração inicial cria diretamente a tabela `memory`, com usuário e data.
`MemoryExtraction` permanece como DTO do agente.
A busca semântica consulta o pgvector; a gravação atual não indexa novos embeddings.

Envie o identificador do usuário no chat:

```json
{"userId":"italo","message":"Prefiro café sem açúcar."}
```

- `POST /api/v1/chat`: responde e salva a extração.
- `GET /api/v1/memories?userId=italo&type=PREFERENCE&page=0&size=20`: busca paginada.
- Filtros opcionais: `subject` e `predicate` por igualdade; `text` procura trechos
  do valor, sem distinguir maiúsculas e minúsculas. `size`: 1 a 100.
- `GET /api/v1/memories/semantic?userId=italo&query=gostos%20alimentares&limit=10`:
  busca por significado, em ordem de relevância. `limit`: 1 a 100.

Todas as consultas filtram pelo usuário. O projeto ainda não possui autenticação:
`userId` é informado pelo cliente e **não comprova identidade**. Ao adicionar login,
obtenha esse identificador da sessão autenticada.

As migrações ficam em `src/main/resources/db/changelog`. A tabela vetorial continua
sendo inicializada pelo Spring AI. A tabela `memory` não possui índices adicionais. A busca por significado usa o
índice HNSW da tabela vetorial do Spring AI.

Testes de persistência e serviço, sem Ollama nem PostgreSQL:

```bash
./gradlew test --tests '*MemoryPersistenceTest' --tests '*MemoryServiceTest'
```


### Uso das memórias no chat

Antes de responder, o chat consulta pelo JPA as memórias do `userId` recebido e
inclui até 20 registros no contexto. A seleção é das mais recentes, com desempate
pelo ID. O agente escolhe quais informações ajudam na resposta e prioriza a mensagem
atual quando houver conflito. Usuários sem memórias seguem o fluxo normal.

Configure `app.memory.context-limit` (1 a 100) para ajustar a quantidade carregada.
Esta recuperação não consulta o pgvector nem gera embeddings. Após responder, o
chat continua extraindo e salvando novas memórias. O `userId` ainda é informado
pelo cliente; quando houver autenticação, deverá vir da sessão autenticada.


### Fidelidade das memórias

A extração usa temperatura zero e exemplos para preservar negações, manter os
campos em português e ignorar perguntas sem afirmações pessoais. Preferências
usam sujeito `usuário`, predicado `gosta de` ou `não gosta de` e o objeto no valor.
Isso reduz erros de interpretação, mas a extração continua dependendo do modelo.
O contexto traduz registros antigos como `DISLIKE=true` para uma frase explícita
de rejeição, sem alterar os registros do banco.

Para executar os testes de regressão com o Ollama real disponível:

```bash
RUN_OLLAMA_TESTS=true ./gradlew test --tests '*MemoryExtractorOllamaTest'
```

Esses testes consultam o modelo sem salvar as extrações. Na suíte padrão, ficam
desabilitados para não exigir inferência local em cada execução.
