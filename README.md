# Metopa

Metopa é uma plataforma para publicação, descoberta e leitura de quadrinhos, mangás, graphic novels, webtoons e outros formatos de narrativa visual.

A proposta é permitir que criadores publiquem suas obras e que leitores possam descobrir, acompanhar e consumir esse conteúdo em uma experiência pensada especificamente para quadrinhos.

## Status

Projeto em desenvolvimento — MVP.

Nesta primeira fase, toda a infraestrutura é executada localmente, sem dependência de serviços cloud pagos.

## Tecnologias

- Java 25
- Spring Boot 4.1
- Spring Modulith
- Spring Web MVC
- Spring Data JPA
- Hibernate
- Spring Security
- Bean Validation
- PostgreSQL 18
- Flyway
- Docker Compose
- Maven
- JUnit
- Mockito

## Arquitetura

O Metopa é desenvolvido inicialmente como um **monólito modular** utilizando Spring Modulith.

A escolha permite manter uma única aplicação durante o MVP, mas com limites claros entre os diferentes domínios do sistema.

Os módulos definidos atualmente são:

```text
metopa
├── identity
├── catalog
├── publication
├── library
└── moderation
```

O Spring Modulith é utilizado para verificar automaticamente as dependências e os limites entre os módulos.

Os arquivos das páginas são armazenados fora do banco de dados.

O PostgreSQL mantém apenas os metadados e a referência do arquivo (`storageKey`). No MVP, os arquivos são armazenados no filesystem local através de uma abstração de storage, permitindo que uma implementação diferente seja utilizada futuramente sem acoplar o domínio ao mecanismo físico de armazenamento.

## Módulos

### Identity

Responsável por contas de usuário e autenticação.

Atualmente suporta:

- criação de contas;
- normalização de username e e-mail;
- validação dos dados de entrada;
- unicidade de username e e-mail;
- UUID gerado no domínio;
- armazenamento seguro de senha utilizando hash bcrypt;
- autenticação baseada em sessão HTTP;
- proteção CSRF;
- login;
- logout;
- consulta do usuário autenticado;
- respostas HTTP de autenticação utilizando Problem Details.

### Catalog

Responsável pelo catálogo de obras disponíveis na plataforma.

Atualmente suporta:

- criação de obras;
- associação automática da obra ao usuário autenticado;
- diferentes tipos de obra, como comic, mangá, graphic novel, webtoon e tirinha;
- direção de leitura configurável;
- modo de apresentação configurável;
- normalização e validação dos dados da obra;
- persistência das obras no PostgreSQL.

### Publication

Responsável pela criação, publicação e leitura do conteúdo das obras.

Atualmente suporta:

- criação de installments associados a uma obra;
- diferentes tipos de installment: chapter, issue, episode e strip;
- título opcional para installments;
- installments criados inicialmente como draft;
- validação de propriedade da obra antes de alterações;
- prevenção de installments duplicados por tipo e número;
- criação e ordenação de páginas;
- upload de páginas através de multipart/form-data;
- armazenamento local dos arquivos através de uma abstração de storage;
- armazenamento de metadados das páginas no PostgreSQL;
- prevenção de números de página duplicados;
- publicação de installments;
- publicação permitida somente quando existe pelo menos uma página;
- bloqueio de alterações em installments já publicados;
- leitura pública de installments publicados;
- páginas retornadas em ordem de leitura;
- leitura pública do conteúdo binário das páginas;
- drafts não são expostos pelos endpoints públicos.

### Library

Responsável pela biblioteca pessoal dos usuários.

Atualmente suporta:

- adição de obras à biblioteca pessoal;
- associação automática da entrada ao usuário autenticado;
- somente obras com conteúdo publicado podem ser adicionadas;
- prevenção de entradas duplicadas para a mesma obra e usuário;
- listagem da biblioteca do usuário autenticado;
- isolamento entre bibliotecas de usuários diferentes;
- ordenação das entradas pelas adicionadas mais recentemente;
- remoção de obras da própria biblioteca;
- remoção idempotente: remover uma obra que já não está na biblioteca continua sendo uma operação válida;
- obras sem conteúdo publicado não são expostas através das operações de biblioteca.

### Reading

Responsável pelo progresso de leitura dos usuários.

Atualmente suporta:

- registro da posição atual de leitura;
- uma posição de leitura por usuário e obra;
- atualização do mesmo progresso conforme o usuário avança;
- consulta da última posição registrada;
- identificação automática da obra e do installment através da página;
- registro de progresso somente para páginas publicadas;
- páginas inexistentes e páginas ainda não publicadas são tratadas igualmente como indisponíveis;
- progresso de leitura independente da biblioteca pessoal.

### Moderation

Responsável por denúncias e ações de moderação.

Planejado para etapas posteriores do MVP.

## Decisões arquiteturais

Algumas decisões adotadas no MVP:

- monólito modular antes de microsserviços;
- Spring Modulith para definição e validação das fronteiras entre módulos;
- PostgreSQL como banco relacional;
- Docker Compose para infraestrutura local;
- Flyway como fonte de evolução do schema do banco;
- UUID como identificador das entidades de domínio;
- entidades JPA e repositories mantidos internos aos módulos;
- API pública dos módulos separada de detalhes de persistência;
- autenticação baseada em sessão HTTP no MVP web;
- proteção CSRF mantida habilitada;
- senhas nunca são armazenadas em texto puro;
- serviços cloud e infraestrutura paga ficam fora da primeira fase do MVP;
- novas tecnologias e abstrações são adicionadas somente quando surge uma necessidade concreta.

## Estrutura do projeto

```text
metopa/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── metopa/
│   │   │       ├── identity/
│   │   │       ├── catalog/
│   │   │       ├── publication/
│   │   │       ├── library/
│   │   │       └── moderation/
│   │   │
│   │   └── resources/
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│       ├── java/
│       └── resources/
│
├── compose.yaml
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

## Requisitos

Para executar o projeto localmente:

- Java 25
- Docker
- Docker Compose

O projeto utiliza Maven Wrapper, portanto não é necessário instalar Maven globalmente.

## Banco de dados

O PostgreSQL é executado localmente através do Docker Compose.

Para iniciar:

```bash
docker compose up -d
```

Para verificar os containers:

```bash
docker compose ps
```

Para parar:

```bash
docker compose down
```

Os dados do PostgreSQL são mantidos em um volume Docker.

## Executando a aplicação

Com o PostgreSQL disponível:

```bash
./mvnw spring-boot:run
```

A aplicação fica disponível por padrão em:

```text
http://localhost:8080
```

## Testes

Para limpar o build, compilar o projeto e executar todos os testes:

```bash
./mvnw clean test
```

Os testes cobrem:

- criação e persistência de installments;
- autorização baseada na propriedade da obra;
- criação e persistência de páginas;
- upload multipart;
- armazenamento físico local;
- geração interna de storage keys;
- prevenção de installments e páginas duplicados;
- publicação de installments;
- impedimento de publicação sem páginas;
- impedimento de alteração após publicação;
- fluxos HTTP 401, 403, 404 e 409 relacionados ao módulo de publicação.
- persistência de entradas da biblioteca;
- disponibilidade da obra através de conteúdo publicado;
- adição autenticada de obras à biblioteca;
- prevenção de entradas duplicadas;
- rejeição de obras sem conteúdo publicado;
- fluxos HTTP 401, 404 e 409 da biblioteca.
- listagem da biblioteca do usuário autenticado;
- resposta vazia para biblioteca sem entradas;
- isolamento de dados entre usuários diferentes;
- rejeição da leitura sem autenticação.
- remoção de obras da biblioteca;
- desaparecimento da obra na listagem após remoção;
- comportamento idempotente do DELETE;
- rejeição da remoção sem autenticação;
- proteção CSRF das operações que alteram estado.
- persistência de progresso;
- criação da primeira posição;
- atualização da posição existente sem criar um novo progresso;
- consulta da última posição registrada;
- integração entre reading e publication através de APIs públicas dos módulos;
- rejeição de páginas inexistentes;
- rejeição de páginas pertencentes a installments ainda não publicados;
- tratamento indistinguível de páginas inexistentes e privadas;
- autenticação para leitura e escrita do progresso;
- proteção CSRF na atualização da posição.
- leitura pública dos detalhes de uma obra;
- acesso à obra publicada sem autenticação;
- ocultação de obras que possuem somente conteúdo em draft;
- listagem somente de installments publicados;
- ocultação de installments draft mesmo quando pertencem a uma obra já pública.

## API atual

### Criar conta

```http
POST /api/users
```

Exemplo:

```json
{
  "username": "andre",
  "email": "andre@example.com",
  "displayName": "André Ward",
  "password": "StrongPassword123!"
}
```

Resposta de sucesso:

```http
201 Created
```

### Obter token CSRF

```http
GET /api/auth/csrf
```

O token deve ser enviado em requisições protegidas por CSRF através do header retornado pela própria API.

### Login

```http
POST /api/auth/login
```

O login atualmente utiliza:

```text
Content-Type: application/x-www-form-urlencoded
```

com os campos:

```text
username
password
```

Após autenticação bem-sucedida, o servidor cria uma sessão HTTP identificada pelo cookie `JSESSIONID`.

### Usuário autenticado

```http
GET /api/auth/me
```

Requer sessão autenticada.

Exemplo de resposta:

```json
{
  "username": "andre"
}
```

### Logout

```http
POST /api/auth/logout
```

### Criar obra

```http
POST /api/works
```

Requer usuário autenticado e token CSRF válido.

Exemplo:

```json
{
  "title": "Metopa Origins",
  "description": "A graphic novel.",
  "type": "GRAPHIC_NOVEL",
  "readingDirection": "LEFT_TO_RIGHT",
  "presentationMode": "SINGLE_PAGE"
}
```

O proprietário da obra não é informado pelo cliente. O Metopa determina o `ownerId` a partir do usuário autenticado.

Resposta de sucesso:

```http
201 Created
```

Exemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

Requer sessão autenticada e token CSRF válido.

O logout invalida a sessão atual.

### Criar installment

```http
POST /api/works/{workId}/installments
```

Requer usuário autenticado e token CSRF válido.

Exemplo:

```json
{
  "type": "CHAPTER",
  "number": 1,
  "title": "The Beginning"
}
```

O installment é criado inicialmente com status `DRAFT`.

O usuário autenticado deve ser proprietário da obra.

### Adicionar página

```http
POST /api/installments/{installmentId}/pages
```

Requer usuário autenticado e token CSRF válido.

A requisição utiliza:

```text
Content-Type: multipart/form-data
```

Campos:

```text
number → número da página
file   → arquivo da página
```

O cliente não informa o local físico do arquivo. O `storageKey` é gerado internamente pelo backend.

### Publicar installment

```http
POST /api/installments/{installmentId}/publish
```

Requer usuário autenticado e token CSRF válido.

Para publicação:

- o usuário deve ser proprietário da obra;
- o installment deve possuir pelo menos uma página;
- o installment deve estar em estado `DRAFT`.

Resposta de sucesso:

```http
204 No Content
```

Após a publicação, o status passa para `PUBLISHED`.

### Consultar installment publicado

```http
GET /api/installments/{installmentId}
```

Endpoint público.

Retorna somente installments com status `PUBLISHED`.

Exemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "workId": "5c4f1d34-7623-4ccf-9ee1-d5bc16d0af61",
  "type": "CHAPTER",
  "number": 1,
  "title": "The Beginning",
  "pages": [
    {
      "id": "26fdb67d-261c-4760-b88a-ec0b0843f9fc",
      "number": 1,
      "contentType": "image/jpeg"
    }
  ]
}
```

Installments em estado `DRAFT` são tratados como não encontrados.

### Ler conteúdo de página publicada

```http
GET /api/pages/{pageId}/content
```

Endpoint público.

Retorna diretamente o conteúdo binário da página com o `Content-Type` correspondente.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/jpeg
```

O `storageKey` não é exposto pela API.

Páginas pertencentes a installments em estado `DRAFT` são tratadas como não encontradas.

### Adicionar obra à biblioteca

```http
POST /api/library/works/{workId}
```

Requer usuário autenticado e token CSRF válido.

Não existe `userId` no corpo da requisição. O proprietário da biblioteca é determinado através da sessão autenticada.

Somente obras que possuam conteúdo publicado podem ser adicionadas.

Resposta de sucesso:

```http
201 Created
```

Exemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

Possíveis respostas:

```text
401 → usuário não autenticado
404 → obra não disponível para biblioteca
409 → obra já presente na biblioteca
```

### Listar biblioteca

```http
GET /api/library
```

Requer usuário autenticado.

A identidade do proprietário da biblioteca é obtida através da sessão autenticada. Não existe `userId` na URL ou nos parâmetros.

Resposta:

```http
200 OK
```

Exemplo:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "workId": "550e8400-e29b-41d4-a716-446655440001",
    "title": "Metopa Origins",
    "type": "GRAPHIC_NOVEL",
    "addedAt": "2026-10-07T19:30:00Z"
  }
]
```

Uma biblioteca vazia retorna:

```json
[]
```

Sem autenticação:

```text
401 → usuário não autenticado
```

### Remover obra da biblioteca

```http
DELETE /api/library/works/{workId}
```

Requer usuário autenticado e token CSRF válido.

A identidade do proprietário da biblioteca é obtida através da sessão autenticada.

Resposta:

```http
204 No Content
```

A operação é idempotente. Caso a obra já não esteja presente na biblioteca do usuário, a resposta continua sendo:

```http
204 No Content
```

Sem autenticação:

```text
401 → usuário não autenticado
```

### Salvar progresso de leitura

```http
PUT /api/reading/progress/pages/{pageId}
```

Requer usuário autenticado e token CSRF válido.

O cliente informa somente a página atual. A obra e o installment são determinados internamente a partir da página publicada.

Se ainda não existir progresso para o usuário e a obra, ele é criado. Caso já exista, a posição existente é atualizada.

Resposta:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
404 → página não disponível para progresso
403 → token CSRF ausente ou inválido
```

### Consultar progresso de leitura

```http
GET /api/reading/progress/works/{workId}
```

Requer usuário autenticado.

Resposta:

```http
200 OK
```

Exemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "workId": "550e8400-e29b-41d4-a716-446655440001",
  "installmentId": "550e8400-e29b-41d4-a716-446655440002",
  "pageId": "550e8400-e29b-41d4-a716-446655440003",
  "updatedAt": "2026-10-09T03:00:00Z"
}
```

Caso ainda não exista progresso para aquela obra:

```http
404 Not Found
```

### Consultar obra publicada

```http
GET /api/works/{workId}
```

Endpoint público. Não requer autenticação.

Uma obra somente é disponibilizada quando possui pelo menos um installment publicado.

Resposta:

```http
200 OK
```

Exemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Metopa Origins",
  "description": "A science fiction graphic novel.",
  "type": "GRAPHIC_NOVEL",
  "readingDirection": "LEFT_TO_RIGHT",
  "presentationMode": "SINGLE_PAGE",
  "installments": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "type": "ISSUE",
      "number": 1,
      "title": "Issue One"
    }
  ]
}
```

Somente installments publicados são retornados. Installments em estado `DRAFT` permanecem invisíveis.

Caso a obra não exista ou ainda não possua conteúdo publicado:

```text
404 → obra não disponível
```

## Migrations

As alterações no schema do banco são controladas pelo Flyway.

As migrations ficam em:

```text
src/main/resources/db/migration/
```

Exemplo:

```text
V1__create_user_account.sql
V2__add_password_to_user_account.sql
V3__create_work.sql
V4__create_installment.sql
V5__create_installment_page.sql
V6__create_library_entry.sql
V7__create_reading_progress.sql
```

Migrations já executadas não devem ser alteradas. Novas mudanças no banco devem ser implementadas através de uma nova migration versionada.

## Segurança

O Metopa utiliza Spring Security.

No estado atual do MVP:

```text
POST /api/users       → público
GET  /api/auth/csrf   → público
POST /api/auth/login  → público

GET  /api/auth/me     → autenticado
POST /api/auth/logout → autenticado
```

Senhas são processadas utilizando `PasswordEncoder` e armazenadas somente como hash.

Respostas de autenticação inválida não revelam se determinado username existe.

Leitura pública atualmente permitida:

```text
GET /api/installments/{installmentId}
GET /api/pages/{pageId}/content
```

## Desenvolvimento

O projeto utiliza branches de feature para mudanças relevantes.

Exemplos:

```text
feature/modulith-architecture
feature/module-boundaries
feature/identity-user
feature/identity-authentication
feature/catalog-work
```

O fluxo utilizado é:

```text
main
  ↓
feature/*
  ↓
commit
  ↓
push
  ↓
pull request
  ↓
merge
```
