# Metopa

Metopa é uma plataforma para publicação, descoberta e leitura de quadrinhos, mangás, graphic novels, webtoons e outros formatos de narrativa visual.

A proposta é permitir que criadores publiquem suas obras e que leitores possam descobrir, acompanhar e consumir esse conteúdo em uma experiência pensada especificamente para quadrinhos.

## Status

Projeto em desenvolvimento — MVP.

Nesta primeira fase, toda a infraestrutura é executada localmente, sem dependência de serviços cloud pagos.

A suíte automatizada possui atualmente **185 testes**, cobrindo regras de domínio, persistência, segurança, APIs HTTP, armazenamento binário e fluxos de integração.

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
├── reading
├── media
└── moderation
```

O Spring Modulith é utilizado para verificar automaticamente as dependências e os limites entre os módulos.

Arquivos binários, como páginas e capas de obras, são armazenados fora do banco de dados. O PostgreSQL mantém apenas os metadados e as referências necessárias para localizar esses arquivos.

O módulo `media` fornece uma abstração genérica de armazenamento binário através de `BinaryStorage`. No MVP, a implementação utiliza o filesystem local, permitindo que o mecanismo físico de armazenamento seja substituído futuramente sem acoplar os demais módulos à infraestrutura.

O `storageKey` é um detalhe interno e não é exposto pela API pública.

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

Responsável pelo catálogo e pelos metadados das obras.

Atualmente suporta:

- criação de obras;
- associação automática da obra ao usuário autenticado;
- atualização dos metadados da obra pelo proprietário;
- listagem das obras pertencentes ao usuário autenticado;
- inclusão de obras ainda não publicadas na visão de gerenciamento do criador;
- isolamento das obras por proprietário;
- ordenação das obras do criador pelas atualizadas mais recentemente;
- diferentes tipos de obra, como comic, mangá, graphic novel, webtoon e tirinha;
- direção de leitura configurável;
- modo de apresentação configurável;
- associação de zero ou mais gêneros a uma obra;
- vocabulário de gêneros definido pelo domínio através de `Genre`;
- persistência dos gêneros em coleção associada à obra;
- substituição dos gêneros durante a atualização dos metadados;
- normalização e validação dos dados da obra;
- persistência das obras no PostgreSQL;
- definição e substituição da capa por parte do proprietário da obra;
- armazenamento apenas da referência e do `Content-Type` da capa no banco;
- leitura interna da capa através do módulo `media`;
- indicação de disponibilidade de capa sem expor o `storageKey`.

Os gêneros disponíveis atualmente são:

```text
ACTION
ADVENTURE
COMEDY
DRAMA
FANTASY
HORROR
MYSTERY
ROMANCE
SCI_FI
SLICE_OF_LIFE
THRILLER
```

Uma obra pode não possuir gêneros ou possuir vários gêneros.

Como os gêneros são representados pelo domínio como um conjunto, sua ordem não faz parte do contrato da aplicação.

### Publication

Responsável pela criação, gerenciamento, publicação e leitura pública do conteúdo das obras.

Atualmente suporta:

- criação de installments associados a uma obra;
- diferentes tipos de installment: chapter, issue, episode e strip;
- título opcional para installments;
- installments criados inicialmente como draft;
- atualização de tipo, número e título de installments em estado `DRAFT`;
- normalização do título durante a atualização;
- validação de propriedade da obra antes de alterações;
- prevenção de installments duplicados por tipo e número;
- prevenção de combinações duplicadas de tipo e número durante a atualização;
- listagem administrativa dos installments de uma obra pelo proprietário;
- exposição conjunta de installments `DRAFT` e `PUBLISHED` na visão do criador;
- exposição explícita do status do installment na visão administrativa;
- isolamento da listagem administrativa por propriedade da obra;
- ordenação dos installments por número e data de criação;
- consulta administrativa dos detalhes de um installment pelo proprietário;
- consulta administrativa de installments em estado `DRAFT` ou `PUBLISHED`;
- consulta das páginas de installments ainda não publicados para gerenciamento;
- validação de propriedade da obra antes da consulta das páginas;
- leitura administrativa do conteúdo binário de páginas pelo proprietário;
- acesso a páginas pertencentes a installments `DRAFT` ou `PUBLISHED` na visão do criador;
- validação de propriedade da obra antes da leitura do conteúdo binário;
- preservação do `Content-Type` ao retornar o conteúdo da página;
- criação e ordenação de páginas;
- upload de páginas através de `multipart/form-data`;
- armazenamento local dos arquivos através do módulo `media`;
- armazenamento de metadados das páginas no PostgreSQL;
- prevenção de números de página duplicados;
- publicação de installments;
- publicação permitida somente quando existe pelo menos uma página;
- bloqueio de alterações em installments já publicados;
- leitura pública de installments publicados;
- páginas retornadas em ordem de leitura;
- leitura pública do conteúdo binário das páginas;
- consulta pública de detalhes de obras que possuem conteúdo publicado;
- descoberta pública de obras publicadas sem duplicação por installment;
- filtragem opcional da descoberta pública por gênero;
- propagação dos gêneros definidos no catálogo para as representações públicas;
- exposição dos gêneros nos endpoints públicos de detalhe e descoberta;
- exposição pública da capa somente quando a obra possui conteúdo publicado;
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

### Media

Responsável pela abstração de armazenamento de conteúdo binário.

Atualmente suporta:

- armazenamento por namespace e identificador do recurso;
- geração interna de `storageKey`;
- leitura de conteúdo binário a partir da chave;
- proteção contra resolução de caminhos fora da raiz configurada;
- implementação local baseada em filesystem;
- uso compartilhado por páginas de installments e capas de obras.

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
- referências entre módulos feitas por identificadores e APIs públicas, evitando relacionamentos JPA entre módulos;
- arquivos binários armazenados fora do PostgreSQL;
- armazenamento binário acessado através de uma abstração compartilhada;
- `storageKey` tratado como detalhe interno de infraestrutura;
- gêneros tratados como vocabulário pertencente ao módulo `catalog`;
- múltiplos gêneros representados como `Set<Genre>`, sem entidade de domínio dedicada;
- alterações dos metadados da obra concentradas em uma operação de domínio;
- alterações dos metadados de installments concentradas em uma operação de domínio;
- separação entre descoberta pública e gerenciamento das obras do criador;
- a listagem das obras do criador pertence ao módulo `catalog` e não depende do estado de publicação;
- separação entre leitura pública de installments e gerenciamento dos installments pelo criador;
- a visão administrativa de installments pertence ao módulo `publication` e pode expor conteúdo ainda em `DRAFT`;
- separação entre a leitura pública de um installment e sua visualização administrativa pelo criador;
- a visão administrativa permite consultar drafts e suas páginas sem expô-los através dos endpoints públicos;
- separação entre a leitura pública do conteúdo de uma página e sua visualização administrativa pelo criador;
- o conteúdo binário de páginas em `DRAFT` somente pode ser lido pelo proprietário da obra através da visão administrativa;
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
│   │   │       ├── reading/
│   │   │       ├── media/
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

Estado atual da suíte:

```text
Tests run: 185
Failures: 0
Errors: 0
Skipped: 0
```

Os testes cobrem, entre outros cenários:

- criação e persistência de obras;
- associação da obra ao usuário autenticado;
- normalização e validação dos dados da obra;
- persistência de múltiplos gêneros por obra;
- criação de obra com gêneros através do fluxo HTTP completo;
- atualização dos metadados de uma obra pelo proprietário;
- persistência da atualização da obra através do fluxo HTTP completo;
- normalização de título e descrição durante a atualização da obra;
- substituição dos gêneros anteriores pelos novos gêneros;
- rejeição de atualização da obra por usuário que não seja proprietário;
- preservação do estado da obra após tentativa de atualização não autorizada;
- rejeição de metadados inválidos sem alteração do estado persistido;
- listagem das obras pertencentes ao usuário autenticado;
- conversão das obras do criador para `WorkSummary`;
- exposição da listagem do criador através do fluxo HTTP;
- inclusão de obras ainda não publicadas na listagem do proprietário;
- isolamento entre obras pertencentes a usuários diferentes;
- integração da consulta por proprietário com persistência real no PostgreSQL;
- propagação de gêneros entre `Work`, `WorkSummary` e `WorkDetails`;
- propagação de gêneros entre os módulos `catalog` e `publication`;
- exposição de gêneros nos endpoints públicos de detalhe e descoberta;
- criação, persistência, publicação e leitura de installments;
- atualização dos metadados de installments em estado `DRAFT`;
- persistência da atualização de installments através do fluxo HTTP completo;
- normalização do título durante a atualização de installments;
- alteração somente de título sem falso conflito de duplicidade;
- rejeição de combinações duplicadas de tipo e número durante a atualização;
- rejeição de alterações em installments publicados;
- preservação dos metadados persistidos após tentativa de alteração de installment publicado;
- listagem administrativa dos installments de uma obra pelo proprietário;
- conversão de installments para `CreatorInstallmentSummary`;
- retorno conjunto de installments `DRAFT` e `PUBLISHED` na visão do criador;
- exposição explícita do status na listagem administrativa;
- rejeição da listagem de installments para usuário que não seja proprietário da obra;
- exposição da listagem administrativa através do fluxo MVC autenticado;
- integração completa da listagem administrativa com sessão real e PostgreSQL;
- preservação da ordem dos installments por número;
- isolamento da visão administrativa entre proprietários diferentes;
- consulta administrativa dos detalhes de installments;
- consulta de installments em estado `DRAFT` pelo proprietário;
- exposição de status e páginas através da visão administrativa;
- rejeição da consulta de detalhes por usuário que não seja proprietário da obra;
- impedimento da consulta das páginas quando a validação de propriedade falha;
- exposição do detalhe administrativo através do fluxo MVC autenticado;
- integração completa do detalhe administrativo com sessão real e PostgreSQL;
- leitura administrativa do conteúdo de páginas;
- leitura de página pertencente a installment `DRAFT` pelo proprietário;
- rejeição da leitura de página por usuário que não seja proprietário da obra;
- impedimento da leitura do `BinaryStorage` quando a validação de propriedade falha;
- exposição do conteúdo binário através do fluxo MVC autenticado;
- integração completa da leitura administrativa com sessão real, PostgreSQL e armazenamento local;
- autorização baseada na propriedade da obra;
- criação, ordenação e persistência de páginas;
- upload multipart;
- armazenamento físico local e geração interna de `storageKey`;
- prevenção de installments e páginas duplicados;
- impedimento de publicação sem páginas;
- impedimento de alteração de installments publicados;
- autenticação, sessão HTTP e proteção CSRF;
- fluxos HTTP 401, 403, 404 e 409;
- persistência, listagem e remoção idempotente de entradas da biblioteca;
- isolamento da biblioteca entre usuários;
- persistência, atualização e consulta do progresso de leitura;
- rejeição de páginas inexistentes ou ainda não publicadas no progresso;
- leitura pública dos detalhes de obras publicadas;
- ocultação de obras que possuem somente conteúdo em draft na descoberta pública;
- listagem somente de installments publicados na visão pública;
- descoberta pública de obras sem duplicação;
- filtragem de obras publicadas por gênero;
- integração completa do filtro de gênero com persistência real no PostgreSQL;
- exclusão de obras draft mesmo quando possuem o gênero filtrado;
- rejeição de valores inválidos no filtro de gênero;
- persistência de referências de capa;
- armazenamento e leitura real dos bytes da capa;
- rejeição de upload de capa por usuário que não seja proprietário;
- exigência de autenticação e CSRF no upload da capa;
- ocultação pública da capa de obras ainda não publicadas;
- leitura pública da capa de obras publicadas;
- propagação de `hasCover` entre `catalog` e `publication`;
- exposição de `coverUrl` nos endpoints públicos de detalhe e descoberta.

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

O token deve ser enviado nas requisições protegidas por CSRF através do header informado pela própria API.

### Login

```http
POST /api/auth/login
```

O login utiliza:

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

Requer sessão autenticada.

O logout invalida a sessão atual.

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
  "presentationMode": "SINGLE_PAGE",
  "genres": [
    "SCI_FI",
    "ADVENTURE"
  ]
}
```

O proprietário da obra não é informado pelo cliente. O Metopa determina o `ownerId` a partir do usuário autenticado.

O campo `genres` é opcional no MVP. Quando omitido, a obra é criada sem gêneros associados.

Os valores aceitos atualmente são:

```text
ACTION
ADVENTURE
COMEDY
DRAMA
FANTASY
HORROR
MYSTERY
ROMANCE
SCI_FI
SLICE_OF_LIFE
THRILLER
```

Valores duplicados não representam gêneros adicionais, pois o domínio mantém os gêneros da obra como um conjunto.

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

### Atualizar metadados da obra

```http
PUT /api/works/{workId}
```

Requer usuário autenticado e token CSRF válido.

Somente o proprietário da obra pode atualizar seus metadados.

A operação utiliza `PUT`: a requisição representa o novo estado completo dos metadados editáveis da obra.

Exemplo:

```json
{
  "title": "Metopa Origins Revised",
  "description": "Updated description.",
  "type": "GRAPHIC_NOVEL",
  "readingDirection": "LEFT_TO_RIGHT",
  "presentationMode": "SINGLE_PAGE",
  "genres": [
    "SCI_FI",
    "ADVENTURE"
  ]
}
```

Os campos editáveis são:

```text
title
description
type
readingDirection
presentationMode
genres
```

O identificador da obra, o proprietário, a capa e a data de criação não são modificados por esse endpoint.

`title`, `type`, `readingDirection` e `presentationMode` são obrigatórios.

`description` pode ser omitida ou informada em branco. Após normalização, uma descrição vazia é armazenada como ausência de descrição.

O campo `genres` pode ser omitido. Nesse caso, a obra passa a não possuir gêneros associados.

Os gêneros previamente associados são substituídos pelo conjunto informado na requisição.

Título e descrição são normalizados antes da persistência.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → um ou mais metadados são inválidos
401 → usuário não autenticado
403 → token CSRF ausente ou inválido, ou usuário autenticado não é proprietário da obra
```

### Listar obras do criador

```http
GET /api/me/works
```

Requer usuário autenticado.

Retorna as obras pertencentes ao usuário da sessão, independentemente de possuírem conteúdo publicado.

O cliente não informa `userId`. A identidade do proprietário é determinada a partir da sessão autenticada.

Exemplo de resposta:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Metopa Origins",
    "type": "GRAPHIC_NOVEL",
    "genres": [
      "SCI_FI",
      "ADVENTURE"
    ],
    "hasCover": true
  }
]
```

A resposta pode incluir:

- obras sem installments;
- obras que possuem somente installments em estado `DRAFT`;
- obras com conteúdo publicado.

Somente obras pertencentes ao usuário autenticado são retornadas.

As obras são retornadas das atualizadas mais recentemente para as menos recentes.

A ordem dos elementos de `genres` não faz parte do contrato da API.

Quando o usuário ainda não possui obras:

```json
[]
```

Sem autenticação:

```text
401 → usuário não autenticado
```

A visão de gerenciamento do criador é independente da descoberta pública:

```text
GET /api/works
→ descoberta pública
→ somente obras com conteúdo publicado

GET /api/me/works
→ gerenciamento do criador
→ todas as obras pertencentes ao usuário autenticado
```

### Definir capa da obra

```http
PUT /api/works/{workId}/cover
```

Requer usuário autenticado e token CSRF válido.

Somente o proprietário da obra pode definir a capa.

A requisição utiliza:

```text
Content-Type: multipart/form-data
```

Campo:

```text
file → arquivo da capa
```

O cliente não informa o local físico do arquivo. O `storageKey` é gerado internamente pelo backend através do módulo `media`.

Resposta de sucesso:

```http
204 No Content
```

### Ler capa de obra publicada

```http
GET /api/works/{workId}/cover
```

Endpoint público.

Retorna diretamente o conteúdo binário da capa com o `Content-Type` correspondente.

A capa somente é disponibilizada publicamente quando:

- a obra possui uma capa;
- a obra possui pelo menos um installment publicado.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/webp
```

Uma obra sem capa ou sem conteúdo publicado retorna:

```text
404 → capa da obra não disponível
```

O `storageKey` não é exposto pela API.

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

### Listar installments da obra para o criador

```http
GET /api/me/works/{workId}/installments
```

Requer usuário autenticado.

Retorna todos os installments da obra quando o usuário autenticado é seu proprietário.

A listagem inclui installments em estado `DRAFT` e `PUBLISHED`.

O cliente não informa `userId`. A identidade do usuário é determinada a partir da sessão autenticada e a propriedade da obra é validada pelo backend.

Exemplo de resposta:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "type": "CHAPTER",
    "number": 1,
    "title": "Chapter One",
    "status": "PUBLISHED"
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "type": "CHAPTER",
    "number": 2,
    "title": "Chapter Two",
    "status": "DRAFT"
  }
]
```

Cada item contém:

```text
id
type
number
title
status
```

O `workId` não é repetido em cada item porque a obra já é identificada pelo recurso solicitado.

Os installments são ordenados por número crescente e, em caso de empate, pela data de criação.

Quando a obra não possui installments:

```json
[]
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário autenticado não é proprietário da obra
404 → obra não encontrada
```

A visão administrativa é distinta da leitura pública:

```text
GET /api/works/{workId}
→ visão pública
→ somente installments PUBLISHED

GET /api/me/works/{workId}/installments
→ gerenciamento do criador
→ installments DRAFT + PUBLISHED
→ status explícito
```

### Consultar installment para o criador

```http
GET /api/me/installments/{installmentId}
```

Requer usuário autenticado.

Retorna os detalhes de um installment pertencente a uma obra do usuário autenticado.

Diferentemente do endpoint público, a consulta administrativa permite installments nos estados `DRAFT` e `PUBLISHED`.

O cliente não informa `userId`. A identidade do usuário é determinada através da sessão autenticada e a propriedade da obra é validada antes da consulta das páginas.

Exemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "workId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CHAPTER",
  "number": 1,
  "title": "Chapter One",
  "status": "DRAFT",
  "pages": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "number": 1,
      "contentType": "image/jpeg"
    }
  ]
}
```

A resposta contém:

```text
id
workId
type
number
title
status
pages
```

Cada página expõe:

```text
id
number
contentType
```

O `storageKey` não é exposto.

As páginas são retornadas em ordem crescente de número.

Um installment em estado `DRAFT` pode ser consultado através desta visão administrativa, mas continua indisponível através dos endpoints públicos.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário autenticado não é proprietário da obra
404 → installment não encontrado
```

A visão administrativa e a leitura pública possuem contratos distintos:

```text
GET /api/installments/{installmentId}
→ leitura pública
→ somente PUBLISHED
→ status não é exposto

GET /api/me/installments/{installmentId}
→ gerenciamento do criador
→ DRAFT ou PUBLISHED
→ status explícito
→ páginas disponíveis para gerenciamento
```

### Atualizar metadados do installment

```http
PUT /api/installments/{installmentId}
```

Requer usuário autenticado e token CSRF válido.

Somente o proprietário da obra pode atualizar os metadados do installment.

A atualização é permitida somente enquanto o installment estiver em estado `DRAFT`.

A operação utiliza `PUT`: a requisição representa o novo estado completo dos metadados editáveis do installment.

Exemplo:

```json
{
  "type": "ISSUE",
  "number": 2,
  "title": "Issue Two"
}
```

Os campos editáveis são:

```text
type
number
title
```

O campo `type` é obrigatório.

O campo `number` deve ser maior que zero.

O campo `title` é opcional. Espaços nas extremidades são removidos e um título vazio é normalizado para ausência de título.

A combinação de `type` e `number` deve ser única dentro da mesma obra.

Quando apenas o título é alterado, o installment pode manter seu `type` e `number` atuais sem entrar em conflito consigo mesmo.

O identificador do installment, a obra associada, o status e a data de criação não são modificados por esse endpoint.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → um ou mais campos são inválidos
401 → usuário não autenticado
403 → token CSRF ausente ou inválido, ou usuário não é proprietário da obra
404 → installment não encontrado
409 → combinação de tipo e número já existe para a obra
409 → installment já publicado e não pode ser modificado
```

Installments em estado `PUBLISHED` não podem ter seus metadados modificados.

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

### Ler conteúdo de página para o criador

```http
GET /api/me/pages/{pageId}/content
```

Requer usuário autenticado.

Retorna diretamente o conteúdo binário de uma página pertencente a uma obra do usuário autenticado.

Diferentemente do endpoint público, a leitura administrativa permite páginas pertencentes a installments em estado `DRAFT` ou `PUBLISHED`.

O cliente não informa `userId`. A identidade do usuário é determinada através da sessão autenticada.

A propriedade da obra é validada antes que o conteúdo seja lido do armazenamento binário.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/jpeg

<conteúdo binário da página>
```

O `storageKey` não é exposto.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário autenticado não é proprietário da obra
404 → página não encontrada
```

A leitura administrativa e a leitura pública possuem contratos distintos:

```text
GET /api/pages/{pageId}/content
→ leitura pública
→ somente páginas de installments PUBLISHED

GET /api/me/pages/{pageId}/content
→ gerenciamento do criador
→ páginas de installments DRAFT ou PUBLISHED
→ somente proprietário
```

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
  "genres": [
    "SCI_FI",
    "ADVENTURE"
  ],
  "coverUrl": "/api/works/550e8400-e29b-41d4-a716-446655440000/cover",
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

Quando a obra não possui gêneros associados:

```json
{
  "genres": []
}
```

A ordem dos elementos de `genres` não deve ser considerada parte do contrato da API.

Quando a obra não possui capa, `coverUrl` é `null`.

Somente installments publicados são retornados. Installments em estado `DRAFT` permanecem invisíveis.

Caso a obra não exista ou ainda não possua conteúdo publicado:

```text
404 → obra não disponível
```

### Listar obras publicadas

```http
GET /api/works
```

Endpoint público. Não requer autenticação.

Retorna as obras que possuem pelo menos um installment publicado.

A listagem pode ser filtrada opcionalmente por gênero:

```http
GET /api/works?genre=SCI_FI
```

Quando `genre` é informado, somente obras publicadas associadas ao gênero solicitado são retornadas.

Os valores aceitos atualmente são:

```text
ACTION
ADVENTURE
COMEDY
DRAMA
FANTASY
HORROR
MYSTERY
ROMANCE
SCI_FI
SLICE_OF_LIFE
THRILLER
```

O filtro utiliza os valores exatos definidos pelo enum `Genre`. Valores desconhecidos são rejeitados com:

```http
400 Bad Request
```

Exemplo de resposta:

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Metopa Origins",
    "type": "GRAPHIC_NOVEL",
    "genres": [
      "SCI_FI",
      "ADVENTURE"
    ],
    "coverUrl": "/api/works/550e8400-e29b-41d4-a716-446655440000/cover"
  }
]
```

Sem o parâmetro `genre`, o comportamento permanece o mesmo e todas as obras publicadas são retornadas.

Uma obra aparece apenas uma vez, mesmo que possua vários installments publicados.

Obras que possuem o gênero solicitado, mas não possuem nenhum installment publicado, continuam invisíveis na descoberta pública.

A ordem dos gêneros de cada obra não deve ser utilizada pelo cliente como informação semântica.

Caso nenhuma obra publicada corresponda ao filtro:

```json
[]
```

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
403 → token CSRF ausente ou inválido
404 → página não disponível para progresso
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

## Migrations

As alterações no schema do banco são controladas pelo Flyway.

As migrations ficam em:

```text
src/main/resources/db/migration/
```

Migrations atuais:

```text
V1__create_user_account.sql
V2__add_password_to_user_account.sql
V3__create_work.sql
V4__create_installment.sql
V5__create_installment_page.sql
V6__create_library_entry.sql
V7__create_reading_progress.sql
V8__add_work_cover.sql
V9__create_work_genre.sql
```

A migration `V9__create_work_genre.sql` introduz a tabela `work_genre`, utilizada para persistir múltiplos gêneros associados a uma obra.

A relação utiliza o identificador da obra e o valor do gênero, mantendo a associação dentro do módulo `catalog`.

Migrations já executadas não devem ser alteradas. Novas mudanças no banco devem ser implementadas através de uma nova migration versionada.

## Segurança

O Metopa utiliza Spring Security com autenticação baseada em sessão HTTP e proteção CSRF habilitada.

Principais endpoints públicos:

```text
POST /api/users
GET  /api/auth/csrf
POST /api/auth/login

GET  /api/installments/{installmentId}
GET  /api/pages/{pageId}/content
GET  /api/works
GET  /api/works/{workId}
GET  /api/works/{workId}/cover
```

Principais endpoints autenticados:

```text
GET    /api/auth/me
POST   /api/auth/logout

POST   /api/works
PUT    /api/works/{workId}
PUT    /api/works/{workId}/cover
POST   /api/works/{workId}/installments

GET    /api/me/works
GET    /api/me/works/{workId}/installments
GET    /api/me/installments/{installmentId}
GET    /api/me/pages/{pageId}/content

PUT    /api/installments/{installmentId}
POST   /api/installments/{installmentId}/pages
POST   /api/installments/{installmentId}/publish

GET    /api/library
POST   /api/library/works/{workId}
DELETE /api/library/works/{workId}

GET    /api/reading/progress/works/{workId}
PUT    /api/reading/progress/pages/{pageId}
```

Operações que alteram estado permanecem protegidas por CSRF.

Senhas são processadas utilizando `PasswordEncoder` e armazenadas somente como hash.

Respostas de autenticação inválida não revelam se determinado username existe.

## Desenvolvimento

O projeto utiliza branches de feature e refactor para mudanças relevantes.

Exemplos:

```text
feature/modulith-architecture
feature/module-boundaries
feature/identity-user
feature/identity-authentication
feature/catalog-work
refactor/media-storage
feature/work-cover
feature/work-genres
feature/work-discovery-filtering
feature/work-metadata-update
feature/installment-metadata-update
feature/creator-work-list
feature/creator-installment-list
feature/creator-installment-detail
feature/creator-page-content
```

O fluxo utilizado é:

```text
main
  ↓
feature/* ou refactor/*
  ↓
commit
  ↓
push
  ↓
pull request
  ↓
merge
```