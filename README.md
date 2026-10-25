
# Metopa

Metopa é uma plataforma para publicação, descoberta e leitura de quadrinhos, mangás, graphic novels, webtoons e outros formatos de narrativa visual.

O projeto busca oferecer um ambiente onde criadores possam publicar e gerenciar suas obras, enquanto leitores podem descobrir conteúdos, acompanhar suas leituras e organizar uma biblioteca pessoal.

## Status

**Em desenvolvimento — MVP**

A primeira fase do Metopa utiliza infraestrutura local, sem dependência de serviços cloud pagos.

O backend possui atualmente **242 testes automatizados aprovados**, cobrindo regras de domínio, persistência, segurança, APIs HTTP, armazenamento de arquivos, transações e concorrência.

## Tecnologias

### Backend

- Java 25
- Spring Boot 4.1
- Spring Modulith
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security
- Bean Validation

### Persistência e infraestrutura

- PostgreSQL 18
- Flyway
- Docker Compose
- Armazenamento de arquivos em filesystem local

### Desenvolvimento e testes

- Maven Wrapper
- JUnit
- Mockito
- Testes unitários e de integração

## Arquitetura

O Metopa utiliza uma arquitetura de **monólito modular**, implementada com Spring Modulith.

Essa abordagem permite desenvolver o MVP como uma única aplicação, mantendo responsabilidades, dependências e limites explícitos entre os módulos.

Os módulos atuais são:

```text
metopa
├── identity
├── catalog
├── publication
├── library
├── reading
├── media
└── moderation
```

| Módulo | Responsabilidade |
|--------|------------------|
| `identity` | Contas de usuário, autenticação e sessões |
| `catalog` | Obras, metadados, gêneros e capas |
| `publication` | Installments, páginas, publicação e leitura de conteúdo |
| `library` | Biblioteca pessoal dos leitores |
| `reading` | Registro e consulta do progresso de leitura |
| `media` | Abstração para armazenamento de arquivos binários |
| `moderation` | Denúncias e ações de moderação (planejado) |

O Spring Modulith é utilizado para verificar os limites e as dependências entre os módulos.

Os dados estruturados são persistidos no PostgreSQL. Arquivos binários, como páginas e capas, são armazenados separadamente através da abstração `BinaryStorage`.

O identificador interno de armazenamento (`storageKey`) não é exposto pelos endpoints públicos.

Para informações detalhadas, consulte [Arquitetura](docs/ARCHITECTURE.md).

## Funcionalidades implementadas

### Contas e autenticação

- Criação de contas com validação e normalização dos dados.
- Armazenamento seguro de senhas com bcrypt.
- Autenticação baseada em sessão HTTP.
- Proteção CSRF para operações que alteram estado.
- Login, logout e consulta do usuário autenticado.

### Catálogo de obras

- Criação e atualização de obras pelo proprietário.
- Diferentes formatos de publicação.
- Configuração da direção de leitura e modo de apresentação.
- Associação de múltiplos gêneros.
- Upload, substituição e leitura de capas.
- Consulta e gerenciamento das obras do criador.
- Descoberta pública de obras com conteúdo publicado.
- Filtragem da descoberta pública por gênero.

### Publicação de conteúdo

- Criação e gerenciamento de installments.
- Tipos: `CHAPTER`, `ISSUE`, `EPISODE` e `STRIP`.
- Estados `DRAFT` e `PUBLISHED`.
- Atualização e exclusão de installments em `DRAFT`.
- Upload e gerenciamento de páginas.
- Reordenação de páginas em installments não publicados.
- Exclusão de páginas em installments `DRAFT`.
- Publicação de installments que possuam pelo menos uma página.
- Leitura pública de installments publicados.
- Acesso administrativo a drafts pelo proprietário.
- Bloqueio de alterações em installments publicados.

### Biblioteca e leitura

- Adição e remoção de obras da biblioteca pessoal.
- Prevenção de entradas duplicadas.
- Listagem da biblioteca do usuário autenticado.
- Registro e atualização do progresso de leitura.
- Consulta da última posição registrada por obra.
- Restrição do progresso a páginas publicadas.

### Persistência e consistência

- Migrations versionadas com Flyway.
- Restrições de integridade no PostgreSQL.
- Bloqueio pessimista para coordenar operações concorrentes.
- Exclusão física de arquivos somente após commit nas operações de remoção.
- Compensação de uploads de páginas em caso de rollback.
- Preservação de registros e arquivos em transações revertidas.

## Requisitos

Para executar o projeto localmente:

- JDK 25
- Docker
- Docker Compose

O Maven Wrapper está incluído no repositório, portanto não é necessário instalar o Maven globalmente.

## Executando localmente

### 1. Iniciar o PostgreSQL

Na raiz do projeto:

```bash
docker compose up -d
```

Verifique se o container está em execução:

```bash
docker compose ps
```

Os dados do PostgreSQL são mantidos em um volume Docker.

### 2. Iniciar a aplicação

Com o PostgreSQL disponível e a configuração local da aplicação ajustada:

```bash
./mvnw spring-boot:run
```

Por padrão, a aplicação fica disponível em:

```text
http://localhost:8080
```

### 3. Encerrar a infraestrutura

```bash
docker compose down
```

Esse comando encerra os containers sem remover o volume persistente do PostgreSQL.

## API HTTP

O Metopa expõe uma API REST para gerenciamento de contas, obras, publicações, bibliotecas e progresso de leitura.

Exemplos de endpoints públicos:

```http
POST /api/users
GET  /api/works
GET  /api/works/{workId}
GET  /api/installments/{installmentId}
GET  /api/pages/{pageId}/content
```

Exemplos de endpoints autenticados:

```http
GET    /api/me/works
POST   /api/works
PUT    /api/works/{workId}
POST   /api/works/{workId}/installments
DELETE /api/installments/{installmentId}
POST   /api/installments/{installmentId}/pages
PUT    /api/installments/{installmentId}/pages/order
POST   /api/installments/{installmentId}/publish
GET    /api/library
PUT    /api/reading/progress/pages/{pageId}
```

As operações protegidas exigem sessão autenticada e, quando alteram estado, token CSRF válido.

A API distingue endpoints públicos de leitura e endpoints administrativos utilizados pelos criadores.

Contratos completos, payloads, autenticação e respostas HTTP estão documentados em [API](docs/API.md).

## Testes

O Metopa utiliza testes unitários e de integração para validar regras de negócio e o comportamento da aplicação.

Para executar a suíte completa:

```bash
./mvnw clean test
```

Resultado da última execução completa reportada:

```text
Tests run: 242
Failures: 0
Errors: 0
Skipped: 0
```

A suíte cobre:

- Regras de domínio e validações.
- Persistência real no PostgreSQL.
- Endpoints HTTP e integração com Spring MVC.
- Autenticação, autorização, sessão e CSRF.
- Armazenamento e leitura de arquivos binários.
- Operações de upload, exclusão e reordenação.
- Rollback e compensação de operações com arquivos.
- Concorrência utilizando transações independentes e bloqueios pessimistas.
- Verificação da modularidade com Spring Modulith.

Os testes de integração que dependem do banco exigem o PostgreSQL disponível.

Para informações detalhadas, consulte [Testes](docs/TESTING.md).

## Banco de dados

O schema do PostgreSQL é gerenciado pelo Flyway.

As migrations estão localizadas em:

```text
src/main/resources/db/migration/
```

O projeto utiliza migrations versionadas. Arquivos de migrations já executadas não devem ser modificados; alterações posteriores são introduzidas por novas versões.

As decisões de persistência e integridade estão descritas em [Arquitetura](docs/ARCHITECTURE.md).

## Segurança

A autenticação do MVP utiliza sessões HTTP gerenciadas pelo Spring Security.

As principais medidas implementadas incluem:

- Hash de senhas com bcrypt.
- Proteção CSRF habilitada.
- Validação de autenticação para operações privadas.
- Autorização baseada na propriedade das obras.
- Separação entre conteúdo público e conteúdo administrativo.
- Bloqueio de alterações em installments publicados.
- Respostas HTTP apropriadas para falhas de autenticação, autorização e regras de negócio.

## Documentação

A documentação técnica detalhada é organizada nos seguintes arquivos:

| Documento | Conteúdo |
|-----------|----------|
| [API](docs/API.md) | Endpoints, payloads, autenticação, respostas HTTP e regras de acesso |
| [Arquitetura](docs/ARCHITECTURE.md) | Módulos, decisões arquiteturais, persistência, armazenamento e transações |
| [Testes](docs/TESTING.md) | Estratégia de testes, cobertura funcional, integração, rollback e concorrência |

## Desenvolvimento

O desenvolvimento é organizado em branches de funcionalidades, correções, refatorações e documentação.

Fluxo habitual:

```text
main
  ↓
feature/*, fix/*, refactor/* ou docs/*
  ↓
commit
  ↓
push
  ↓
pull request
  ↓
merge
```

As alterações são integradas à `main` por meio de Pull Requests.

O desenvolvimento prioriza incrementos pequenos, testes automatizados, clareza das responsabilidades e preservação dos limites entre módulos.
