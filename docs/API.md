# Metopa — API HTTP

Documentação dos endpoints HTTP disponíveis no MVP do Metopa.

A API permite gerenciar contas, autenticação, obras, installments, páginas, biblioteca pessoal, progresso de leitura e moderação administrativa.

Este documento descreve os contratos implementados no backend. Os exemplos utilizam identificadores UUID e dados ilustrativos.

## Visão geral

URL base da aplicação local:

```text
http://localhost:8080
```

Os endpoints utilizam o prefixo `/api`.

A API distingue:

- operações públicas de leitura;
- operações pessoais e de criação que exigem autenticação;
- operações sobre recursos que exigem a propriedade da obra;
- operações administrativas de moderação que exigem `ROLE_ADMIN`.

### Autenticação

O Metopa utiliza Spring Security com autenticação baseada em sessão HTTP.

Após o login, a sessão é identificada pelo cookie `JSESSIONID`.

Endpoints protegidos exigem uma sessão autenticada.

As contas possuem os papéis:

| Papel | Responsabilidade |
|-------|------------------|
| `USER` | Operações regulares de criadores e leitores |
| `ADMIN` | Operações regulares e administração da moderação |

As operações de moderação administrativa exigem a autoridade `ROLE_ADMIN`.

O registro público de usuários não concede privilégios administrativos.

### Proteção CSRF

Operações que alteram estado exigem um token CSRF válido quando protegidas pela configuração de segurança.

O token pode ser obtido através de:

```http
GET /api/auth/csrf
```

O cliente deve enviar o token nas requisições protegidas utilizando o header informado pela API.

A autenticação por sessão não substitui a proteção CSRF.

### Identificadores

As principais entidades utilizam UUIDs:

- `userId`: usuário;
- `workId`: obra;
- `installmentId`: installment;
- `pageId`: página;
- `reportId`: denúncia de moderação.

Os identificadores relacionados ao usuário autenticado são obtidos internamente a partir da sessão, quando aplicável.

O cliente não deve informar o proprietário de uma obra ou de uma biblioteca.

Nas operações administrativas, o identificador do administrador é obtido a partir da identidade autenticada.

### Códigos HTTP

Os contratos utilizam, entre outros:

| Código | Significado |
|--------|-------------|
| `200 OK` | Consulta bem-sucedida |
| `201 Created` | Recurso criado |
| `204 No Content` | Operação concluída sem corpo de resposta |
| `400 Bad Request` | Dados ou parâmetros inválidos |
| `401 Unauthorized` | Autenticação necessária |
| `403 Forbidden` | Operação não autorizada ou CSRF inválido |
| `404 Not Found` | Recurso inexistente ou indisponível |
| `409 Conflict` | Conflito com uma regra de negócio ou transição inválida |

A indicação de possíveis respostas em cada endpoint não substitui todos os erros gerais que podem ocorrer durante o processamento HTTP.

### Respostas de erro

A aplicação utiliza respostas HTTP apropriadas para falhas de validação, autenticação, autorização e regras de negócio.

Alguns conflitos são representados utilizando `ProblemDetail`, compatível com o formato Problem Details.

Exemplo ilustrativo:

```json
{
  "type": "about:blank",
  "title": "Invalid moderation transition",
  "status": 409,
  "detail": "The requested moderation action is not allowed in the current state."
}
```

O conteúdo exato depende do tratamento de exceções associado à operação.

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
  "username": "reader",
  "email": "reader@example.com",
  "displayName": "Metopa Reader",
  "password": "StrongPassword123!"
}
```

Regras:

- `username`, `email`, `displayName` e `password` devem atender às validações da aplicação;
- username e e-mail são normalizados;
- username e e-mail devem ser únicos;
- o UUID da conta é gerado pelo domínio;
- a senha é armazenada como hash bcrypt, nunca em texto puro;
- novas contas recebem o papel regular `USER`;
- o registro público não permite solicitar o papel `ADMIN`.

Resposta de sucesso:

```http
201 Created
```

Possíveis respostas:

```text
400 → dados inválidos
409 → username ou e-mail já utilizado
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

As autoridades de acesso utilizadas pelo Spring Security são derivadas do papel associado à conta autenticada.

### 1.4. Consultar usuário autenticado

```http
GET /api/auth/me
```

Requer sessão autenticada.

Exemplo de resposta:

```json
{
  "username": "reader"
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
  "description": "A science fiction graphic novel.",
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

Valores duplicados não representam gêneros adicionais, pois o domínio mantém os gêneros da obra como um conjunto.

Novas obras possuem inicialmente visibilidade `PUBLIC`.

A visibilidade pública não significa que uma obra sem conteúdo publicado já esteja disponível na descoberta. A publicação de installments continua sendo necessária.

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

Somente o proprietário pode atualizar os metadados da obra.

A operação utiliza `PUT`: a requisição representa o novo estado completo dos metadados editáveis.

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

O identificador da obra, o proprietário, a capa, a data de criação e a visibilidade não são modificados por esse endpoint.

A atualização utiliza bloqueio pessimista sobre a obra para coordenar alterações concorrentes de metadados e capas.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → metadados inválidos
401 → usuário não autenticado
403 → CSRF inválido ou usuário não é proprietário
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

A consulta administrativa do criador é independente da descoberta pública.

Uma restrição de visibilidade impede o acesso público, mas não exclui o registro da obra do catálogo.

Somente obras pertencentes ao usuário autenticado são retornadas.

A ordenação considera as obras atualizadas mais recentemente primeiro.

A ordem dos elementos de `genres` não faz parte do contrato.

Uma listagem vazia retorna:

```json
[]
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

O `storageKey` da capa não é exposto.

A ordem dos gêneros não representa informação semântica.

Quando uma capa é removida, `hasCover` passa a ser `false`.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário
404 → obra não encontrada
```

Diferença entre as consultas:

```text
GET /api/works/{workId}
→ visão pública
→ requer conteúdo publicado e visibilidade PUBLIC

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
403 → CSRF inválido ou usuário não é proprietário
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
3. os campos `coverStorageKey` e `coverContentType` são removidos;
4. a alteração é persistida na transação PostgreSQL;
5. após o commit, o arquivo físico é excluído através de `BinaryStorage`.

Se ocorrer rollback, a referência anterior e o arquivo permanecem preservados.

A operação é **idempotente**.

Quando a obra existe, mas não possui capa, a remoção continua sendo considerada bem-sucedida.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → CSRF inválido ou usuário não é proprietário
404 → obra não encontrada
```

A remoção da capa não exclui a obra, seus metadados, installments ou páginas.

Após uma remoção confirmada:

- `hasCover` passa a ser `false`;
- a leitura administrativa da capa deixa de disponibilizar o arquivo;
- a leitura pública da capa deixa de disponibilizar o arquivo;
- `coverUrl` passa a ser `null` nas representações públicas.

A exclusão física utiliza `TransactionSynchronization.afterCommit()`.

Como PostgreSQL e filesystem não compartilham uma transação atômica, falhas na limpeza após o commit podem deixar arquivos órfãos.

### 2.7. Ler capa para o criador

```http
GET /api/me/works/{workId}/cover
```

Requer sessão autenticada.

Retorna diretamente o conteúdo binário da capa quando o usuário é proprietário da obra.

A leitura administrativa independe da existência de conteúdo publicado.

A propriedade da obra é validada antes do acesso ao armazenamento binário.

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
403 → usuário não é proprietário
404 → obra ou capa não encontrada
```

### 2.8. Ler capa publicamente

```http
GET /api/works/{workId}/cover
```

Endpoint público.

Retorna os bytes da capa com seu `Content-Type`.

A capa somente é disponibilizada publicamente quando:

- a obra possui uma capa;
- a obra possui pelo menos um installment publicado;
- a visibilidade da obra é `PUBLIC`.

Exemplo:

```text
HTTP/1.1 200 OK
Content-Type: image/webp
```

Possível resposta:

```text
404 → capa não disponível
```

A restrição administrativa de uma obra também torna sua capa indisponível publicamente.

O `storageKey` não é exposto.

### 2.9. Excluir obra

```http
DELETE /api/works/{workId}
```

Requer sessão autenticada e token CSRF válido.

Somente o proprietário pode excluir a obra. O cliente não informa `ownerId` nem referências de arquivos.

A exclusão é permitida **somente quando a obra não possui installments**, independentemente de estarem em `DRAFT` ou `PUBLISHED`.

Não existe exclusão em cascata de installments ou páginas por essa operação.

Quando a operação é permitida:

1. o backend localiza e bloqueia a obra utilizando `PESSIMISTIC_WRITE`;
2. a propriedade da obra é validada antes de consultar os installments;
3. o módulo `catalog` consulta o contrato `WorkDeletionGuard`, implementado pelo módulo `publication`;
4. se houver qualquer installment, a operação é rejeitada;
5. se não houver installments, o registro é removido na transação PostgreSQL;
6. as associações de gêneros, entradas de biblioteca e progresso são removidas conforme as constraints do banco;
7. quando existe capa, o arquivo físico é removido somente após o commit.

Se ocorrer rollback, a obra, suas referências e o arquivo da capa permanecem preservados.

Restrições:

- obras sem installments podem ser excluídas;
- obras com installments `DRAFT` não podem ser excluídas;
- obras com installments `PUBLISHED` não podem ser excluídas;
- a existência de capa ou gêneros não impede a exclusão;
- o proprietário deve ser validado antes da consulta aos installments.

A migration `V10__add_work_reference_constraints.sql` reforça essas regras diretamente no PostgreSQL.

A relação `installment.work_id` utiliza `ON DELETE RESTRICT`, impedindo que obras com installments sejam excluídas por operações SQL diretas.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → CSRF inválido ou usuário não é proprietário
404 → obra não encontrada
409 → obra possui installments DRAFT ou PUBLISHED
```

Quando a obra possui installments, a aplicação retorna `409 Conflict` com `ProblemDetail`.

A operação não é idempotente em termos de resposta HTTP.

Após uma exclusão bem-sucedida, uma nova tentativa com o mesmo `workId` retorna `404 Not Found`.

A exclusão da obra e a criação de installments utilizam bloqueio pessimista sobre a mesma entidade `Work`.

O objetivo é impedir que uma exclusão e uma criação concorrentes produzam registros inconsistentes.

A exclusão física da capa ocorre após commit e não constitui uma transação distribuída com o PostgreSQL.

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

A criação ocorre dentro de uma transação e adquire bloqueio pessimista na linha da obra.

Essa coordenação impede condições de corrida com a exclusão concorrente da mesma obra.

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

Os installments são ordenados por número crescente e, em caso de empate, pela data de criação.

Uma obra sem installments retorna:

```json
[]
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário
404 → obra não encontrada
```

A consulta administrativa inclui drafts, diferentemente da leitura pública.

### 3.3. Consultar installment para o criador

```http
GET /api/me/installments/{installmentId}
```

Requer sessão autenticada.

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

As páginas são ordenadas por número crescente.

O `storageKey` não é exposto.

Possíveis respostas:

```text
401 → usuário não autenticado
403 → usuário não é proprietário
404 → installment não encontrado
```

### 3.4. Atualizar metadados do installment

```http
PUT /api/installments/{installmentId}
```

Requer sessão autenticada, token CSRF válido e propriedade da obra.

A atualização é permitida somente enquanto o installment estiver em estado `DRAFT`.

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
- a combinação de tipo e número deve ser única;
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

1. o backend adquire bloqueio pessimista na linha do installment;
2. a propriedade da obra é validada;
3. as referências dos arquivos são coletadas;
4. o installment é removido no PostgreSQL;
5. os registros das páginas são excluídos por `ON DELETE CASCADE`;
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

A limpeza física posterior ao commit não é uma operação distribuída com o PostgreSQL.

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

A publicação de um installment não altera automaticamente a visibilidade administrativa da obra.

Uma obra `RESTRICTED` continua indisponível para leitura pública mesmo que possua installments publicados.

### 3.7. Consultar installment publicado

```http
GET /api/installments/{installmentId}
```

Endpoint público.

Retorna somente installments com status `PUBLISHED` pertencentes a obras com visibilidade `PUBLIC`.

Exemplo:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "workId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CHAPTER",
  "number": 1,
  "title": "The Beginning",
  "pages": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "number": 1,
      "contentType": "image/jpeg"
    }
  ]
}
```

As páginas são retornadas em ordem de leitura.

Installments `DRAFT` são tratados como não encontrados.

Installments de obras `RESTRICTED` também não são disponibilizados publicamente.

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
- o número da página deve ser único;
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
404 → installment ou página não encontrada
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

Regras:

- `pageIds` é obrigatório e não pode estar vazio;
- todos os elementos devem ser UUIDs válidos e não nulos;
- todas as páginas devem aparecer exatamente uma vez;
- IDs duplicados, ausentes ou externos são rejeitados;
- a ordem dos IDs define a nova ordem de leitura;
- os números são atribuídos sequencialmente a partir de `1`;
- lacunas anteriores são eliminadas;
- arquivos físicos e `Content-Type` não são alterados;
- installments publicados não podem ser reordenados.

A renumeração ocorre em duas fases, com `flush()` intermediário, preservando a unicidade do número da página no PostgreSQL.

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
403 → usuário não é proprietário
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

A página somente é disponibilizada quando:

- pertence a um installment `PUBLISHED`;
- a obra associada possui visibilidade `PUBLIC`.

Páginas em `DRAFT` são tratadas como não encontradas.

Páginas de obras `RESTRICTED` também são tratadas como indisponíveis.

O `storageKey` não é exposto.

---

## 5. Descoberta pública

A descoberta pública permite consultar obras disponíveis para leitura sem autenticação.

Uma obra precisa atender a duas condições:

1. possuir pelo menos um installment `PUBLISHED`;
2. possuir visibilidade `PUBLIC`.

A descoberta utiliza consultas PostgreSQL com filtros aplicados antes da paginação.

### 5.1. Consultar obra publicada

```http
GET /api/works/{workId}
```

Endpoint público.

Uma obra somente é disponibilizada quando possui conteúdo publicado e visibilidade `PUBLIC`.

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

Quando a obra não possui gêneros, a coleção é vazia.

Quando não possui capa, `coverUrl` é `null`.

A ordem dos gêneros não faz parte do contrato.

Possíveis respostas:

```text
404 → obra inexistente
404 → obra sem conteúdo publicado
404 → obra com visibilidade RESTRICTED
```

### 5.2. Listar obras publicadas

```http
GET /api/works
```

Endpoint público.

Retorna uma página de obras que possuem pelo menos um installment publicado e visibilidade `PUBLIC`.

O endpoint oferece:

- paginação;
- busca parcial por título;
- filtragem por gênero;
- combinação de filtros;
- ordenação determinística.

Parâmetros disponíveis:

| Parâmetro | Tipo | Padrão | Descrição |
|-----------|------|--------|-----------|
| `title` | String | Sem filtro | Busca parcial pelo título |
| `genre` | Enum | Sem filtro | Gênero da obra |
| `page` | Integer | `0` | Página solicitada |
| `size` | Integer | `20` | Quantidade de resultados por página |

O tamanho permitido é de 1 a 50 resultados por página.

Exemplos:

```http
GET /api/works
GET /api/works?page=0&size=10
GET /api/works?title=dragon
GET /api/works?genre=SCI_FI
GET /api/works?title=dragon&genre=SCI_FI
GET /api/works?title=dragon&genre=SCI_FI&page=0&size=10
```

Resposta de sucesso:

```http
200 OK
```

Exemplo ilustrativo de resposta paginada:

```json
{
  "content": [
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
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

A resposta possui:

| Campo | Descrição |
|-------|-----------|
| `content` | Obras presentes na página solicitada |
| `page` | Índice da página |
| `size` | Tamanho configurado |
| `totalElements` | Quantidade total de obras que correspondem aos filtros |
| `totalPages` | Quantidade total de páginas |

Uma página sem resultados apresenta `content` vazio.

A contagem considera os filtros de título, gênero e visibilidade antes da paginação.

### 5.3. Filtrar obras por gênero

```http
GET /api/works?genre=SCI_FI
```

O filtro `genre` é opcional.

Quando informado, somente obras públicas associadas ao gênero solicitado são retornadas.

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

Obras com o gênero solicitado, mas sem conteúdo publicado, permanecem invisíveis.

Obras restritas também são excluídas dos resultados.

### 5.4. Buscar obras por título

```http
GET /api/works?title=dragon
```

O parâmetro `title` permite realizar uma busca parcial e sem diferenciação entre maiúsculas e minúsculas.

A implementação utiliza PostgreSQL `ILIKE`, sem Elasticsearch ou outro mecanismo externo de busca.

Regras:

- o texto informado pode corresponder a qualquer trecho do título;
- espaços no início e no final são ignorados;
- valores ausentes ou em branco não aplicam filtro textual;
- `%` e `_` são tratados literalmente, não como curingas SQL;
- a busca considera somente obras publicadas e com visibilidade `PUBLIC`.

Exemplos:

```http
GET /api/works?title=dragon
GET /api/works?title=origins
GET /api/works?title=Dragon&page=0&size=10
```

A busca é aplicada no banco antes da paginação.

### 5.5. Combinar título, gênero e paginação

```http
GET /api/works?title=dragon&genre=FANTASY&page=0&size=10
```

Os filtros são opcionais e podem ser combinados.

Quando título e gênero são informados, a obra precisa corresponder aos dois critérios.

O PostgreSQL aplica os filtros antes de selecionar os resultados da página solicitada.

A contagem total corresponde às obras que atendem aos filtros, não apenas aos elementos retornados naquela página.

### 5.6. Ordenação e consistência da descoberta

Os resultados são ordenados por:

1. data de atualização mais recente de um installment publicado, em ordem decrescente;
2. identificador da obra, como critério de desempate.

Uma obra aparece somente uma vez, mesmo quando possui vários installments publicados.

A paginação é aplicada sobre obras distintas.

Obras `RESTRICTED` não participam da descoberta e não devem ser contabilizadas nos resultados públicos.

A implementação utiliza consultas PostgreSQL com filtragem e contagem no banco.

### 5.7. Visibilidade pública e moderação

O catálogo possui os estados:

```text
PUBLIC
RESTRICTED
```

Uma obra `PUBLIC` pode ser disponibilizada quando possui conteúdo publicado.

Uma obra `RESTRICTED` não é disponibilizada pelos endpoints públicos, mesmo que seus installments permaneçam `PUBLISHED`.

A restrição administrativa não remove:

- o registro da obra;
- seus installments;
- suas páginas;
- suas capas;
- seus metadados;
- o histórico da denúncia.

Quando a obra é restaurada para `PUBLIC`, o conteúdo publicado pode voltar a ser acessado.

Fluxo esperado:

```text
PUBLIC + PUBLISHED
        ↓
GET /api/works/{workId}
        ↓
200 OK

WORK_RESTRICTED
        ↓
RESTRICTED
        ↓
GET /api/works/{workId}
        ↓
404 Not Found

WORK_RESTORED
        ↓
PUBLIC
        ↓
GET /api/works/{workId}
        ↓
200 OK
```

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

Quando uma obra é excluída, suas referências em `library_entry` são removidas pelo PostgreSQL através de `ON DELETE CASCADE`.

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

Caso a obra não esteja presente na biblioteca, a resposta continua sendo:

```http
204 No Content
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
- a obra associada deve possuir visibilidade `PUBLIC`;
- existe uma posição de leitura por usuário e obra;
- se não houver progresso, ele é criado;
- se houver progresso, a posição é atualizada;
- o progresso não depende da presença da obra na biblioteca pessoal.

Quando uma obra é restringida, a criação e a atualização de progresso são bloqueadas.

O progresso existente permanece preservado.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
401 → usuário não autenticado
403 → token CSRF ausente ou inválido
404 → página indisponível para progresso
404 → página pertencente a obra RESTRICTED
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

A restrição administrativa de uma obra não elimina automaticamente seu progresso de leitura armazenado.

A consulta de progresso e a disponibilidade pública do conteúdo são responsabilidades distintas.

A exclusão de uma obra remove as referências em `reading_progress` através das restrições de integridade do PostgreSQL.

---

## 8. Moderation — Denúncias e administração

O módulo `moderation` permite registrar denúncias de obras e executar ações administrativas de análise, resolução, restrição e restauração.

O fluxo utiliza persistência no PostgreSQL, validações de transição e auditoria das ações administrativas.

As operações administrativas exigem `ROLE_ADMIN`.

### 8.1. Registrar denúncia de obra

```http
POST /api/works/{workId}/reports
```

Requer sessão autenticada e token CSRF válido.

O identificador da obra é informado na URL.

O usuário responsável pela denúncia é obtido a partir da sessão.

Exemplo:

```json
{
  "reason": "COPYRIGHT",
  "description": "Possible copyright infringement."
}
```

O campo `reason` representa o motivo da denúncia.

O campo `description` descreve o problema relatado.

Regras:

- o usuário deve estar autenticado;
- a obra denunciada deve existir;
- o motivo deve corresponder a um valor válido de `ReportReason`;
- a descrição deve atender às validações do domínio;
- o identificador do denunciante não é fornecido pelo cliente;
- uma nova denúncia começa no status `OPEN`.

Resposta de sucesso:

```http
201 Created
```

O registro criado possui um identificador UUID.

### 8.2. Estados da denúncia

O ciclo de vida da denúncia utiliza:

```text
OPEN
UNDER_REVIEW
RESOLVED
```

Transições permitidas:

```text
OPEN
  ↓
UNDER_REVIEW
  ↓
RESOLVED
```

As operações administrativas verificam o estado atual antes de realizar a transição.

Tentativas incompatíveis com o estado da denúncia são rejeitadas.

A alteração de estado é persistida em transação e acompanhada de registro de auditoria.

### 8.3. Listar denúncias administrativamente

```http
GET /api/admin/moderation/reports
```

Requer autenticação com `ROLE_ADMIN`.

Permite consultar denúncias com paginação e filtragem opcional por status.

Parâmetros:

| Parâmetro | Descrição | Padrão |
|-----------|-----------|--------|
| `status` | `OPEN`, `UNDER_REVIEW` ou `RESOLVED` | Sem filtro |
| `page` | Índice da página, começando em zero | `0` |
| `size` | Quantidade de denúncias por página | `20` |

O tamanho da página deve estar entre 1 e 50.

Exemplos:

```http
GET /api/admin/moderation/reports
GET /api/admin/moderation/reports?status=OPEN
GET /api/admin/moderation/reports?status=UNDER_REVIEW
GET /api/admin/moderation/reports?status=RESOLVED
GET /api/admin/moderation/reports?page=0&size=10
GET /api/admin/moderation/reports?status=OPEN&page=0&size=10
```

Resposta de sucesso:

```http
200 OK
```

A resposta é paginada e apresenta os dados resumidos das denúncias.

Os registros são ordenados pela data de criação mais recente, com desempate determinístico pelo identificador.

Possíveis respostas:

```text
400 → status ou paginação inválidos
401 → usuário não autenticado
403 → usuário sem ROLE_ADMIN
```

### 8.4. Iniciar análise administrativa

```http
POST /api/admin/moderation/reports/{reportId}/start-review
```

Requer autenticação com `ROLE_ADMIN` e token CSRF válido.

Exemplo:

```json
{
  "note": "Administrative review initiated."
}
```

Regras:

- a denúncia deve existir;
- a denúncia deve estar em `OPEN`;
- a justificativa é obrigatória;
- o administrador é identificado pela sessão;
- a operação altera o status para `UNDER_REVIEW`;
- uma auditoria `REVIEW_STARTED` é registrada.

A justificativa aceita até 2000 caracteres.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → justificativa inválida
401 → usuário não autenticado
403 → usuário sem ROLE_ADMIN ou CSRF inválido
404 → denúncia não encontrada
409 → denúncia não está em OPEN
```

A operação utiliza bloqueio pessimista para coordenar análises concorrentes.

### 8.5. Resolver denúncia sem violação

```http
POST /api/admin/moderation/reports/{reportId}/resolve
```

Requer autenticação com `ROLE_ADMIN` e token CSRF válido.

Exemplo:

```json
{
  "decision": "NO_VIOLATION",
  "note": "No copyright violation was found."
}
```

Regras:

- a denúncia deve estar `UNDER_REVIEW`;
- a decisão deve ser válida;
- a justificativa é obrigatória;
- a denúncia passa para `RESOLVED`;
- a decisão `NO_VIOLATION` é persistida;
- nenhuma restrição de visibilidade é aplicada;
- a auditoria registra `REPORT_RESOLVED`.

Resposta de sucesso:

```http
204 No Content
```

Tentativas de resolver uma denúncia já resolvida retornam `409 Conflict`.

### 8.6. Resolver denúncia com restrição da obra

```http
POST /api/admin/moderation/reports/{reportId}/resolve
```

Requer autenticação com `ROLE_ADMIN` e token CSRF válido.

Exemplo:

```json
{
  "decision": "WORK_RESTRICTED",
  "note": "Confirmed content policy violation."
}
```

Regras:

- a denúncia deve estar `UNDER_REVIEW`;
- a decisão deve ser `WORK_RESTRICTED`;
- a obra deve existir;
- a obra deve estar `PUBLIC`;
- a denúncia passa para `RESOLVED`;
- a decisão `WORK_RESTRICTED` é persistida;
- a obra passa para `RESTRICTED`;
- a auditoria registra `REPORT_RESOLVED`.

A alteração do estado da denúncia, a restrição da obra e a auditoria participam da mesma transação.

Se a restrição falhar, a resolução não deve ser persistida parcialmente.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → decisão ou justificativa inválida
401 → usuário não autenticado
403 → usuário sem ROLE_ADMIN ou CSRF inválido
404 → denúncia não encontrada
409 → transição inválida
409 → obra já restrita
```

### 8.7. Restaurar obra restringida

```http
POST /api/admin/moderation/reports/{reportId}/restore
```

Requer autenticação com `ROLE_ADMIN` e token CSRF válido.

Exemplo:

```json
{
  "note": "Restriction reversed after administrative review."
}
```

Regras:

- a denúncia deve existir;
- a denúncia deve estar `RESOLVED`;
- a decisão original deve ser `WORK_RESTRICTED`;
- a obra deve estar `RESTRICTED`;
- a justificativa é obrigatória;
- a obra retorna para `PUBLIC`;
- a denúncia permanece `RESOLVED`;
- a decisão original permanece `WORK_RESTRICTED`;
- uma nova auditoria `WORK_RESTORED` é registrada.

A restauração não altera retroativamente a decisão administrativa original.

Resposta de sucesso:

```http
204 No Content
```

Possíveis respostas:

```text
400 → justificativa inválida
401 → usuário não autenticado
403 → usuário sem ROLE_ADMIN ou CSRF inválido
404 → denúncia não encontrada
409 → denúncia incompatível com restauração
409 → obra já pública
```

Uma segunda tentativa de restauração da mesma obra, sem nova restrição, é rejeitada.

### 8.8. Decisões de moderação

As decisões implementadas são:

```text
NO_VIOLATION
WORK_RESTRICTED
```

| Decisão | Efeito |
|---------|--------|
| `NO_VIOLATION` | Resolve a denúncia sem alterar a visibilidade |
| `WORK_RESTRICTED` | Resolve a denúncia e restringe a obra |

A restauração não é uma decisão de resolução.

Ela representa uma ação administrativa posterior, registrada na auditoria.

### 8.9. Auditoria administrativa

A moderação possui os eventos:

```text
REVIEW_STARTED
REPORT_RESOLVED
WORK_RESTORED
```

Cada entrada registra:

- identificador do evento;
- identificador da denúncia;
- identificador do administrador;
- ação realizada;
- justificativa;
- data de criação.

A auditoria mantém o histórico das ações administrativas mesmo após a restauração da visibilidade de uma obra.

A restauração não exclui a denúncia original nem substitui os registros anteriores.

### 8.10. Consistência transacional

As operações administrativas utilizam transações Spring e persistência PostgreSQL.

O módulo `moderation` coordena as alterações da denúncia e da auditoria.

O módulo `catalog` permanece responsável pela visibilidade da obra.

As operações de restrição e restauração utilizam bloqueio pessimista sobre a entidade `Work`.

A denúncia também é bloqueada durante transições administrativas.

Essa abordagem busca impedir:

- resolução simultânea da mesma denúncia;
- transições inválidas;
- auditorias duplicadas após operações rejeitadas;
- alterações parciais em caso de rollback;
- restauração duplicada de uma obra já pública.

As decisões históricas permanecem persistidas, independentemente do estado atual da visibilidade.

---

## 9. Segurança e regras de acesso

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

A leitura pública depende do estado de publicação e da visibilidade da obra.

### Endpoints autenticados

```text
GET    /api/auth/me
POST   /api/auth/logout

POST   /api/works
PUT    /api/works/{workId}
DELETE /api/works/{workId}
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

POST   /api/works/{workId}/reports
```

Essas operações exigem autenticação e podem possuir regras adicionais de propriedade.

### Endpoints administrativos

```text
GET  /api/admin/moderation/reports

POST /api/admin/moderation/reports/{reportId}/start-review

POST /api/admin/moderation/reports/{reportId}/resolve

POST /api/admin/moderation/reports/{reportId}/restore
```

Esses endpoints exigem `ROLE_ADMIN`.

Usuários regulares não podem executar operações administrativas de moderação.

### Propriedade da obra

Operações administrativas do criador sobre obras, installments, páginas e capas validam a propriedade da obra.

O `ownerId` não é recebido diretamente pelo cliente.

A remoção de capas verifica a propriedade antes de modificar a entidade ou registrar a exclusão física.

A exclusão de uma obra verifica a propriedade antes de consultar a existência de installments.

Obras com qualquer installment são preservadas, e a operação retorna `409 Conflict`.

### Estados de publicação

Installments utilizam:

```text
DRAFT
PUBLISHED
```

Installments `DRAFT` podem ser gerenciados por seus proprietários.

Installments `PUBLISHED` permanecem imutáveis no MVP.

O conteúdo em draft não é disponibilizado publicamente.

A definição, substituição ou remoção da capa não modifica o estado de publicação dos installments.

### Estados de visibilidade

Obras utilizam:

```text
PUBLIC
RESTRICTED
```

A visibilidade é distinta do estado de publicação.

Uma obra `PUBLIC` ainda precisa possuir conteúdo publicado para aparecer na descoberta.

Uma obra `RESTRICTED` permanece persistida, mas seu conteúdo publicado não é disponibilizado nos endpoints públicos.

A restauração administrativa altera a visibilidade para `PUBLIC`, sem modificar os installments publicados.

### Autorização administrativa

O Spring Security diferencia os papéis `USER` e `ADMIN`.

Os endpoints `/api/admin/**` são protegidos por `ROLE_ADMIN`.

As regras de propriedade das obras continuam sendo aplicadas aos endpoints de gerenciamento do criador.

As operações de moderação usam a identidade do administrador autenticado para registrar o histórico de auditoria.

### Concorrência

As operações de escrita sobre o mesmo installment utilizam bloqueio pessimista no PostgreSQL.

Isso inclui:

- criação, exclusão e reordenação de páginas;
- atualização de metadados;
- publicação;
- exclusão de installments.

O módulo `catalog` utiliza bloqueio pessimista sobre `Work` para coordenar:

- atualização dos metadados;
- definição e substituição de capas;
- remoção de capas;
- exclusão de obras;
- criação de installments;
- restrição de visibilidade;
- restauração de visibilidade.

O módulo `moderation` também utiliza bloqueio pessimista sobre as denúncias durante transições administrativas.

A estratégia utiliza `PESSIMISTIC_WRITE` através dos métodos de repositório apropriados.

A criação de installments utiliza `CatalogService.findWorkForInstallmentCreation()`, que exige uma transação existente através de `Propagation.MANDATORY`.

O PostgreSQL complementa as regras de negócio com constraints e integridade referencial.

### Armazenamento binário

Os endpoints nunca expõem diretamente caminhos físicos ou `storageKey`.

Os arquivos são acessados através da abstração `BinaryStorage`, fornecida pelo módulo `media`.

A remoção física associada à exclusão de páginas ou installments ocorre após o commit.

Uploads possuem compensação em caso de rollback.

As operações de capas incluem:

- upload inicial com compensação;
- substituição com remoção da capa anterior após commit;
- remoção física após commit;
- preservação das referências e arquivos durante rollback;
- exclusão da capa após a exclusão confirmada da obra.

As estratégias utilizam callbacks transacionais do Spring, como `afterCommit()` e `afterCompletion()`.

Essas operações não constituem uma transação distribuída entre PostgreSQL e filesystem.

Falhas abruptas da aplicação ou erros durante a limpeza física podem produzir arquivos órfãos.

A reconciliação desses arquivos constitui uma etapa futura do backend MVP.

### Integridade referencial

A migration V10 reforça a integridade das referências relacionadas às obras:

| Relação | Restrição | Comportamento |
|---------|-----------|---------------|
| `installment.work_id` | `ON DELETE RESTRICT` | Impede excluir obras com installments |
| `library_entry.work_id` | `ON DELETE CASCADE` | Remove entradas de biblioteca associadas |
| `reading_progress.work_id` | `ON DELETE CASCADE` | Remove registros de progresso associados |
| `work_genre.work_id` | `ON DELETE CASCADE` | Remove gêneros associados; comportamento introduzido na V9 |

As migrations V11–V18 acrescentam a persistência e as constraints necessárias à moderação, aos papéis de acesso e à visibilidade das obras.

A aplicação permanece responsável pelas regras de domínio, autenticação e autorização.

O PostgreSQL complementa essas validações garantindo a integridade dos registros.

---

## Documentação relacionada

- [Visão geral do projeto](../README.md)
- [Arquitetura](ARCHITECTURE.md)
- [Testes](TESTING.md)
