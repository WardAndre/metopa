# Metopa — API HTTP

Documentação dos endpoints HTTP disponíveis no MVP do Metopa.

A API permite gerenciar contas, autenticação, obras, installments, páginas, biblioteca pessoal e progresso de leitura.

Este documento descreve os contratos implementados atualmente. Os exemplos utilizam identificadores UUID e dados ilustrativos.

## Visão geral

URL base da aplicação local:

```text
http://localhost:8080
```

Os endpoints utilizam o prefixo `/api`.

A API distingue operações públicas de leitura e operações administrativas ou pessoais que exigem autenticação.

### Autenticação

O Metopa utiliza Spring Security com autenticação baseada em sessão HTTP.

Após o login, a sessão é identificada pelo cookie `JSESSIONID`.

Endpoints protegidos exigem uma sessão autenticada.

### Proteção CSRF

Operações que alteram estado exigem um token CSRF válido quando protegidas por essa configuração de segurança.

O token pode ser obtido através de:

```http
GET /api/auth/csrf
```

O cliente deve enviar o token nas requisições protegidas utilizando o header informado pela API.

### Identificadores

As principais entidades utilizam UUIDs:

- `userId`: usuário;
- `workId`: obra;
- `installmentId`: installment;
- `pageId`: página.

Os identificadores relacionados à identidade do usuário autenticado são obtidos internamente a partir da sessão, quando aplicável.

O cliente não deve informar o proprietário de uma obra ou de uma biblioteca.

### Códigos HTTP

Os contratos utilizam, entre outros:

| Código | Significado |
|--------|-------------|
| `200 OK` | Consulta bem-sucedida |
| `201 Created` | Recurso criado |
| `204 No Content` | Operação concluída sem corpo de resposta |
| `400 Bad Request` | Dados inválidos |
| `401 Unauthorized` | Autenticação necessária |
| `403 Forbidden` | Operação não autorizada ou CSRF inválido |
| `404 Not Found` | Recurso não encontrado ou indisponível |
| `409 Conflict` | Conflito com uma regra de negócio |

A indicação de possíveis respostas em cada endpoint não substitui todos os erros gerais que podem ocorrer durante o processamento HTTP.

---

## 1. Identity — Contas e autenticação

### 1.1. Criar conta

```http
POST /api/users
```

Cria uma conta de usuário.

Exemplo:

```json
{
  "username": "andre",
  "email": "andre@example.com",
  "displayName": "André Ward",
  "password": "StrongPassword123!"
}
```

Regras:

- `username`, `email`, `displayName` e `password` devem atender às validações da aplicação;
- username e e-mail são normalizados;
- username e e-mail devem ser únicos;
- o UUID da conta é gerado pelo domínio;
- a senha é armazenada como hash bcrypt, nunca em texto puro.

Resposta de sucesso:

```http
201 Created
```

### 1.2. Obter token CSRF

```http
GET /api/auth/csrf
```

Obtém as informações necessárias para o envio do token CSRF em requisições protegidas.

O header utilizado deve corresponder ao informado pela API.

### 1.3. Login

```http
POST /api/auth/login
```

A requisição utiliza:

```text
Content-Type: application/x-www-form-urlencoded
```

Campos:

```text
username
password
```

Após autenticação bem-sucedida, o servidor estabelece uma sessão HTTP identificada pelo cookie `JSESSIONID`.

As mensagens de falha de autenticação não revelam se determinado username existe.

### 1.4. Consultar usuário autenticado

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

Possível resposta:

```text
401 → usuário não autenticado
```

### 1.5. Logout

```http
POST /api/auth/logout
```

Requer sessão autenticada.

O logout invalida a sessão atual.

---

## 2. Catalog — Obras

### 2.1. Criar obra

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

O campo `genres` é opcional. Quando omitido, a obra é criada sem gêneros associados.

Os valores disponíveis atualmente são:

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

### 2.2. Atualizar metadados da obra

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

Campos editáveis:

```text
title
description
type
readingDirection
presentationMode
genres
```

Regras:

- `title`, `type`, `readingDirection` e `presentationMode` são obrigatórios;
- `description` pode ser omitida ou informada em branco;
- uma descrição vazia é normalizada para ausência de descrição;
- `genres` pode ser omitido, deixando a obra sem gêneros;
- os gêneros anteriores são substituídos pelo conjunto informado;
- título e descrição são normalizados antes da persistência.

O identificador da obra, o proprietário, a capa e a data de criação não são modificados.

A atualização utiliza bloqueio pessimista sobre a obra para coordenar alterações concorrentes de metadados e capas.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → um ou mais metadados são inválidos
401 → usuário não autenticado
403 → token CSRF ausente ou inválido, ou usuário não é proprietário da obra
404 → obra não encontrada
```

### 2.3. Listar obras do criador

```http
GET /api/me/works
```

Requer sessão autenticada.

Retorna as obras pertencentes ao usuário autenticado, independentemente da existência de conteúdo publicado.

O cliente não informa `userId`.

Exemplo:

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

A listagem pode incluir:

- obras sem installments;
- obras com apenas installments `DRAFT`;
- obras com conteúdo publicado.

Somente obras pertencentes ao usuário autenticado são retornadas.

A ordenação considera as obras atualizadas mais recentemente primeiro.

A ordem dos elementos de `genres` não faz parte do contrato da API.

Uma listagem vazia retorna:

```json
[]
```

Possível resposta:

```text
401 → usuário não autenticado
```

A consulta administrativa é independente da descoberta pública:

```text
GET /api/works
→ descoberta pública
→ somente obras com conteúdo publicado

GET /api/me/works
→ gerenciamento do criador
→ todas as obras do proprietário autenticado
```

### 2.4. Consultar obra para o criador

```http
GET /api/me/works/{workId}
```

Requer sessão autenticada.

Retorna os metadados completos de uma obra pertencente ao usuário autenticado.

A consulta permite obras sem installments ou com apenas conteúdo `DRAFT`.

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
  "hasCover": true
}
```

A resposta contém:

```text
id
title
description
type
readingDirection
presentationMode
genres
hasCover
```

O `storageKey` da capa não é exposto.

A ordem dos gêneros não representa informação semântica.

Quando uma capa é removida, `hasCover` passa a ser `false`.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário da obra
404 → obra não encontrada
```

Diferença entre as consultas:

```text
GET /api/works/{workId}
→ visão pública
→ requer conteúdo publicado

GET /api/me/works/{workId}
→ visão do proprietário
→ independe do estado de publicação
```

### 2.5. Definir capa da obra

```http
PUT /api/works/{workId}/cover
```

Requer usuário autenticado e token CSRF válido.

Somente o proprietário da obra pode definir ou substituir sua capa.

A requisição utiliza:

```text
Content-Type: multipart/form-data
```

Campo:

```text
file → arquivo da capa
```

O cliente não informa o caminho físico ou o `storageKey`.

A chave de armazenamento é gerada internamente através do módulo `media`.

Quando uma capa existente é substituída:

1. o backend adquire um bloqueio pessimista na linha da obra;
2. a propriedade da obra é validada;
3. o novo arquivo é armazenado através de `BinaryStorage`;
4. a nova referência é persistida no PostgreSQL;
5. após o commit, o arquivo da capa anterior é removido.

Se a transação sofrer rollback, a referência anterior permanece preservada e o novo arquivo é removido através de compensação transacional.

Quando a obra ainda não possui capa, o novo arquivo é preservado após a confirmação da transação.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → token CSRF ausente ou inválido, ou usuário não é proprietário da obra
404 → obra não encontrada
```

A coordenação transacional não representa uma transação distribuída entre PostgreSQL e filesystem.

### 2.6. Remover capa da obra

```http
DELETE /api/works/{workId}/cover
```

Requer usuário autenticado e token CSRF válido.

Somente o proprietário da obra pode remover sua capa.

A operação remove a referência da capa no PostgreSQL e agenda a exclusão do arquivo físico para depois da confirmação da transação.

O cliente não precisa informar o `storageKey` ou qualquer identificador do arquivo.

Quando a obra possui uma capa:

1. o backend localiza e bloqueia a obra utilizando `PESSIMISTIC_WRITE`;
2. a propriedade da obra é validada;
3. os campos `coverStorageKey` e `coverContentType` são removidos da entidade;
4. a alteração é persistida na transação PostgreSQL;
5. após o commit, o arquivo físico é excluído através de `BinaryStorage`.

Se a transação sofrer rollback, a referência anterior e o arquivo físico permanecem preservados.

A operação é **idempotente**.

Quando a obra existe, mas não possui capa, a remoção continua sendo considerada bem-sucedida.

Chamadas repetidas à operação retornam a mesma resposta de sucesso, desde que a obra continue existindo e o usuário tenha autorização.

Resposta de sucesso:

```http
204 No Content
```

A resposta não possui corpo.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → token CSRF ausente ou inválido, ou usuário não é proprietário da obra
404 → obra não encontrada
```

A remoção da capa não exclui a obra, seus metadados, installments ou páginas.

Após uma remoção confirmada:

- a consulta administrativa da obra apresenta `hasCover: false`;
- a leitura administrativa da capa deixa de disponibilizar o arquivo;
- a leitura pública da capa deixa de disponibilizar o arquivo;
- nas representações públicas que disponibilizam `coverUrl`, o valor passa a ser `null` para obras sem capa.

A remoção é coordenada com substituições de capas e atualizações de metadados através do bloqueio pessimista da entidade `Work`.

A exclusão física utiliza `TransactionSynchronization.afterCommit()`.

Como PostgreSQL e filesystem não compartilham uma transação atômica, falhas na limpeza após o commit podem deixar arquivos órfãos.

### 2.7. Ler capa para o criador

```http
GET /api/me/works/{workId}/cover
```

Requer sessão autenticada.

Retorna diretamente o conteúdo binário da capa quando o usuário é proprietário da obra.

A leitura administrativa independe de a obra possuir conteúdo publicado.

A propriedade da obra é validada antes da leitura no armazenamento binário.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/webp

<conteúdo binário da capa>
```

O `storageKey` não é exposto.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário da obra
404 → obra ou capa não encontrada
```

Após a remoção da capa, este endpoint retorna `404` enquanto não existir uma nova capa.

### 2.8. Ler capa publicamente

```http
GET /api/works/{workId}/cover
```

Endpoint público.

Retorna os bytes da capa com seu `Content-Type`.

A capa somente é disponibilizada publicamente quando:

- a obra possui uma capa;
- a obra possui pelo menos um installment publicado.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/webp
```

Possível resposta:

```text
404 → capa não disponível
```

O `storageKey` não é exposto.

A remoção da capa não altera o estado de publicação dos installments.

Uma obra que continua possuindo conteúdo publicado permanece disponível publicamente, mesmo sem capa.

---

## 3. Publication — Installments

### 3.1. Criar installment

```http
POST /api/works/{workId}/installments
```

Requer usuário autenticado e token CSRF válido.

O usuário deve ser proprietário da obra.

Exemplo:

```json
{
  "type": "CHAPTER",
  "number": 1,
  "title": "The Beginning"
}
```

Tipos disponíveis:

```text
CHAPTER
ISSUE
EPISODE
STRIP
```

O installment é criado inicialmente com status `DRAFT`.

A combinação de tipo e número deve ser única dentro da obra.

### 3.2. Listar installments para o criador

```http
GET /api/me/works/{workId}/installments
```

Requer sessão autenticada e propriedade da obra.

Retorna installments `DRAFT` e `PUBLISHED`.

Exemplo:

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

Cada elemento contém:

```text
id
type
number
title
status
```

Os installments são ordenados por número crescente e, em caso de empate, pela data de criação.

Uma obra sem installments retorna:

```json
[]
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário da obra
404 → obra não encontrada
```

Essa consulta é diferente da leitura pública:

```text
GET /api/works/{workId}
→ somente installments publicados

GET /api/me/works/{workId}/installments
→ installments DRAFT e PUBLISHED
→ status explícito
```

### 3.3. Consultar installment para o criador

```http
GET /api/me/installments/{installmentId}
```

Requer sessão autenticada.

Retorna um installment pertencente a uma obra do usuário autenticado.

Permite consultar installments `DRAFT` e `PUBLISHED`, incluindo suas páginas.

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

Cada página contém:

```text
id
number
contentType
```

As páginas são ordenadas por número crescente.

O `storageKey` não é exposto.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário da obra
404 → installment não encontrado
```

### 3.4. Atualizar metadados do installment

```http
PUT /api/installments/{installmentId}
```

Requer sessão autenticada, token CSRF válido e propriedade da obra.

A atualização é permitida somente enquanto o installment estiver em estado `DRAFT`.

A operação representa o estado completo dos metadados editáveis.

Exemplo:

```json
{
  "type": "ISSUE",
  "number": 2,
  "title": "Issue Two"
}
```

Campos editáveis:

```text
type
number
title
```

Regras:

- `type` é obrigatório;
- `number` deve ser maior que zero;
- `title` é opcional;
- espaços nas extremidades do título são removidos;
- título vazio é normalizado para ausência de título;
- a combinação de tipo e número deve ser única dentro da obra;
- alterar apenas o título não causa conflito com o próprio installment.

O identificador, a obra associada, o status e a data de criação não são modificados.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → campos inválidos
401 → usuário não autenticado
403 → CSRF inválido ou usuário não é proprietário
404 → installment não encontrado
409 → combinação de tipo e número duplicada
409 → installment já publicado
```

### 3.5. Excluir installment em draft

```http
DELETE /api/installments/{installmentId}
```

Requer sessão autenticada, token CSRF válido e propriedade da obra.

A exclusão é permitida somente para installments `DRAFT`, inclusive quando não possuem páginas.

Installments `PUBLISHED` não podem ser excluídos.

Quando a operação é válida:

1. o backend adquire um bloqueio pessimista na linha do installment;
2. a propriedade da obra é validada antes do estado de publicação;
3. as referências internas dos arquivos das páginas são coletadas;
4. o registro do installment é removido no PostgreSQL;
5. os registros das páginas são excluídos pelo relacionamento com `ON DELETE CASCADE`;
6. após o commit, os arquivos físicos são removidos através de `BinaryStorage`.

Se ocorrer rollback, os registros e arquivos existentes permanecem preservados.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → CSRF inválido ou usuário não é proprietário
404 → installment não encontrado
409 → installment publicado
```

A autorização é verificada antes de revelar o estado de publicação.

O `storageKey` não é exposto ao cliente.

A limpeza física posterior ao commit não é uma operação distribuída com o PostgreSQL. Falhas após o commit podem deixar arquivos órfãos.

### 3.6. Publicar installment

```http
POST /api/installments/{installmentId}/publish
```

Requer usuário autenticado e token CSRF válido.

Regras:

- o usuário deve ser proprietário da obra;
- o installment deve estar em estado `DRAFT`;
- o installment deve possuir pelo menos uma página.

Resposta de sucesso:

```http
204 No Content
```

Após a publicação, o status passa para `PUBLISHED`.

Installments publicados permanecem imutáveis no MVP.

### 3.7. Consultar installment publicado

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

As páginas são retornadas em ordem de leitura.

Installments `DRAFT` são tratados como não encontrados.

A resposta pública não expõe o status nem o `storageKey`.

---

## 4. Publication — Páginas

### 4.1. Adicionar página

```http
POST /api/installments/{installmentId}/pages
```

Requer sessão autenticada, token CSRF válido e propriedade da obra.

A requisição utiliza:

```text
Content-Type: multipart/form-data
```

Campos:

```text
number → número da página
file   → arquivo da página
```

Regras:

- o installment deve estar em estado `DRAFT`;
- o número da página deve ser único no installment;
- o arquivo é armazenado através do módulo `media`;
- o `storageKey` é gerado internamente;
- os metadados são persistidos no PostgreSQL.

O upload inclui compensação transacional.

Após o armazenamento físico, o serviço registra um callback através de `TransactionSynchronization.afterCompletion()`.

Se a transação sofrer rollback, o arquivo recém-criado é removido por `BinaryStorage`.

Se a transação for confirmada, o arquivo é preservado.

Resposta de sucesso:

```http
201 Created
```

A compensação protege contra rollbacks transacionais normais. Encerramento abrupto da aplicação e falhas de limpeza física ainda podem produzir arquivos órfãos.

### 4.2. Excluir página em draft

```http
DELETE /api/installments/{installmentId}/pages/{pageId}
```

Requer sessão autenticada, token CSRF válido e propriedade da obra.

Regras:

- o installment deve estar em estado `DRAFT`;
- a página deve pertencer ao installment informado;
- installments `PUBLISHED` não permitem exclusão de páginas.

Quando a operação é válida:

1. o registro da página é removido na transação PostgreSQL;
2. a alteração é enviada ao banco por `flush()`;
3. após o commit, o arquivo físico é removido através de `BinaryStorage`.

Se houver rollback, o arquivo permanece preservado.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → CSRF inválido ou usuário não é proprietário
404 → installment ou página não encontrada, ou associação inválida
409 → installment já publicado
```

A exclusão não renumera automaticamente as páginas restantes.

Para eliminar lacunas, utilize o endpoint de reordenação.

### 4.3. Reordenar páginas

```http
PUT /api/installments/{installmentId}/pages/order
```

Requer sessão autenticada, token CSRF válido e propriedade da obra.

Permitido somente para installments `DRAFT`.

A requisição utiliza:

```text
Content-Type: application/json
```

Exemplo:

```json
{
  "pageIds": [
    "11111111-1111-1111-1111-111111111111",
    "22222222-2222-2222-2222-222222222222",
    "33333333-3333-3333-3333-333333333333"
  ]
}
```

Os UUIDs são ilustrativos e devem corresponder às páginas existentes.

Regras:

- `pageIds` é obrigatório e não pode estar vazio;
- todos os elementos devem ser UUIDs válidos e não nulos;
- todas as páginas do installment devem aparecer exatamente uma vez;
- IDs duplicados, ausentes ou externos são rejeitados;
- a ordem dos IDs define a nova ordem de leitura;
- os números são atribuídos sequencialmente a partir de `1`;
- lacunas anteriores são eliminadas;
- conteúdo binário, `Content-Type` e arquivos físicos não são alterados;
- installments publicados não podem ser reordenados.

A renumeração ocorre em duas fases, com `flush()` intermediário, preservando a restrição de unicidade de número por installment no PostgreSQL.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → JSON ou ordem inválida
401 → usuário não autenticado
403 → CSRF inválido ou usuário não é proprietário
404 → installment não encontrado
409 → installment publicado
```

A operação utiliza bloqueio pessimista na linha do installment para coordenar alterações concorrentes.

### 4.4. Ler conteúdo de página para o criador

```http
GET /api/me/pages/{pageId}/content
```

Requer sessão autenticada.

Permite ao proprietário ler páginas de installments `DRAFT` ou `PUBLISHED`.

A propriedade da obra é validada antes do acesso ao armazenamento binário.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/jpeg

<conteúdo binário da página>
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário da obra
404 → página não encontrada
```

O `storageKey` não é exposto.

### 4.5. Ler conteúdo de página publicada

```http
GET /api/pages/{pageId}/content
```

Endpoint público.

Retorna diretamente os bytes da página com o `Content-Type` correspondente.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/jpeg
```

Somente páginas pertencentes a installments publicados estão disponíveis.

Páginas em `DRAFT` são tratadas como não encontradas.

O `storageKey` não é exposto.

---

## 5. Descoberta pública

### 5.1. Consultar obra publicada

```http
GET /api/works/{workId}
```

Endpoint público.

Uma obra somente é disponibilizada quando possui pelo menos um installment publicado.

Resposta de sucesso:

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

Somente installments publicados são retornados.

Quando não há gêneros, a coleção é vazia:

```json
{
  "genres": []
}
```

O exemplo acima ilustra o campo `genres`, não a resposta completa do endpoint.

Quando a obra não possui capa, `coverUrl` é `null`.

A remoção da capa não remove a obra da descoberta pública, desde que ela continue possuindo conteúdo publicado.

A ordem dos gêneros não faz parte do contrato.

Possível resposta:

```text
404 → obra inexistente ou sem conteúdo publicado
```

### 5.2. Listar obras publicadas

```http
GET /api/works
```

Endpoint público.

Retorna as obras que possuem pelo menos um installment publicado.

Cada obra aparece somente uma vez, independentemente da quantidade de installments publicados.

### 5.3. Filtrar obras por gênero

```http
GET /api/works?genre=SCI_FI
```

O filtro `genre` é opcional.

Quando informado, somente obras publicadas associadas ao gênero solicitado são retornadas.

Valores disponíveis:

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

Valores desconhecidos são rejeitados:

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

Sem o parâmetro, todas as obras publicadas são consideradas.

Obras que possuem o gênero, mas nenhum installment publicado, continuam invisíveis.

Quando nenhuma obra corresponde ao filtro:

```json
[]
```

A ordem dos gêneros não representa informação semântica.

---

## 6. Library — Biblioteca pessoal

### 6.1. Adicionar obra à biblioteca

```http
POST /api/library/works/{workId}
```

Requer sessão autenticada e token CSRF válido.

O proprietário da biblioteca é determinado pela sessão. O cliente não informa `userId`.

Somente obras com conteúdo publicado podem ser adicionadas.

Não é permitido adicionar a mesma obra duas vezes à biblioteca do mesmo usuário.

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

### 6.2. Listar biblioteca

```http
GET /api/library
```

Requer sessão autenticada.

Retorna somente as entradas da biblioteca do usuário autenticado.

Resposta de sucesso:

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

As entradas são ordenadas das adicionadas mais recentemente para as mais antigas.

Uma biblioteca vazia retorna:

```json
[]
```

Possível resposta:

```text
401 → usuário não autenticado
```

### 6.3. Remover obra da biblioteca

```http
DELETE /api/library/works/{workId}
```

Requer sessão autenticada e token CSRF válido.

A remoção considera exclusivamente a biblioteca do usuário autenticado.

Resposta de sucesso:

```http
204 No Content
```

A operação é idempotente.

Caso a obra não esteja mais presente na biblioteca, a resposta continua sendo:

```http
204 No Content
```

Possível resposta:

```text
401 → usuário não autenticado
```

---

## 7. Reading — Progresso de leitura

### 7.1. Salvar progresso de leitura

```http
PUT /api/reading/progress/pages/{pageId}
```

Requer sessão autenticada e token CSRF válido.

O cliente informa somente a página atual.

A obra e o installment são identificados internamente a partir dessa página.

Regras:

- a página deve pertencer a um installment publicado;
- páginas inexistentes ou não publicadas são tratadas como indisponíveis;
- existe uma posição de leitura por usuário e obra;
- se não houver progresso, ele é criado;
- se houver progresso, a posição é atualizada;
- o progresso não depende da presença da obra na biblioteca pessoal.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → token CSRF ausente ou inválido
404 → página indisponível para progresso
```

### 7.2. Consultar progresso de leitura

```http
GET /api/reading/progress/works/{workId}
```

Requer sessão autenticada.

Retorna a última posição registrada pelo usuário para a obra.

Resposta de sucesso:

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

Quando não existe progresso para a obra:

```http
404 Not Found
```

---

## 8. Segurança e regras de acesso

### Endpoints públicos

Os principais endpoints que dispensam uma sessão autenticada são:

```text
POST /api/users
GET  /api/auth/csrf
POST /api/auth/login

GET  /api/works
GET  /api/works/{workId}
GET  /api/works/{workId}/cover

GET  /api/installments/{installmentId}
GET  /api/pages/{pageId}/content
```

### Endpoints autenticados

```text
GET    /api/auth/me
POST   /api/auth/logout

POST   /api/works
PUT    /api/works/{workId}
PUT    /api/works/{workId}/cover
DELETE /api/works/{workId}/cover

GET    /api/me/works
GET    /api/me/works/{workId}
GET    /api/me/works/{workId}/cover

POST   /api/works/{workId}/installments
GET    /api/me/works/{workId}/installments
GET    /api/me/installments/{installmentId}

PUT    /api/installments/{installmentId}
DELETE /api/installments/{installmentId}
POST   /api/installments/{installmentId}/publish

POST   /api/installments/{installmentId}/pages
DELETE /api/installments/{installmentId}/pages/{pageId}
PUT    /api/installments/{installmentId}/pages/order
GET    /api/me/pages/{pageId}/content

GET    /api/library
POST   /api/library/works/{workId}
DELETE /api/library/works/{workId}

GET    /api/reading/progress/works/{workId}
PUT    /api/reading/progress/pages/{pageId}
```

### Propriedade da obra

Operações administrativas sobre obras, installments, páginas e capas validam a propriedade da obra.

O `ownerId` não é recebido diretamente pelo cliente.

A remoção de capas verifica a propriedade antes de modificar a entidade ou registrar a exclusão do arquivo físico.

### Estados de publicação

Installments em estado `DRAFT` podem ser gerenciados por seus proprietários.

Installments em estado `PUBLISHED` permanecem imutáveis no MVP.

O conteúdo em draft não é disponibilizado pelos endpoints públicos.

A definição, substituição ou remoção da capa não modifica o estado de publicação dos installments.

### Concorrência

As operações de escrita sobre o mesmo installment utilizam bloqueio pessimista no PostgreSQL.

Isso inclui criação, exclusão e reordenação de páginas, alteração de metadados, publicação e exclusão do installment.

O módulo `catalog` também utiliza bloqueio pessimista sobre a entidade `Work` para coordenar:

- atualização dos metadados da obra;
- definição e substituição de capas;
- remoção de capas.

A estratégia utiliza `PESSIMISTIC_WRITE` através de `findByIdForUpdate()`.

O bloqueio coordena operações concorrentes sobre a mesma obra, impedindo que alterações incompatíveis sejam aplicadas simultaneamente sem observar o estado confirmado pela transação anterior.

### Armazenamento binário

Os endpoints nunca expõem diretamente os caminhos físicos dos arquivos ou seus `storageKey`.

Arquivos são acessados através da abstração `BinaryStorage`, fornecida pelo módulo `media`.

A remoção física de arquivos associada à exclusão de páginas ou installments ocorre após o commit da transação.

Uploads de páginas possuem compensação em caso de rollback.

As operações de capas utilizam mecanismos complementares:

- **Upload inicial:** preserva o arquivo quando a transação é confirmada e o remove em caso de rollback.
- **Substituição:** remove a capa anterior após commit e remove a nova capa em caso de rollback.
- **Remoção:** elimina a referência no PostgreSQL e exclui o arquivo físico somente após commit.
- **Rollback de remoção:** preserva a referência anterior e o arquivo físico.

A exclusão de arquivos utiliza callbacks transacionais do Spring, incluindo `afterCommit()` e `afterCompletion()`.

Essas estratégias não constituem uma transação distribuída entre PostgreSQL e filesystem.

Falhas abruptas da aplicação ou erros durante a limpeza física ainda podem produzir arquivos órfãos.

---

## Documentação relacionada

- [Visão geral do projeto](../README.md)
- [Arquitetura](ARCHITECTURE.md)
- [Testes](TESTING.md)
