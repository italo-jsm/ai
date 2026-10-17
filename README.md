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
vetorial; o Hibernate atualiza o esquema das entidades para desenvolvimento local.

As variaveis `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` e `POSTGRES_PORT`
podem ser exportadas no terminal antes de iniciar tanto o Compose quanto a aplicacao.
`OLLAMA_BASE_URL` permite alterar o endereco do Ollama.

Para parar o banco preservando os dados: `docker compose down`.
