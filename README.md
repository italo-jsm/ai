# AI — ambiente local

Requisitos: JDK 21, Docker com Compose, Ollama local para embeddings e acesso à Groq para chat.

1. Inicie o Docker e crie o banco PostgreSQL com pgvector:

   ```bash
   docker compose up -d --wait
   ```

   Banco: `ai`; usuario: `ai`; senha local: `ai_local`; porta: `5433`.
   Os dados persistem no volume `postgres_data`.

2. Inicie o Ollama no host (pelo aplicativo ou `ollama serve`) e prepare o modelo de embeddings:

   ```bash
   ollama list
   ollama pull nomic-embed-text
   ```

   O modelo `nomic-embed-text` gera os embeddings de 768 dimensoes para o pgvector.
   Não é necessário carregar um modelo de chat no Ollama para o uso normal.
   A aplicacao nao baixa modelos automaticamente.

3. Configure as variáveis da Groq e inicie a aplicação:

   ```bash
   export GROQ_API_KEY='sua-chave'
   export GROQ_MODEL='openai/gpt-oss-20b'
   ./gradlew bootRun
   ```

   Use em `GROQ_MODEL` o identificador de um modelo disponível na sua conta
   com suporte a tool calling. As duas variáveis são obrigatórias para o chat.
   A aplicação não carrega arquivo `.env` automaticamente.

O Ollama e acessado em `http://localhost:11434`. O Spring AI inicializa sua tabela
vetorial; o Liquibase cria as tabelas de memória e o Hibernate valida o esquema.

As variaveis `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_PORT`
podem ser exportadas no terminal antes de iniciar tanto o Compose quanto a aplicacao.
`OLLAMA_BASE_URL` permite alterar o endereco do Ollama.

Para parar o banco preservando os dados: `docker compose down`.


### Chat na Groq e embeddings locais

O adaptador `spring-ai-starter-model-openai` do Spring AI conecta à Groq por sua
API compatível. O prefixo `openai` identifica o adaptador; as requisições vão
para a URL Groq configurada:

```properties
spring.ai.model.chat=openai
spring.ai.openai.base-url=https://api.groq.com/openai/v1
spring.ai.openai.api-key=${GROQ_API_KEY}
spring.ai.openai.chat.model=${GROQ_MODEL}
spring.ai.model.embedding=ollama
spring.ai.ollama.embedding.model=nomic-embed-text
```

Chat e extração de memórias usam o modelo escolhido em `GROQ_MODEL`. Mensagens
e memórias incluídas no contexto são enviadas à Groq. As ferramentas de horário
e busca Brave permanecem disponíveis no chat; `BRAVE_API_KEY` continua separada.

O Ollama permanece necessário para indexar e buscar por embeddings.
A troca do modelo de chat não altera o modelo vetorial, as 768 dimensões ou
os registros existentes; não exige reindexação.

Compatibilidade com modelos de raciocínio na Groq: o Spring AI 2.0.1 pode ler
`reasoning` da resposta e reenviá-lo como `reasoning_content` no histórico,
campo que a Groq rejeita com HTTP 400. A configuração abaixo habilita um advisor
que remove somente o metadado `reasoningContent` de cópias das mensagens do
assistente antes de cada chamada do modelo, inclusive após ferramentas:

```properties
app.groq.reasoning-history-filter-enabled=true
```

O conteúdo, as chamadas de ferramentas e seus resultados são preservados.
Não desliga o raciocínio do modelo nem altera a resposta original.
Essa compatibilidade é específica da Groq; desative a opção ao usar um provedor
que exija o reenvio de raciocínio. O fluxo atual usa chamadas síncronas
`ChatClient.call()`.

`GroqConfigurationTest` verifica seleção dos provedores, endpoint compatível,
autenticação com chave fictícia, execução de ferramenta e extração JSON.
Usa H2 e servidor HTTP local, sem Groq, Ollama ou Brave reais:

```bash
./gradlew test --tests '*GroqConfigurationTest'
```

### Memórias do agente

O chat extrai e salva as memórias no PostgreSQL. Cada memória possui UUID,
usuário, data de criação, tipo, sujeito, predicado, valor e motivo.
A gravação é transacional. Extrações vazias não geram registros.
A migração inicial cria diretamente a tabela `memory`, com usuário e data.
`MemoryExtraction` permanece como DTO do agente.
A busca semântica consulta o pgvector. O chat pode indexar novas memórias após o
commit SQL quando a indexação está habilitada.

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


### RAG de memórias no chat

A recuperação de contexto usa a interface `MemoryRetriever`, com duas estratégias:

- `sql` (padrão): seleciona as memórias mais recentes do usuário, ordenadas por
  data decrescente e ID como desempate.
- `semantic`: usa a mensagem atual para buscar memórias por similaridade no
  pgvector. Filtra `userId` e `kind=memory`, carrega os registros oficiais no
  SQL e preserva a ordem de relevância. Vetores órfãos são ignorados.

Configure em `application.properties` ou por argumentos do Spring Boot:

```properties
app.memory.retrieval-strategy=sql
app.memory.indexing.enabled=false
app.memory.context-limit=20
```

Recuperação e indexação são independentes. Para começar, habilite a indexação
mantendo SQL como estratégia:

```bash
./gradlew bootRun --args='--app.memory.indexing.enabled=true'
```

Envie mensagens com fatos ou preferências para gerar novas memórias. Depois,
habilite também a recuperação semântica:

```bash
./gradlew bootRun --args='--app.memory.indexing.enabled=true --app.memory.retrieval-strategy=semantic'
```

O chat gera a resposta, extrai memórias, confirma o salvamento SQL e chama
`AsyncMemoryIndexingService`, um bean separado com `@Async`. A indexação é
executada em segundo plano pelo executor do Spring, sem esperar pelos embeddings
para retornar a resposta. Quando habilitado, `MemoryIndexer` gera embeddings e
grava documentos com o UUID SQL, texto da memória e metadados de usuário e tipo.
Quando desabilitado, `NoOpMemoryIndexer` não chama o modelo de embeddings.
Reindexar o mesmo UUID atualiza o documento vetorial, sem duplicá-lo.

A busca semântica tem consistência eventual: uma mensagem enviada logo após o
salvamento pode ainda não encontrar a nova memória. Se a indexação falhar, a
memória permanece salva no SQL e o erro é registrado sem conteúdo pessoal.
Falha ao submeter a tarefa também não impede o retorno da resposta.
As tarefas ficam em memória; encerrar a aplicação antes de concluí-las pode
perder a indexação. Não existem retentativas, fila persistente, indexação
automática do histórico ou backfill.
Memórias antigas e indexações que falharem continuam acessíveis por SQL, mas não
entram automaticamente na busca semântica. A chamada direta de
`MemoryService.save` apenas persiste no SQL; o chat coordena a indexação.

Uma busca semântica vazia não adiciona contexto. Se o serviço de embeddings ou a
consulta vetorial falhar durante a recuperação, o erro é propagado; não existe
fallback automático. Para voltar à seleção de memórias recentes, configure
`app.memory.retrieval-strategy=sql`; isso não altera o salvamento nem desliga
a indexação.

`app.memory.context-limit` aceita valores de 1 a 100. O contexto preserva
negações, trata memórias como dados e prioriza a mensagem atual em conflitos.
Todas as recuperações filtram o usuário informado pelo cliente; ao adicionar
autenticação, esse identificador deverá vir da sessão autenticada.

### Testes

A suíte padrão usa H2 e não exige PostgreSQL nem Ollama:

```bash
./gradlew test
```

Com o PostgreSQL local iniciado, execute os testes existentes de inicialização
e persistência:

```bash
RUN_POSTGRES_TESTS=true ./gradlew test --tests '*AiApplicationTests' --tests '*PostgreSqlMemoryPersistenceTest'
```

A integração RAG usa PostgreSQL/pgvector real e embeddings determinísticos de
768 dimensões, sem inferência Ollama. Verifica commit antes de indexar,
indexação em segundo plano, falha sem perda do SQL, isolamento e atualização
pelo mesmo UUID:

```bash
RUN_RAG_POSTGRES_TESTS=true ./gradlew test --tests '*MemoryRagPostgresTest'
```

Ao repetir testes opt-in após mudar variáveis de ambiente, acrescente
`--rerun-tasks` para evitar reutilização de um resultado anterior do Gradle.
Os testes de RAG usam usuários exclusivos e removem somente seus próprios registros.

### Fidelidade das memórias

A extração usa temperatura zero e exemplos para preservar negações, manter os
campos em português e ignorar perguntas sem afirmações pessoais. Preferências
usam sujeito `usuário`, predicado `gosta de` ou `não gosta de` e o objeto no valor.
Isso reduz erros de interpretação, mas a extração continua dependendo do modelo.
O contexto traduz registros antigos como `DISLIKE=true` para uma frase explícita
de rejeição, sem alterar os registros do banco.

Os testes abaixo continuam exercitando especificamente o Ollama: substituem
a seleção de chat da aplicação por `ollama` e usam `qwen2.5:7b`.
Prepare esse modelo local e o PostgreSQL para executá-los; eles não validam a Groq:

```bash
RUN_OLLAMA_TESTS=true ./gradlew test --tests '*MemoryExtractorOllamaTest'
```

Esses testes consultam o modelo sem salvar as extrações. Na suíte padrão, ficam
desabilitados para não exigir inferência local em cada execução.

### Busca na web com Brave

O serviço `BraveSearchService` pesquisa na Brave Search e retorna até cinco
resultados, na ordem do provedor, com `title`, `url` e `description`.
O serviço continua independente de IA. A classe `WebSearchTool` o expõe ao
modelo como `searchWeb`, registrada no `ChatClient` junto da ferramenta de
horário. Não existe endpoint REST separado para busca.

Configure a credencial no ambiente antes de iniciar a aplicação:

```bash
export BRAVE_API_KEY='sua-chave'
./gradlew bootRun
```

Configuração disponível em `application.properties`:

```properties
app.brave-search.api-key=${BRAVE_API_KEY:}
app.brave-search.base-url=https://api.search.brave.com
app.brave-search.timeout=10s
```

O timeout deve ser positivo e é aplicado à conexão e à espera HTTP.
A aplicação pode iniciar sem chave; tentar pesquisar sem configurá-la produz
`BraveSearchException` com razão `CONFIGURATION`.
A URL base pode ser substituída em testes por um servidor simulado.

Injete `BraveSearchService` no consumidor e chame:

```java
List<SearchResult> results = braveSearchService.search("Fernando da Gata");
```

Consultas nulas ou em branco produzem `IllegalArgumentException`, sem chamar
a API. Espaços externos são removidos e caracteres especiais são codificados.
Uma busca válida sem resultados retorna lista vazia. Descrição ausente retorna
string vazia.

Falhas da integração geram `BraveSearchException`; `reason()` identifica:
`AUTHENTICATION` (401/403), `RATE_LIMIT` (429), `UNAVAILABLE` (5xx/conexão),
`TIMEOUT`, `INVALID_RESPONSE` ou `REQUEST_FAILED` (demais erros HTTP).
As exceções não incluem a chave, a consulta ou o corpo de erro do provedor.
Não existem retentativas, cache, persistência, paginação nem leitura das páginas.

Os testes usam um servidor HTTP local com chave fictícia, sem consumir a Brave,
PostgreSQL ou Ollama:

```bash
./gradlew test --tests '*BraveSearchServiceTest'
```

Contrato do provedor: [Brave Web Search API](https://api-dashboard.search.brave.com/api-reference/web/search/get).


### Busca Brave pelo modelo

Com `BRAVE_API_KEY` configurada, use o endpoint de chat existente:

```json
{"userId":"italo","message":"Pesquise na internet quem foi Fernando da Gata e cite as fontes."}
```

A ferramenta `searchWeb(query)` recebe somente o termo de pesquisa. A chave,
URL do provedor e timeout permanecem controlados pela aplicação. O resultado
contém `results` (título, URL e descrição) e `error`:

- Sucesso: resultados encontrados e `error=null`.
- Sem fontes: lista vazia e `error=null`.
- Falha de integração ou consulta inválida: lista vazia e erro explícito,
  sem propagar detalhes HTTP ao modelo.

As instruções orientam o modelo a buscar quando o usuário pedir pesquisa,
informações atuais ou identificação/biografia de pessoas, citar as URLs usadas,
informar falhas e pedir esclarecimento em casos de nomes ambíguos.
Os trechos retornados são dados, não instruções. A ferramenta não abre páginas.

A decisão de chamar a ferramenta continua sendo do modelo; o registro não
garante que toda pergunta provoque uma busca. Não há pesquisa automática no
controller nem persistência dos resultados da web como memórias.

Teste de registro e execução pelo callback real do Spring AI, sem Ollama ou
Brave real:

```bash
./gradlew test --tests '*WebSearchToolTest'
```
