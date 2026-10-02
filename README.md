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

Responsável pelo processo de publicação de conteúdo.

Planejado para etapas posteriores do MVP.

### Library

Responsável pela biblioteca pessoal e progresso de leitura dos usuários.

Planejado para etapas posteriores do MVP.

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

Os testes atualmente cobrem, entre outros pontos:

- inicialização do ApplicationContext;
- estrutura do Spring Modulith;
- persistência com JPA e PostgreSQL;
- criação de contas;
- normalização de dados;
- Bean Validation;
- regras de unicidade;
- hash de senha;
- controllers HTTP;
- autenticação;
- sessão;
- CSRF;
- acesso autenticado;
- logout.

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

## Próximos passos

A próxima etapa do MVP é iniciar o módulo `catalog`, começando pela modelagem de uma obra (`Work`) capaz de representar diferentes formatos, como:

- comic;
- mangá;
- graphic novel;
- webtoon;
- tirinha.

Posteriormente serão desenvolvidos os módulos de publicação, leitura, biblioteca e moderação.