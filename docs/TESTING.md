# Metopa — Estratégia de Testes

Documentação da estratégia de testes automatizados do backend do Metopa.

Os testes são utilizados para validar regras de domínio, persistência, autenticação, autorização, contratos HTTP, modularidade, descoberta pública, moderação administrativa, armazenamento binário, consistência transacional e concorrência.

O projeto utiliza Java 25, Spring Boot 4.1.1, JUnit, Mockito, Spring Test e PostgreSQL 18.

## 1. Estado atual

A última execução local completa da suíte apresentou:

```text
Tests run: 441
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A execução foi realizada em **21 de novembro de 2026**, através do comando:

```bash
./mvnw clean verify
```

O tempo total registrado foi de aproximadamente 3 minutos e 23 segundos.

Além dos testes automatizados, o Maven concluiu o empacotamento da aplicação como um JAR executável do Spring Boot.

Os checks do GitHub Actions também foram aprovados durante o Pull Request da moderação mínima e após o merge na branch `main`.

### Histórico de validações

| Data | Testes aprovados | Contexto |
|------|------------------|----------|
| 29/10/2026 | 258 | Remoção de capas |
| 02/11/2026 | 273 | Exclusão de obras e integridade referencial |
| 07/11/2026 | 304 | Descoberta pública paginada e busca por título |
| 21/11/2026 | 441 | Moderação mínima, visibilidade, autorização administrativa e auditoria |

O histórico representa as validações completas registradas durante o desenvolvimento.

A evolução de 304 para 441 testes também inclui ajustes e testes de regressão relacionados aos módulos existentes.

A quantidade de testes não representa, isoladamente, uma medida de qualidade ou cobertura percentual.

### Evolução da cobertura

A exclusão de obras introduziu cenários relacionados a:

- exclusão de obras sem installments;
- rejeição da exclusão de obras com installments `DRAFT`;
- rejeição da exclusão de obras com installments `PUBLISHED`;
- exclusão física da capa após commit;
- preservação da obra e da capa após rollback;
- autorização baseada na propriedade da obra;
- autenticação e CSRF;
- tratamento de recursos inexistentes;
- conflitos HTTP;
- concorrência entre exclusão e criação de installments;
- integridade referencial;
- exclusão das referências de biblioteca, progresso e gêneros.

Posteriormente, a descoberta pública passou a oferecer paginação, busca parcial por título e combinação com o filtro de gênero.

Os testes verificam:

- listagem paginada;
- exclusão de obras sem conteúdo publicado;
- prevenção de resultados duplicados;
- contagem de obras distintas;
- ordenação determinística;
- busca parcial por título;
- correspondência sem diferenciar maiúsculas e minúsculas;
- combinação de título e gênero;
- tratamento literal de `%`, `_` e `!`;
- validação dos parâmetros HTTP;
- preservação dos metadados de paginação.

A implementação da moderação mínima ampliou a cobertura para:

- registro de denúncias;
- persistência de motivos e descrições;
- estados `OPEN`, `UNDER_REVIEW` e `RESOLVED`;
- decisões `NO_VIOLATION` e `WORK_RESTRICTED`;
- autorização baseada em `USER` e `ADMIN`;
- autenticação administrativa;
- listagem e paginação de denúncias;
- início de análise;
- resolução administrativa;
- restrição de obras;
- restauração de obras;
- auditoria das ações;
- transições inválidas;
- operações duplicadas;
- rollback;
- concorrência;
- visibilidade dos conteúdos públicos;
- preservação do histórico de moderação.

A suíte também mantém a cobertura de consistência transacional das capas, incluindo compensação após rollback, limpeza após commit e operações concorrentes.

A migration V10 introduziu foreign keys entre `work` e as tabelas `installment`, `library_entry` e `reading_progress`.

Como consequência, quatro classes de testes de repositório foram ajustadas para preparar registros válidos de obras antes de persistir entidades dependentes:

- `InstallmentRepositoryTests`;
- `PageRepositoryTests`;
- `LibraryEntryRepositoryTests`;
- `ReadingProgressRepositoryTests`.

As migrations V11–V18 acrescentaram a persistência e as restrições relacionadas à moderação, aos papéis de acesso e à visibilidade das obras.

A suíte utiliza diferentes níveis de teste para validar tanto o comportamento isolado das classes quanto o funcionamento integrado da aplicação.

**O número de testes não é utilizado isoladamente como indicador de qualidade.** O objetivo principal é verificar comportamentos relevantes, regras de negócio e possíveis regressões.

## 2. Objetivos

A estratégia de testes busca:

- verificar regras de negócio;
- identificar regressões;
- garantir a integridade dos dados;
- validar contratos HTTP;
- verificar autenticação e autorização;
- preservar limites arquiteturais;
- validar operações com arquivos binários;
- verificar comportamentos transacionais;
- testar situações de concorrência;
- verificar integridade referencial;
- validar filtros, buscas e paginação;
- verificar ordenação e contagem;
- validar transições de moderação;
- proteger operações administrativas;
- verificar o histórico de auditoria;
- validar a visibilidade pública das obras;
- impedir alterações parciais em operações transacionais;
- permitir a evolução incremental do MVP;
- fornecer feedback automatizado através da CI.

O Metopa utiliza testes automatizados como parte do fluxo de desenvolvimento, especialmente em alterações que envolvem persistência, segurança, consultas públicas, operações destrutivas e transições de estado.

A validação local é complementada pelo GitHub Actions.

## 3. Tipos de teste

### 3.1. Testes unitários

Os testes unitários verificam comportamentos específicos de classes e componentes, geralmente sem iniciar toda a aplicação Spring.

O Mockito é utilizado para simular dependências quando necessário.

Esses testes são adequados para:

- validações de comandos;
- normalização de dados;
- regras de domínio;
- tratamento de conflitos;
- transições de estado;
- interações entre serviços;
- verificação de chamadas a repositories;
- seleção de consultas;
- conversão de resultados;
- preservação de metadados de paginação;
- operações realizadas através de `BinaryStorage`;
- registro e execução de callbacks transacionais;
- decisões administrativas;
- auditoria;
- restrição e restauração de visibilidade.

Os testes unitários permitem identificar problemas rapidamente durante o desenvolvimento.

Entretanto, não substituem testes de integração quando o comportamento depende de transações reais, SQL, bloqueios PostgreSQL ou configuração do Spring Security.

#### Catalog

Os testes verificam o uso de `findByIdForUpdate()` em operações de escrita.

Essa verificação confirma a interação com o repository, mas não demonstra, isoladamente, a aquisição do bloqueio no PostgreSQL.

Os testes de remoção de capas verificam:

- remoção da referência na entidade;
- exclusão física solicitada após commit;
- ausência de exclusão física após rollback;
- utilização do repository com bloqueio pessimista.

Os testes de visibilidade verificam as regras de domínio associadas aos estados `PUBLIC` e `RESTRICTED`.

#### Publication

Os testes de `PublicationService` verificam a seleção das consultas conforme os filtros.

Os cenários de busca por título incluem:

- ausência de filtro quando `title` é `null`;
- títulos compostos somente por espaços;
- remoção de espaços das extremidades;
- combinação com gênero;
- escape dos caracteres especiais;
- mapeamento para `PublishedWorkSummary`;
- preservação dos metadados da página;
- retorno de páginas vazias.

Esses testes utilizam mocks e não executam diretamente o operador PostgreSQL `ILIKE`.

A correspondência real é verificada por testes de persistência.

#### Moderation

Os testes unitários verificam:

- criação de denúncias;
- validação de entradas;
- transições permitidas;
- rejeição de transições inválidas;
- seleção das decisões administrativas;
- chamadas ao módulo `catalog`;
- persistência das ações de auditoria;
- tratamento de operações duplicadas.

As regras das entidades são verificadas independentemente dos controllers HTTP.

Os testes do serviço verificam a orquestração das dependências, sem substituir os testes transacionais com PostgreSQL.

### 3.2. Testes de integração

Os testes de integração verificam o comportamento conjunto de diferentes componentes.

Conforme o cenário, utilizam:

- contexto real do Spring Boot;
- endpoints Spring MVC;
- autenticação e sessões HTTP;
- proteção CSRF;
- persistência real no PostgreSQL;
- migrations Flyway;
- transações reais;
- bloqueios pessimistas;
- armazenamento binário no filesystem local.

Esses testes são relevantes para funcionalidades cujo comportamento depende da integração entre aplicação, banco e armazenamento físico.

#### Consistência de arquivos

Os testes verificam simultaneamente o estado persistido no PostgreSQL e a presença dos arquivos no filesystem.

Nas operações de capas, verificam a exclusão confirmada e a preservação dos dados após rollback.

Nas operações de exclusão de obras, verificam:

- remoção do registro;
- preservação de obras com installments;
- comportamento da capa após commit;
- preservação após rollback;
- integridade referencial;
- coordenação entre transações concorrentes.

#### Descoberta pública

Os testes de integração verificam:

- criação de obras e installments;
- publicação de conteúdo;
- descoberta HTTP;
- exclusão de drafts;
- consultas reais no PostgreSQL;
- busca textual com `ILIKE`;
- paginação;
- contagem;
- ordenação;
- tratamento literal de caracteres especiais;
- exclusão de obras `RESTRICTED`.

#### Moderação

Os testes de integração verificam:

- persistência das denúncias;
- alterações de estado;
- auditoria administrativa;
- autenticação e autorização;
- resolução sem violação;
- resolução com restrição;
- restauração;
- rollback;
- concorrência;
- prevenção de operações duplicadas;
- preservação das decisões históricas.

A integração entre `moderation` e `catalog` é importante porque a resolução pode alterar dados pertencentes aos dois módulos na mesma transação.

Esses cenários não poderiam ser reproduzidos integralmente apenas com mocks.

### 3.3. Testes de contratos HTTP

Os testes HTTP verificam o comportamento observado pelo cliente.

Entre os aspectos validados estão:

- rotas e métodos;
- formatos de requisição;
- parâmetros de consulta;
- validações de entrada;
- autenticação;
- autorização;
- proteção CSRF;
- códigos HTTP;
- representações retornadas;
- metadados de paginação;
- persistência dos efeitos, quando aplicável.

O Spring MockMvc é utilizado para exercitar a camada HTTP sem exigir um servidor externo iniciado manualmente.

#### Remoção de capas

Endpoint:

```http
DELETE /api/works/{workId}/cover
```

Os testes verificam respostas como:

- `204 No Content`;
- `401 Unauthorized`;
- `403 Forbidden`;
- `404 Not Found`.

#### Exclusão de obras

Endpoint:

```http
DELETE /api/works/{workId}
```

Os testes verificam:

| Situação | Resposta |
|----------|----------|
| Proprietário exclui obra sem installments | `204` |
| Usuário não autenticado | `401` |
| Usuário não autorizado | `403` |
| CSRF ausente ou inválido | `403` |
| Obra inexistente | `404` |
| Obra com installments | `409` |

O conflito utiliza `ProblemDetail`.

Campos relevantes:

```text
Title:  Work cannot be deleted
Detail: A work with installments cannot be deleted.
```

#### Descoberta pública

Endpoint:

```http
GET /api/works
```

Parâmetros:

```text
title
genre
page
size
```

Os testes verificam:

- valores padrão;
- recebimento dos filtros;
- combinação dos parâmetros;
- encaminhamento ao serviço;
- resposta paginada;
- parâmetros inválidos;
- resultados vazios.

Campos da resposta:

```text
content
page
size
totalElements
totalPages
```

Uma consulta válida retorna `200 OK`, inclusive sem resultados.

Parâmetros inválidos retornam `400 Bad Request`.

#### Moderação administrativa

Os testes HTTP verificam os endpoints de listagem, início de análise, resolução e restauração de denúncias.

Os contratos administrativos incluem:

```http
GET /api/admin/moderation/reports
POST /api/admin/moderation/reports/{reportId}/start-review
POST /api/admin/moderation/reports/{reportId}/resolve
POST /api/admin/moderation/reports/{reportId}/restore
```

São verificados:

- autenticação obrigatória;
- autorização `ROLE_ADMIN`;
- proteção CSRF;
- parâmetros de paginação;
- filtro por status;
- validação das justificativas;
- decisões válidas;
- códigos de sucesso;
- tratamento de denúncias inexistentes;
- rejeição de transições inválidas;
- conflitos `409`;
- ausência de alterações após operações rejeitadas.

Os testes isolados dos controllers utilizam dependências simuladas.

A integração real com autenticação, persistência e transações é verificada por testes específicos.

### 3.4. Testes de modularidade

O Spring Modulith é utilizado para verificar os limites arquiteturais da aplicação.

Os módulos são:

```text
identity
catalog
publication
library
reading
media
moderation
```

O objetivo é preservar o encapsulamento dos domínios.

A exclusão de obras utiliza `WorkDeletionGuard`, definido por `catalog` e implementado em `publication.internal`.

A moderação utiliza APIs públicas de `catalog` para validar obras e modificar sua visibilidade.

As entidades e repositories internos não devem ser acessados livremente por outros módulos.

A descoberta pública possui uma integração diferente: `publication` executa consultas SQL nativas que acessam tabelas de `catalog`.

O teste de modularidade verifica dependências entre módulos Java, não todas as referências SQL entre tabelas.

Essa limitação está documentada em [ARCHITECTURE.md](ARCHITECTURE.md).

## 4. Executando os testes

### 4.1. Pré-requisitos

Para executar a suíte completa, o ambiente deve possuir:

- JDK 25;
- Docker e Docker Compose;
- PostgreSQL disponível;
- Maven Wrapper do projeto.

O Maven Wrapper já está incluído no repositório.

Não é necessário instalar o Maven globalmente.

### 4.2. Iniciar o PostgreSQL

Na raiz do projeto:

```bash
docker compose up -d
```

Verifique os containers:

```bash
docker compose ps
```

Os testes de integração que utilizam o banco dependem de uma instância PostgreSQL acessível.

O Spring Boot também pode gerenciar o ciclo de vida do Docker Compose conforme a configuração da aplicação.

O Flyway valida e aplica as migrations necessárias durante a inicialização.

### 4.3. Executar a suíte completa

Durante o desenvolvimento:

```bash
./mvnw clean test
```

Antes de abrir um Pull Request:

```bash
./mvnw clean verify
```

O segundo comando executa as fases do Maven até `verify`, incluindo testes e empacotamento.

| Comando | Finalidade |
|---------|------------|
| `./mvnw test` | Executar os testes da fase `test` |
| `./mvnw clean test` | Limpar o build anterior e executar os testes |
| `./mvnw verify` | Executar as fases até `verify` |
| `./mvnw clean verify` | Realizar a validação completa com build limpo |

Os testes de integração atuais, incluindo testes com `@SpringBootTest`, são executados pelo Maven Surefire durante a fase `test`, conforme as convenções do projeto.

A classificação como integração depende do comportamento exercitado, não necessariamente da fase `integration-test`.

Executar `verify` não significa automaticamente que exista uma suíte separada controlada pelo Maven Failsafe Plugin.

Última validação:

```text
Tests run: 441, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 4.4. Executar uma classe específica

Durante o desenvolvimento, é possível executar somente a classe relacionada à funcionalidade modificada.

Exemplo:

```bash
./mvnw -Dtest=PublicationServiceTests test
```

#### Catalog

Testes unitários:

```bash
./mvnw -Dtest=CatalogServiceTests test
```

Integração de capas:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests test
```

Exclusão de obras:

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests test
```

Visibilidade:

```bash
./mvnw -Dtest=WorkVisibilityTests test
```

Persistência da visibilidade:

```bash
./mvnw -Dtest=WorkVisibilityRepositoryTests test
```

#### Publication

Repository:

```bash
./mvnw -Dtest=InstallmentRepositoryTests test
```

Service:

```bash
./mvnw -Dtest=PublicationServiceTests test
```

Controller:

```bash
./mvnw -Dtest=PublishedWorkControllerTests test
```

Integração:

```bash
./mvnw -Dtest=PublishedWorkDiscoveryIntegrationTests test
```

#### Identity

Papéis e contas:

```bash
./mvnw -Dtest=UserAccountTests test
```

Integração com Spring Security:

```bash
./mvnw -Dtest=IdentityUserDetailsServiceTests test
```

Autorização HTTP:

```bash
./mvnw -Dtest=WebSecurityAuthorizationTests test
```

#### Moderation

Domínio:

```bash
./mvnw -Dtest=ModerationReportTests test
```

Service:

```bash
./mvnw -Dtest=ModerationServiceTests test
```

Registro de denúncias:

```bash
./mvnw -Dtest=ModerationReportIntegrationTests test
```

Início de análise:

```bash
./mvnw -Dtest=ModerationServiceStartReviewTests test
```

Resolução:

```bash
./mvnw -Dtest=ModerationServiceResolveTests test
```

Testes HTTP administrativos:

```bash
./mvnw -Dtest=AdminModerationReportControllerTests test
```

Resolução administrativa com integração:

```bash
./mvnw -Dtest=AdminModerationResolveIntegrationTests test
```

Rollback de resolução:

```bash
./mvnw -Dtest=ModerationResolveTransactionIntegrationTests test
```

Concorrência na análise:

```bash
./mvnw -Dtest=ModerationStartReviewConcurrencyIntegrationTests test
```

#### Executar um método específico

O Maven Surefire permite selecionar um método pelo formato:

```text
-Dtest=NomeDaClasse#nomeDoMetodo
```

Exemplos existentes:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests#shouldRemoveNewCoverWhenReplacementRollsBack test
```

```bash
./mvnw -Dtest=WorkCoverIntegrationTests#shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently test
```

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests#shouldDeleteEmptyWorkForOwner test
```

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests#shouldPreventDeletingWorkWithInstallmentsAtDatabaseLevel test
```

```bash
./mvnw -Dtest=PublishedWorkDiscoveryIntegrationTests#shouldFindPublishedWorksByPartialTitleIgnoringCase test
```

Para verificar outros métodos, utilize os nomes efetivamente declarados na classe correspondente.

A execução direcionada permite obter feedback rápido.

Após corrigir ou implementar uma funcionalidade, a suíte completa deve ser executada para identificar regressões.

### 4.5. Relatórios de execução

O Maven Surefire disponibiliza relatórios em:

```text
target/surefire-reports/
```

Esses relatórios permitem identificar:

- testes executados;
- falhas;
- erros;
- testes ignorados;
- detalhes de exceções;
- resultados individuais das classes.

Em caso de falha, a investigação deve começar pelo teste afetado e pelo comportamento esperado.

### 4.6. Integração contínua com GitHub Actions

O backend possui um workflow de integração contínua.

O workflow utiliza:

- Java 25;
- PostgreSQL 18.6;
- Maven Wrapper;
- testes automatizados;
- empacotamento da aplicação.

Comando:

```bash
./mvnw -B -ntp clean verify
```

Opções:

| Opção | Finalidade |
|-------|------------|
| `-B` | Executar Maven em modo batch |
| `-ntp` | Desabilitar o progresso de transferências |

A CI utiliza um serviço PostgreSQL próprio.

Não depende da instalação local do desenvolvedor.

A configuração dos testes impede a substituição automática por um banco embarcado.

O workflow está configurado para validar:

- Pull Requests direcionados à `main`;
- pushes na branch `main`;
- execuções manuais.

A branch principal utiliza os checks obrigatórios como parte do processo de integração.

Fluxo:

```text
Desenvolvimento
    |
    v
Testes específicos
    |
    v
./mvnw clean verify
    |
    v
Revisão de código e documentação
    |
    v
Push da branch
    |
    v
Pull Request para main
    |
    v
GitHub Actions
    |
    v
Verificação dos checks
    |
    v
Merge
    |
    v
CI após merge
```

O Pull Request da moderação mínima foi integrado após a aprovação dos checks.

A execução posterior ao merge também foi aprovada.

O sucesso local não substitui a validação na CI.

Da mesma forma, o sucesso da CI não elimina a necessidade de revisão da implementação.

## 5. Estratégia de desenvolvimento

O desenvolvimento utiliza incrementos pequenos.

Quando adequado, segue o ciclo:

```text
RED
  ↓
GREEN
  ↓
REFACTOR
```

### 5.1. RED

Primeiro, é criado um teste que reproduz o comportamento desejado.

Antes da implementação, o teste deve falhar pela ausência desse comportamento.

Essa falha confirma que o teste consegue identificar o problema.

### 5.2. GREEN

Em seguida, é implementada a menor alteração necessária para satisfazer o teste.

A prioridade é corrigir o comportamento preservando os contratos existentes.

### 5.3. REFACTOR

Após a aprovação dos testes, a implementação é revisada.

São avaliados:

- clareza;
- responsabilidades;
- duplicação;
- encapsulamento;
- nomes;
- necessidade real de abstrações;
- respeito aos limites modulares.

O refactor não deve alterar o comportamento validado.

### 5.4. Validação final

Depois da implementação:

```bash
./mvnw clean verify
```

A validação completa ajuda a detectar regressões.

O comando também executa as fases posteriores aos testes, incluindo empacotamento.

### 5.5. Exemplo: consistência transacional das capas

A `feature/cover-transaction-consistency` foi desenvolvida através de quatro ciclos RED → GREEN.

**Primeiro ciclo — rollback de substituição**

O teste identificou que o arquivo da capa substituta permanecia após o rollback do PostgreSQL.

A implementação passou a utilizar `TransactionSynchronization.afterCompletion()` para remover a nova capa quando a transação termina com `STATUS_ROLLED_BACK`.

**Segundo ciclo — commit de substituição**

O teste identificou que a capa anterior permanecia após uma substituição confirmada.

A implementação passou a utilizar `TransactionSynchronization.afterCommit()` para removê-la.

**Terceiro ciclo — substituições concorrentes**

O teste identificou armazenamento de um novo arquivo enquanto outra transação mantinha a obra bloqueada.

A implementação passou a adquirir `PESSIMISTIC_WRITE` antes de armazenar a nova capa.

**Quarto ciclo — metadados e capa**

O teste identificou uma situação em que a atualização dos metadados sobrescrevia a referência de uma capa substituída por outra transação.

`updateWork()` e `setCover()` passaram a utilizar o mesmo bloqueio pessimista.

Depois desses ciclos, a suíte completa foi executada com 248 testes aprovados.

Esse resultado representa um marco histórico, não a contagem atual.

### 5.6. Exemplo: remoção de capas

A `feature/creator-cover-removal` acrescentou cenários funcionais, transacionais, de segurança e concorrência.

**Primeiro cenário — remoção de capa existente**

`shouldRemoveExistingCoverForWorkOwner()` verificou o comportamento esperado para a exclusão da capa pelo proprietário.

Durante o RED, o endpoint retornava `405 Method Not Allowed`.

A implementação introduziu:

- `DELETE /api/works/{workId}/cover`;
- `CatalogService.removeCover()`;
- `Work.clearCover()`;
- limpeza física após commit.

**Segundo cenário — rollback**

`shouldPreserveCoverWhenRemovalRollsBack()` verifica a preservação da referência e do arquivo após rollback.

**Terceiro cenário — idempotência**

`shouldRemoveCoverIdempotentlyWhenWorkHasNoCover()` verifica a resposta `204 No Content` quando a obra já não possui capa.

**Quarto cenário — propriedade**

`shouldRejectCoverRemovalFromAnotherUser()` verifica a rejeição com `403 Forbidden`.

**Quinto cenário — autenticação**

`shouldRejectUnauthenticatedCoverRemoval()` verifica a rejeição de requisições sem sessão.

**Sexto cenário — CSRF**

`shouldRequireCsrfForCoverRemoval()` verifica a proteção contra requisições sem token válido.

**Sétimo cenário — obra inexistente**

`shouldReturnNotFoundWhenRemovingCoverFromNonexistentWork()` identificou a necessidade de tratar `WorkNotFoundException` na camada HTTP.

**Oitavo cenário — concorrência**

`shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()` verifica a coordenação entre remoção e substituição.

**Nono cenário — callback de commit**

`shouldDeleteCoverFileAfterRemovalCommit()` verifica a execução da limpeza após commit.

**Décimo cenário — callback de rollback**

`shouldPreserveCoverFileAfterRemovalRollback()` verifica a ausência de exclusão física após rollback.

A validação histórica dessa feature concluiu com 258 testes aprovados.

### 5.7. Exemplo: exclusão de obras

A `feature/creator-work-deletion` utilizou testes para orientar regras de domínio, autorização, transações e concorrência.

Classe principal:

```text
WorkDeletionIntegrationTests
```

**Primeiro conjunto — regras funcionais**

Os testes verificaram a exclusão de obras sem installments e a rejeição quando existem installments `DRAFT` ou `PUBLISHED`.

A implementação introduziu:

- `DELETE /api/works/{workId}`;
- `WorkDeletionController`;
- `CatalogService.deleteWork()`;
- `WorkDeletionGuard`;
- `PublicationWorkDeletionGuard`;
- `WorkHasInstallmentsException`;
- tratamento HTTP `409 Conflict`.

**Segundo conjunto — consistência transacional**

Os testes verificaram a remoção da capa após commit e sua preservação após rollback.

**Terceiro conjunto — segurança**

Foram verificados:

- usuário sem propriedade;
- ausência de autenticação;
- ausência de CSRF;
- obra inexistente.

**Quarto conjunto — concorrência**

Foram implementados testes para os dois possíveis ordenamentos entre exclusão de obra e criação de installment.

Ambos utilizam bloqueio pessimista sobre a obra.

**Quinto conjunto — integridade referencial**

A migration V10 introduziu foreign keys para impedir a exclusão de obras com installments e remover referências dependentes.

Os testes verificam:

- biblioteca;
- progresso de leitura;
- integridade direta no PostgreSQL;
- gêneros associados.

A validação histórica concluiu com 273 testes aprovados.

### 5.8. Exemplo: descoberta pública paginada

A descoberta pública foi desenvolvida com testes de repository, service, controller e integração.

Endpoint:

```http
GET /api/works
```

Parâmetros:

```text
page
size
```

Padrões:

```text
page = 0
size = 20
```

O tamanho permitido é de 1 a 50.

Os testes verificam:

- obras publicadas;
- exclusão de drafts;
- prevenção de duplicados;
- filtragem por gênero;
- paginação no PostgreSQL;
- contagem de obras distintas;
- ordenação determinística;
- primeira página;
- páginas seguintes;
- páginas fora do intervalo;
- parâmetros inválidos.

O repository retorna `Page<UUID>`.

O serviço recupera os resumos das obras selecionadas.

A resposta HTTP utiliza `PublishedWorksPageResponse`.

Campos:

```text
content
page
size
totalElements
totalPages
```

### 5.9. Exemplo: busca pública por título

A `feature/public-work-title-search` acrescentou a busca parcial por título.

Exemplo:

```http
GET /api/works?title=dragon
```

A busca pode ser combinada com gênero e paginação:

```http
GET /api/works?title=dragon&genre=SCI_FI&page=0&size=10
```

**Persistência**

Os testes verificam:

- busca parcial;
- correspondência case-insensitive;
- exclusão de drafts;
- combinação de filtros;
- contagem;
- paginação;
- caracteres especiais.

A implementação utiliza PostgreSQL `ILIKE` com escape explícito.

**Service**

Os testes verificam:

- normalização;
- `null`;
- textos em branco;
- preservação do gênero;
- seleção da consulta;
- escape de caracteres;
- mapeamento;
- paginação;
- páginas vazias.

**HTTP**

Os testes verificam o recebimento e a validação dos parâmetros.

**Integração**

`PublishedWorkDiscoveryIntegrationTests` exercita o fluxo:

```text
HTTP
  ↓
PublishedWorkController
  ↓
PublicationService
  ↓
InstallmentRepository
  ↓
PostgreSQL
```

A validação histórica dessa etapa concluiu com 304 testes aprovados.

### 5.10. Exemplo: registro de denúncias

A `feature/minimal-moderation` introduziu o domínio de denúncias.

O desenvolvimento passou a verificar:

- validação de motivos;
- descrição da denúncia;
- existência da obra;
- identificação do denunciante;
- criação no estado `OPEN`;
- persistência;
- resposta HTTP;
- autenticação e CSRF.

As regras são verificadas em diferentes camadas.

Classes relacionadas:

```text
ModerationReportTests
ModerationServiceTests
ModerationReportRepositoryTests
WorkReportControllerTests
ModerationReportIntegrationTests
```

Os testes de domínio validam regras da entidade.

Os testes de service verificam a orquestração.

Os testes HTTP verificam o contrato.

Os testes de integração verificam a persistência real.

### 5.11. Exemplo: início de análise administrativa

Uma denúncia começa no estado `OPEN`.

O administrador pode iniciar sua análise, alterando o estado para `UNDER_REVIEW`.

Fluxo:

```text
OPEN
  ↓
UNDER_REVIEW
```

Os testes verificam:

- transição válida;
- rejeição de transição duplicada;
- justificativa administrativa;
- identificação do administrador;
- registro da auditoria;
- autorização `ROLE_ADMIN`;
- rollback;
- concorrência.

Classes relacionadas:

```text
ModerationServiceStartReviewTests
ModerationStartReviewTransactionIntegrationTests
ModerationStartReviewConcurrencyIntegrationTests
AdminModerationStartReviewControllerTests
AdminModerationStartReviewIntegrationTests
```

A transição utiliza bloqueio pessimista.

Duas operações concorrentes não devem registrar validamente o início da mesma análise utilizando o estado `OPEN` anterior.

### 5.12. Exemplo: resolução administrativa

A resolução exige uma denúncia em `UNDER_REVIEW`.

Decisões:

```text
NO_VIOLATION
WORK_RESTRICTED
```

**Sem violação**

A denúncia passa para `RESOLVED`, sem alterar a visibilidade da obra.

**Com restrição**

A denúncia passa para `RESOLVED`, enquanto a obra passa para `RESTRICTED`.

As alterações e a auditoria participam da transação.

Os testes verificam:

- decisões válidas;
- rejeição de decisões inválidas;
- preservação dos dados após falha;
- restrição da obra;
- persistência da auditoria;
- rejeição de resolução duplicada;
- autorização administrativa;
- códigos HTTP.

Classes relacionadas:

```text
ModerationServiceResolveTests
ModerationResolveTransactionIntegrationTests
AdminModerationResolveControllerTests
AdminModerationResolveIntegrationTests
```

### 5.13. Exemplo: restauração administrativa

A moderação também permite restaurar uma obra restringida.

A operação exige uma denúncia:

- `RESOLVED`;
- com decisão `WORK_RESTRICTED`.

A obra precisa estar `RESTRICTED`.

A restauração altera a visibilidade para `PUBLIC`.

A decisão original permanece armazenada.

Uma auditoria `WORK_RESTORED` registra a ação posterior.

Os testes verificam:

- restauração válida;
- preservação da decisão;
- persistência do evento de auditoria;
- rejeição de restauração duplicada;
- rejeição de usuários regulares;
- rollback;
- recuperação do acesso público.

Os cenários de descoberta verificam o comportamento:

```text
PUBLIC
  ↓
200 OK

RESTRICTED
  ↓
404 Not Found

RESTORED
  ↓
200 OK
```

A suíte completa após a implementação da moderação mínima confirmou 441 testes aprovados.

## 6. Cobertura funcional

Os testes estão distribuídos entre os domínios e responsabilidades da aplicação.

### 6.1. Identity

Os testes verificam:

- criação de contas;
- campos obrigatórios;
- normalização;
- unicidade de username e e-mail;
- persistência;
- geração de identificadores;
- armazenamento seguro das senhas;
- autenticação;
- sessões HTTP;
- login e logout;
- consulta do usuário autenticado;
- credenciais inválidas;
- papéis `USER` e `ADMIN`;
- persistência do papel da conta;
- carregamento das autoridades no Spring Security;
- autorização de endpoints administrativos.

Classes relacionadas:

```text
UserAccountTests
UserAccountRepositoryTests
IdentityUserDetailsServiceTests
WebSecurityAuthorizationTests
```

O registro regular não deve conceder privilégios administrativos.

Os testes de integração verificam que a configuração de segurança utiliza os papéis persistidos.

### 6.2. Catalog

Os testes verificam:

- criação de obras;
- associação com proprietário;
- normalização de metadados;
- atualização;
- rejeição de alterações não autorizadas;
- persistência de gêneros;
- substituição de gêneros;
- listagem do criador;
- isolamento entre usuários;
- capas;
- compensação de uploads;
- limpeza após commit;
- rollback;
- concorrência;
- exclusão de obras;
- integridade referencial;
- visibilidade `PUBLIC`;
- visibilidade `RESTRICTED`;
- transições de restrição e restauração;
- persistência do estado de visibilidade.

Classes específicas:

```text
WorkVisibilityTests
WorkVisibilityRepositoryTests
```

A visibilidade é uma propriedade da obra e não deve ser confundida com a publicação dos installments.

Os testes de integração com PostgreSQL verificam a persistência das alterações.

### 6.3. Publication

Os testes verificam:

- criação de installments;
- unicidade de tipo e número;
- atualização de metadados;
- restrições do estado `DRAFT`;
- imutabilidade de `PUBLISHED`;
- criação de páginas;
- upload multipart;
- armazenamento e leitura;
- numeração;
- reordenação;
- exclusão de páginas;
- exclusão de installments;
- publicação;
- consultas administrativas;
- propriedade;
- transações;
- rollback;
- concorrência;
- integridade com obras;
- descoberta pública;
- gênero;
- título;
- caracteres especiais;
- combinação de filtros;
- paginação;
- contagem;
- ordenação;
- metadados de paginação;
- exclusão de obras restritas da descoberta;
- bloqueio do conteúdo público;
- restauração da disponibilidade pública.

A cobertura verifica que restringir uma obra não modifica os estados `PUBLISHED` de seus installments.

O bloqueio ocorre na disponibilização pública do conteúdo.

### 6.4. Library

Os testes verificam:

- inclusão de obras;
- restrição a obras com conteúdo publicado;
- prevenção de duplicidade;
- listagem;
- isolamento por usuário;
- ordenação;
- remoção idempotente;
- integridade referencial;
- exclusão em cascata.

A exclusão em cascata é verificada em `WorkDeletionIntegrationTests`.

A restrição administrativa de visibilidade não representa exclusão física da obra.

### 6.5. Reading

Os testes verificam:

- registro de progresso;
- atualização;
- consulta;
- identificação da obra e do installment pela página;
- restrição a conteúdo publicado;
- páginas inexistentes;
- independência da biblioteca;
- integridade referencial;
- exclusão em cascata;
- bloqueio de criação e atualização de progresso em obras restritas.

A restrição de visibilidade não deve eliminar automaticamente o progresso persistido.

O progresso existente e a possibilidade de novas atualizações são comportamentos distintos.

### 6.6. Media

Os testes verificam:

- armazenamento binário;
- geração de `storageKey`;
- leitura;
- exclusão;
- exclusão idempotente de arquivos inexistentes;
- proteção contra caminhos fora da raiz;
- integração com `BinaryStorage`;
- remoção após commit;
- preservação após rollback.

A restrição administrativa de uma obra não remove seus arquivos.

Arquivos ainda referenciados por obras `RESTRICTED` continuam válidos.

### 6.7. Moderation

A implementação de moderação possui testes de domínio, repositories, services, controllers e integração.

#### Registro de denúncias

Verifica:

- criação;
- validação;
- persistência;
- existência da obra;
- identificação do denunciante;
- status inicial.

#### Estados

```text
OPEN
UNDER_REVIEW
RESOLVED
```

Os testes verificam transições válidas e inválidas.

#### Decisões

```text
NO_VIOLATION
WORK_RESTRICTED
```

Os testes verificam os efeitos das decisões.

#### Auditoria

```text
REVIEW_STARTED
REPORT_RESOLVED
WORK_RESTORED
```

Os testes verificam a persistência das ações administrativas.

#### Segurança

Os testes verificam:

- usuário autenticado;
- administrador;
- usuário regular;
- ausência de sessão;
- ausência de CSRF;
- autoridades do Spring Security.

#### Transações

Os testes verificam:

- consistência entre denúncia e obra;
- rollback;
- preservação do estado anterior;
- ausência de auditoria indevida;
- rejeição de operações duplicadas.

#### Concorrência

Os testes verificam o início de análise com transações concorrentes e bloqueios pessimistas.

#### Restauração

Os testes verificam:

- visibilidade `RESTRICTED` antes da operação;
- visibilidade `PUBLIC` depois;
- preservação da decisão `WORK_RESTRICTED`;
- auditoria `WORK_RESTORED`;
- rejeição de restauração duplicada;
- recuperação do acesso público.

## 7. Segurança

Os testes verificam os mecanismos de autenticação e autorização utilizados pela aplicação.

### 7.1. Autenticação

Operações privadas exigem sessão autenticada.

Os testes verificam respostas apropriadas para usuários não autenticados.

A descoberta pública dispensa sessão.

As operações administrativas de moderação exigem autenticação.

O carregamento das autoridades é validado em testes relacionados a `IdentityUserDetailsService`.

### 7.2. Autorização

As operações de gerenciamento de obras são restritas aos proprietários.

Os testes verificam que usuários não podem modificar recursos de terceiros.

Na moderação, os endpoints administrativos exigem:

```text
ROLE_ADMIN
```

Os testes verificam que usuários com papel `USER` não podem realizar transições administrativas.

A existência de sessão autenticada não significa autorização administrativa.

### 7.3. CSRF

Operações protegidas que alteram estado exigem token CSRF válido.

Os testes verificam:

- exclusão de capas;
- exclusão de obras;
- registro de denúncias;
- início de análise;
- resolução;
- restauração.

Uma requisição autenticada sem o token exigido deve ser rejeitada antes de modificar o estado persistido.

### 7.4. Respostas HTTP

Os códigos verificados incluem:

```text
200 OK
201 Created
204 No Content
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
```

#### Remoção de capas

| Situação | Resposta |
|----------|----------|
| Remoção válida | `204` |
| Capa já ausente | `204` |
| Sem autenticação | `401` |
| Sem propriedade | `403` |
| CSRF inválido | `403` |
| Obra inexistente | `404` |

#### Exclusão de obras

| Situação | Resposta |
|----------|----------|
| Obra sem installments | `204` |
| Obra com `DRAFT` | `409` |
| Obra com `PUBLISHED` | `409` |
| Sem autenticação | `401` |
| Sem propriedade | `403` |
| CSRF inválido | `403` |
| Obra inexistente | `404` |

A idempotência HTTP diz respeito ao efeito pretendido sobre o recurso, não à obrigação de repetir o mesmo código.

#### Descoberta pública

| Situação | Resposta |
|----------|----------|
| Consulta válida | `200` |
| Nenhum resultado | `200` |
| Página fora do intervalo | `200` |
| Gênero inválido | `400` |
| `page` negativo | `400` |
| `size` inválido | `400` |

#### Moderação administrativa

| Situação | Resposta |
|----------|----------|
| Listagem válida | `200` |
| Início de análise válido | `204` |
| Resolução válida | `204` |
| Restauração válida | `204` |
| Dados inválidos | `400` |
| Sem autenticação | `401` |
| Sem `ROLE_ADMIN` | `403` |
| CSRF inválido | `403` |
| Denúncia inexistente | `404` |
| Transição inválida | `409` |
| Operação duplicada incompatível | `409` |

Os testes também verificam a preservação dos dados após rejeições.

### 7.5. Visão pública e administrativa

Os testes distinguem:

- conteúdo público;
- conteúdo `DRAFT`;
- gerenciamento do proprietário;
- operações administrativas de moderação;
- conteúdo publicado de obras restritas.

Uma obra precisa possuir conteúdo publicado e visibilidade `PUBLIC` para ser disponibilizada na descoberta.

O proprietário possui endpoints próprios de gerenciamento.

Os administradores possuem endpoints específicos para moderação.

A restrição administrativa não modifica a propriedade da obra.

### 7.6. Visibilidade de obras

A disponibilidade pública depende de:

```text
Installment.status = PUBLISHED
Work.visibility = PUBLIC
```

Os testes verificam:

- exclusão de obras restritas da descoberta;
- indisponibilidade de detalhes públicos;
- indisponibilidade de conteúdo de installments;
- bloqueio de páginas públicas;
- bloqueio de capas públicas;
- proteção do progresso de leitura;
- restauração do acesso após mudança para `PUBLIC`.

A implementação utiliza `404 Not Found` para recursos de leitura pública indisponíveis devido à restrição.

### 7.7. Segurança da moderação

Os testes verificam a diferença entre autenticação e autorização.

Um usuário regular autenticado não pode:

- consultar a listagem administrativa de denúncias;
- iniciar análise;
- resolver denúncias;
- restringir obras administrativamente;
- restaurar obras.

Essas operações dependem de `ROLE_ADMIN`.

A identidade do administrador utilizada na auditoria é obtida pela aplicação.

O cliente não informa um identificador administrativo arbitrário para assumir a autoria da ação.

## 8. Testes de persistência

O PostgreSQL é utilizado nos testes que dependem de comportamento relacional real.

Mocks não reproduzem integralmente:

- constraints;
- foreign keys;
- transações;
- rollback;
- bloqueios;
- consultas SQL nativas;
- ordenação e paginação;
- isolamento entre transações.

### 8.1. Restrições de unicidade

As validações da aplicação são complementadas pelo banco.

Exemplos:

- combinação única de tipo e número por obra;
- número único de página por installment;
- username e e-mail únicos.

### 8.2. Exclusão em cascata

A exclusão de installments utiliza `ON DELETE CASCADE` para os registros das páginas.

A exclusão física dos arquivos é validada separadamente.

Na exclusão de obras:

| Tabela | Restrição | Comportamento |
|--------|-----------|---------------|
| `installment` | `fk_installment_work` | Impede exclusão da obra |
| `library_entry` | `fk_library_entry_work` | Remove entradas associadas |
| `reading_progress` | `fk_reading_progress_work` | Remove progresso associado |
| `work_genre` | Restrição da V9 | Remove gêneros associados |

Installments não são excluídos em cascata quando uma obra é removida.

### 8.3. Rollback

Os testes verificam que alterações não confirmadas são revertidas.

Quando existem arquivos físicos, verificam também `BinaryStorage`.

Na substituição de capas, a referência anterior e seu arquivo devem ser preservados após rollback.

Na remoção de capas, o rollback deve preservar a referência e o arquivo.

Na exclusão de obras, devem permanecer:

- obra;
- referência da capa;
- arquivo físico.

Na moderação, o rollback deve preservar a consistência entre denúncia, visibilidade e auditoria.

### 8.4. Persistência da remoção de capas

`CatalogService.removeCover()` altera os campos:

```text
cover_storage_key
cover_content_type
```

Depois do commit, ambos devem ficar nulos.

Após rollback, os valores anteriores devem ser preservados.

A remoção não exclui a obra.

### 8.5. Integridade referencial da exclusão de obras

A V10 introduziu:

```sql
ALTER TABLE installment
    ADD CONSTRAINT fk_installment_work
    FOREIGN KEY (work_id)
    REFERENCES work(id)
    ON DELETE RESTRICT;

ALTER TABLE library_entry
    ADD CONSTRAINT fk_library_entry_work
    FOREIGN KEY (work_id)
    REFERENCES work(id)
    ON DELETE CASCADE;

ALTER TABLE reading_progress
    ADD CONSTRAINT fk_reading_progress_work
    FOREIGN KEY (work_id)
    REFERENCES work(id)
    ON DELETE CASCADE;
```

Os testes verificam:

- impedimento de exclusão direta com installments;
- remoção de referências de biblioteca;
- remoção de progresso;
- remoção de gêneros.

O teste de gêneros valida o resultado observado, embora a limpeza também possa ocorrer pelo gerenciamento da coleção JPA.

Por isso, esse teste não demonstra isoladamente qual mecanismo executou a exclusão.

### 8.6. Ajustes nos testes de repositório após a V10

Antes da V10, alguns testes persistiam entidades com `workId` aleatório sem criar a obra correspondente.

Isso passou a violar as foreign keys.

As classes afetadas foram:

```text
InstallmentRepositoryTests
PageRepositoryTests
LibraryEntryRepositoryTests
ReadingProgressRepositoryTests
```

Os testes foram ajustados para criar obras válidas.

A preparação utiliza `JdbcTemplate` quando apropriado.

Essa abordagem mantém o foco nos repositories sem depender de todos os serviços de criação.

### 8.7. Persistência da descoberta pública

As consultas são executadas pelo `InstallmentRepository`.

Os testes verificam:

- status `PUBLISHED`;
- visibilidade `PUBLIC`;
- deduplicação;
- paginação;
- contagem;
- filtros;
- ordenação.

A contagem utiliza:

```sql
COUNT(DISTINCT i.work_id)
```

A ordenação utiliza:

```sql
ORDER BY MAX(i.updated_at) DESC, i.work_id ASC
```

A busca textual utiliza:

```sql
w.title ILIKE :titlePattern ESCAPE '!'
```

Os testes verificam o comportamento real dessas expressões no PostgreSQL.

A associação de gênero é consultada por `EXISTS`.

A visibilidade também precisa participar dos filtros de seleção e contagem.

### 8.8. Persistência da moderação

As migrations V11–V18 introduziram estruturas e constraints relacionadas a:

- denúncias;
- papéis de contas;
- auditoria;
- decisões;
- resolução;
- visibilidade;
- restrição;
- restauração.

Classes de testes relacionadas:

```text
ModerationReportRepositoryTests
ModerationAuditEntryRepositoryTests
UserAccountRepositoryTests
WorkVisibilityRepositoryTests
```

Os testes verificam os comportamentos de persistência associados a cada domínio.

As regras das entidades e as restrições do PostgreSQL são complementares.

### 8.9. Auditoria administrativa

O histórico utiliza os eventos:

```text
REVIEW_STARTED
REPORT_RESOLVED
WORK_RESTORED
```

Os testes verificam a persistência das entradas e sua associação às operações administrativas.

Uma restauração não deve modificar retroativamente a decisão original.

O histórico deve distinguir a resolução da denúncia da alteração posterior da visibilidade.

### 8.10. Visibilidade persistida

A entidade `Work` possui os estados:

```text
PUBLIC
RESTRICTED
```

Os testes verificam as transições e sua persistência.

A alteração da visibilidade não modifica diretamente os registros de installments ou páginas.

Uma obra restrita pode continuar possuindo conteúdo `PUBLISHED`.

A descoberta pública utiliza a visibilidade persistida para determinar a disponibilidade.

## 9. Testes de armazenamento binário

Arquivos de páginas e capas são armazenados fora do PostgreSQL.

Por isso, os testes não verificam apenas os registros do banco.

Quando necessário, verificam a existência, leitura ou remoção dos arquivos físicos.

### 9.1. Upload

Os testes verificam armazenamento de arquivos e persistência dos metadados.

Endpoints multipart são testados em integração com HTTP.

### 9.2. Leitura

Os testes verificam a recuperação do conteúdo binário.

Também validam:

- preservação do `Content-Type`;
- autorização do proprietário;
- indisponibilidade de conteúdo em `DRAFT`;
- indisponibilidade pública de conteúdo de obras `RESTRICTED`.

### 9.3. Exclusão

Os testes verificam a remoção de registros e arquivos.

Na substituição de capas:

- a capa anterior é removida após commit;
- a nova capa é removida após rollback.

Na remoção de capas:

- a referência é removida no PostgreSQL;
- o arquivo é excluído após commit;
- rollback preserva referência e arquivo.

Na exclusão de obras:

- o registro é removido;
- a referência da capa é capturada;
- o arquivo é excluído após commit;
- rollback preserva tudo.

A restrição administrativa não utiliza exclusão física.

### 9.4. Segurança dos caminhos

A implementação de filesystem verifica a resolução dos caminhos em relação à raiz configurada.

Os testes incluem proteção contra operações fora dessa raiz.

### 9.5. Arquivos de obras restritas

Arquivos de obras `RESTRICTED` continuam associados a registros existentes.

Eles não são automaticamente órfãos.

A indisponibilidade pública é uma regra de autorização e visibilidade, não uma indicação de que o arquivo pode ser excluído.

Essa distinção deverá ser preservada nos futuros testes de reconciliação.

## 10. Testes de consistência transacional

O PostgreSQL e o filesystem não compartilham uma transação distribuída.

O Metopa utiliza estratégias complementares de compensação e limpeza.

### 10.1. Exclusão de páginas após commit

`deletePage()` registra a limpeza física para depois do commit.

O callback utiliza `TransactionSynchronization.afterCommit()`.

| Resultado | Registro PostgreSQL | Arquivo físico |
|-----------|---------------------|----------------|
| Commit | Removido | Removido após commit |
| Rollback | Preservado | Preservado |

Os testes verificam que o arquivo não é removido antes da confirmação da exclusão.

### 10.2. Exclusão de installments após commit

`deleteInstallment()` utiliza limpeza física pós-commit.

Os registros das páginas são removidos por `ON DELETE CASCADE`.

| Resultado | Installment e páginas | Arquivos físicos |
|-----------|-----------------------|------------------|
| Commit | Removidos | Removidos após commit |
| Rollback | Preservados | Preservados |

### 10.3. Compensação de upload de páginas após rollback

`createPage()` armazena o arquivo antes da confirmação da transação.

A implementação utiliza `afterCompletion()` para remover o arquivo quando ocorre `STATUS_ROLLED_BACK`.

| Resultado | Registro PostgreSQL | Arquivo físico |
|-----------|---------------------|----------------|
| Commit | Persistido | Preservado |
| Rollback | Não persistido | Removido |

### 10.4. Teste de regressão do upload de páginas

O cenário é implementado em:

```text
PageUploadIntegrationTests.shouldRemoveUploadedPageFileWhenTransactionRollsBack()
```

Fluxo:

1. iniciar transação;
2. criar página;
3. verificar armazenamento;
4. solicitar rollback;
5. verificar ausência do registro;
6. verificar remoção física.

Durante RED, o teste encontrou:

```text
Expecting value to be false but was true
```

O arquivo permanecia após rollback.

A implementação da compensação corrigiu o comportamento.

### 10.5. Consistência transacional de capas

`CatalogService.setCover()` coordena:

- referência anterior;
- arquivo novo;
- atualização da entidade.

**Commit**

- nova referência persistida;
- arquivo novo preservado;
- capa anterior removida após commit.

**Rollback**

- referência anterior preservada;
- arquivo anterior preservado;
- arquivo novo removido.

| Resultado | Referência PostgreSQL | Capa anterior | Nova capa |
|-----------|-----------------------|---------------|-----------|
| Commit | Nova capa | Removida | Preservada |
| Rollback | Capa anterior | Preservada | Removida |

Essas garantias se referem aos fluxos de transação e callbacks executados normalmente.

### 10.6. Testes de regressão das capas

Os principais cenários estão em `WorkCoverIntegrationTests`.

**Rollback de substituição**

`shouldRemoveNewCoverWhenReplacementRollsBack()` verifica:

1. criação de obra;
2. armazenamento da capa original;
3. tentativa de substituição;
4. criação do arquivo novo;
5. rollback;
6. preservação da referência original;
7. preservação do arquivo original;
8. exclusão do arquivo novo.

Durante RED, dois arquivos permaneciam após rollback.

A implementação de `afterCompletion()` corrigiu a falha.

**Commit de substituição**

`shouldRemovePreviousCoverAfterSuccessfulReplacement()` verifica:

1. capa original confirmada;
2. substituição em nova transação;
3. nova referência no banco;
4. leitura da nova capa;
5. exclusão do arquivo anterior;
6. permanência apenas do novo arquivo.

Os testes verificam efeitos reais no banco e no filesystem.

### 10.7. Consistência da remoção de capas

`CatalogService.removeCover()` utiliza bloqueio pessimista.

Fluxo:

1. bloquear obra;
2. validar propriedade;
3. capturar `storageKey`;
4. limpar referência e `Content-Type`;
5. registrar callback de exclusão após commit.

| Resultado | Referência PostgreSQL | Arquivo original |
|-----------|-----------------------|------------------|
| Commit | Removida | Removido após commit |
| Rollback | Preservada | Preservado |

Quando a obra existe sem capa, a operação é idempotente.

Quando a obra não existe, retorna `404 Not Found`.

#### Testes de integração da remoção

`WorkCoverIntegrationTests` possui cenários para:

- remoção válida;
- rollback;
- idempotência;
- usuário sem propriedade;
- ausência de autenticação;
- ausência de CSRF;
- obra inexistente;
- concorrência com substituição.

O teste de concorrência utiliza transações independentes e sincronização entre threads.

Após as operações, verifica a referência e o arquivo final.

### 10.8. Testes unitários dos callbacks

Os serviços possuem testes unitários relacionados a callbacks.

Esses testes não possuem automaticamente uma transação Spring real.

Quando necessário, utilizam `TransactionSynchronizationManager` para preparar a sincronização.

No `CatalogServiceTests`, o helper `simulateTransactionCompletion()` permite executar os callbacks simulando commit ou rollback.

Os testes incluem:

```text
shouldSetCoverForWorkOwner
shouldDeleteNewCoverAfterTransactionRollback
shouldDeletePreviousCoverAfterTransactionCommit
shouldRejectCoverFromUserWhoDoesNotOwnWork
shouldDeleteCoverFileAfterRemovalCommit
shouldPreserveCoverFileAfterRemovalRollback
```

Esses testes verificam as interações e a execução dos callbacks.

Não demonstram a reversão real de entidades JPA.

A persistência efetiva é verificada pelos testes de integração.

### 10.9. Consistência da exclusão de obras

`CatalogService.deleteWork()` utiliza bloqueio pessimista.

A exclusão exige ausência de installments.

Fluxo:

1. bloquear a obra;
2. validar propriedade;
3. verificar installments;
4. capturar referência da capa;
5. remover a obra;
6. agendar exclusão da capa após commit.

| Resultado | Registro da obra | Referência da capa | Arquivo |
|-----------|------------------|--------------------|---------|
| Commit | Removido | Removida | Removido após commit |
| Rollback | Preservado | Preservada | Preservado |
| Obra com installments | Preservado | Preservada | Preservado |

Quando a obra não possui capa, nenhuma limpeza física é necessária.

#### Testes de integração da exclusão

`WorkDeletionIntegrationTests` contém 15 cenários:

1. `shouldDeleteEmptyWorkForOwner()`: exclusão válida.
2. `shouldRejectDeletingWorkWithDraftInstallment()`: rejeição com `DRAFT`.
3. `shouldRejectDeletingWorkWithPublishedInstallment()`: rejeição com `PUBLISHED`.
4. `shouldDeleteWorkCoverAfterSuccessfulWorkDeletion()`: limpeza após commit.
5. `shouldPreserveWorkAndCoverWhenDeletionRollsBack()`: preservação após rollback.
6. `shouldRejectWorkDeletionFromNonOwner()`: propriedade.
7. `shouldRejectUnauthenticatedWorkDeletion()`: autenticação.
8. `shouldRejectWorkDeletionWithoutCsrf()`: CSRF.
9. `shouldReturnNotFoundWhenDeletingNonexistentWork()`: recurso inexistente.
10. `shouldRejectInstallmentCreationWhenWorkDeletionCommitsFirst()`: concorrência.
11. `shouldRejectWorkDeletionWhenInstallmentCreationCommitsFirst()`: concorrência.
12. `shouldRemoveLibraryEntriesWhenDeletingWork()`: biblioteca.
13. `shouldRemoveReadingProgressWhenDeletingWork()`: progresso.
14. `shouldPreventDeletingWorkWithInstallmentsAtDatabaseLevel()`: foreign key.
15. `shouldRemoveWorkGenresWhenDeletingWork()`: gêneros.

Os testes utilizam PostgreSQL real.

As operações concorrentes são executadas em transações independentes.

### 10.10. Consistência transacional da moderação

A resolução de denúncias pode modificar dados em dois módulos.

Exemplo:

```text
ModerationService
    |
    +----> ModerationReport
    |      altera status e decisão
    |
    +----> CatalogService
    |      restringe Work
    |
    +----> ModerationAuditEntry
           registra auditoria
```

As alterações utilizam a mesma transação PostgreSQL.

**Commit**

- denúncia resolvida;
- decisão persistida;
- visibilidade atualizada;
- auditoria registrada.

**Rollback**

- estado anterior preservado;
- decisão não confirmada;
- visibilidade anterior preservada;
- auditoria não confirmada.

Os testes verificam que falhas na alteração da visibilidade não deixam uma denúncia resolvida parcialmente.

Classe relacionada:

```text
ModerationResolveTransactionIntegrationTests
```

### 10.11. Restauração administrativa

A restauração exige:

- denúncia `RESOLVED`;
- decisão `WORK_RESTRICTED`;
- obra `RESTRICTED`.

A operação restaura a visibilidade e registra `WORK_RESTORED`.

A decisão original permanece preservada.

| Resultado | Visibilidade | Decisão original | Nova auditoria |
|-----------|--------------|------------------|----------------|
| Commit válido | `PUBLIC` | Preservada | Persistida |
| Rollback | `RESTRICTED` | Preservada | Não confirmada |
| Restauração duplicada | `PUBLIC` | Preservada | Não duplicada |

Os testes de integração verificam a persistência e o comportamento HTTP.

### 10.12. Limitações da cobertura transacional

Os testes atuais validam transações normais e rollbacks controlados.

Não demonstram garantia de limpeza em todas as situações.

Limitações conhecidas:

- encerramento abrupto da JVM;
- falhas durante exclusão física;
- interrupção antes do registro de compensação;
- resultados transacionais incertos;
- ausência de reconciliação durável.

Uma falha em `afterCommit()` pode deixar um arquivo presente após a alteração relacional confirmada.

Os callbacks não oferecem atomicidade distribuída entre PostgreSQL e filesystem.

A moderação não modifica arquivos físicos ao restringir ou restaurar obras.

Sua consistência transacional é predominantemente relacional.

## 11. Testes de concorrência

O Metopa utiliza bloqueios pessimistas para coordenar operações sobre o mesmo installment, obra ou denúncia.

A implementação utiliza `PESSIMISTIC_WRITE`.

Os testes exercitam transações independentes.

### 11.1. Reordenação concorrente com publicação

Uma transação reordena páginas enquanto outra tenta publicar o installment.

O bloqueio coordena as operações.

O teste verifica a preservação da ordem e do estado final.

### 11.2. Exclusão concorrente com publicação

Se a publicação confirma primeiro, a exclusão encontra o installment `PUBLISHED` e é rejeitada.

O conteúdo permanece preservado.

### 11.3. Substituições concorrentes de capas

`CatalogService.setCover()` utiliza:

```text
WorkRepository.findByIdForUpdate()
```

O teste relevante é:

```text
shouldWaitForWorkLockBeforeStoringReplacementCover()
```

O cenário utiliza transações independentes.

Uma transação mantém a obra bloqueada enquanto outra tenta substituir a capa.

O teste utiliza `WatchService` para observar a criação de arquivos.

A nova capa não deve ser armazenada antes da liberação do bloqueio.

### 11.4. Concorrência entre metadados e capa

O teste:

```text
shouldPreserveCoverWhenMetadataUpdateCommitsConcurrently()
```

verifica que atualização de metadados e substituição de capa utilizam coordenação sobre a mesma entidade.

O estado final precisa preservar:

- título atualizado;
- referência da nova capa;
- `Content-Type`;
- conteúdo binário;
- ausência de arquivos antigos indevidos.

O objetivo é evitar *lost updates*.

### 11.5. Concorrência entre remoção e substituição de capas

O teste:

```text
shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()
```

verifica:

1. remoção iniciada em transação aberta;
2. bloqueio da obra;
3. tentativa concorrente de substituição;
4. espera pela liberação;
5. commit da remoção;
6. persistência da nova capa.

O estado final deve preservar apenas a nova capa.

O teste utiliza transações independentes, `CountDownLatch`, `Future` e verificações no PostgreSQL e filesystem.

### 11.6. Concorrência entre exclusão de obras e criação de installments

A exclusão e a criação de installments compartilham o bloqueio da obra.

**Exclusão confirma primeiro**

O teste `shouldRejectInstallmentCreationWhenWorkDeletionCommitsFirst()` verifica que nenhum installment órfão é persistido.

**Criação confirma primeiro**

O teste `shouldRejectWorkDeletionWhenInstallmentCreationCommitsFirst()` verifica que a exclusão encontra o installment e é rejeitada.

A foreign key `fk_installment_work` fornece proteção adicional.

### 11.7. Concorrência no início da análise de denúncias

O módulo `moderation` utiliza bloqueio pessimista durante as transições administrativas.

Classe:

```text
ModerationStartReviewConcurrencyIntegrationTests
```

Uma denúncia inicialmente `OPEN` pode receber tentativas concorrentes de início de análise.

O teste verifica que a coordenação impede a confirmação válida de duas transições baseadas no mesmo estado anterior.

Após a primeira transação confirmar `UNDER_REVIEW`, a segunda precisa observar o estado atualizado.

A operação incompatível é rejeitada.

A auditoria não deve registrar uma transição adicional indevida.

### 11.8. Concorrência na resolução e restauração

As operações de resolução utilizam bloqueio da denúncia.

Quando a decisão altera a visibilidade, o catálogo também bloqueia a obra.

A restauração valida o estado da denúncia e a visibilidade atual.

Os testes de integração verificam que operações incompatíveis não produzem estados finais parcialmente persistidos.

Uma restauração duplicada é rejeitada e não deve produzir auditoria adicional.

### 11.9. Por que utilizar PostgreSQL real?

Mocks verificam interações, mas não reproduzem integralmente:

- aquisição de bloqueios;
- espera entre transações;
- isolamento;
- visibilidade após commit;
- comportamento das foreign keys;
- restrições de unicidade;
- persistência concorrente;
- semântica de `ILIKE`;
- agrupamento e paginação.

Os testes unitários verificam a utilização dos métodos de repository.

Os testes de integração verificam o comportamento transacional efetivo.

### 11.10. Limites dos testes

Os cenários exercitam situações específicas.

Não representam prova exaustiva de ausência de problemas concorrentes.

Testes com:

```text
CountDownLatch
Future
WatchService
```

e janelas temporais precisam ser interpretados considerando diferenças de escalonamento e desempenho.

As verificações do estado final complementam as observações temporais.

Novos testes devem ser adicionados quando surgirem riscos concretos.

## 12. Isolamento dos testes

Os testes devem evitar dependência de dados deixados por execuções anteriores.

Cada cenário precisa preparar suas condições iniciais.

Arquivos utilizados em testes devem permanecer separados dos arquivos normais da aplicação.

Os testes de integração de capas utilizam:

```properties
metopa.storage.root=target/test-work-cover-storage
```

Os testes de descoberta pública utilizam:

```properties
metopa.storage.root=target/test-work-discovery-storage
```

As verificações devem considerar PostgreSQL e filesystem.

A migration V10 exige referências válidas de obras nos registros dependentes.

As migrations de moderação acrescentam outras regras de integridade que também precisam ser respeitadas.

### 12.1. Isolamento da descoberta pública

`PublishedWorkDiscoveryIntegrationTests` cria os usuários, obras, installments e páginas necessários.

Para os filtros por título, utiliza termos exclusivos, normalmente associados a UUIDs.

Isso permite validar contagens sem exigir banco completamente vazio.

Nos testes de paginação, timestamps são ajustados explicitamente para estabelecer uma ordem conhecida.

Os testes de listagem geral também consideram a quantidade anterior de obras publicadas.

Entretanto, não existe garantia de isolamento completo por banco descartável em todos os cenários.

### 12.2. Isolamento da moderação

Os testes de moderação devem preparar suas próprias denúncias e obras.

Quando um teste exige estado `UNDER_REVIEW`, a transição necessária deve ser preparada explicitamente.

Quando exige uma obra `RESTRICTED`, o cenário deve estabelecer esse estado antes da verificação.

Testes de autenticação administrativa precisam preparar contas e papéis de acesso compatíveis.

As verificações de auditoria devem considerar apenas os registros relacionados ao cenário.

Testes de restauração não devem depender de restrições criadas em execuções anteriores.

### Recomendações

- evitar dependência da ordem de execução;
- não reutilizar identificadores de outro teste;
- preparar explicitamente os dados;
- respeitar foreign keys;
- utilizar armazenamento de teste;
- verificar estado final persistido;
- limpar recursos quando apropriado;
- evitar sleeps arbitrários;
- utilizar sincronização controlada;
- utilizar timeouts;
- liberar bloqueios;
- encerrar executores em `finally`;
- utilizar marcadores exclusivos;
- não pressupor banco vazio;
- evitar ordenação baseada em timestamps muito próximos;
- criar denúncias independentes;
- preparar explicitamente os papéis de acesso;
- verificar o histórico de auditoria;
- preservar dados utilizados por outros cenários.

## 13. Diagnóstico de falhas

Quando um teste falhar, a investigação deve começar pelo comportamento esperado.

### 13.1. Falhas de validação

Verificar:

- dados de entrada;
- normalização;
- comandos;
- regras de domínio;
- enums;
- transições;
- respostas HTTP.

### 13.2. Falhas de persistência

Verificar:

- PostgreSQL;
- datasource;
- Flyway;
- constraints;
- fronteiras transacionais;
- estado persistido;
- integridade referencial;
- SQL nativo;
- consultas de conteúdo e contagem.

Após a V10, verificar se o `workId` utilizado existe.

Após as migrations V11–V18, verificar também as constraints de moderação, papéis e visibilidade.

### 13.3. Falhas de autenticação e autorização

Verificar:

- sessão autenticada;
- papel do usuário;
- autoridades carregadas;
- `ROLE_ADMIN`;
- propriedade da obra;
- CSRF;
- código esperado.

Um usuário autenticado com papel regular não deve receber autorização administrativa.

### 13.4. Falhas de armazenamento

Verificar:

- diretório configurado;
- criação do arquivo;
- `storageKey`;
- leitura;
- exclusão;
- callbacks;
- resultado da transação;
- referências persistidas.

Na remoção de capas, confirmar o momento da exclusão física.

Na exclusão de obras, confirmar que a limpeza ocorreu após commit.

### 13.5. Falhas de concorrência

Verificar:

- bloqueio pessimista;
- transações independentes;
- ordem dos eventos;
- commit e rollback;
- estado final;
- restrições PostgreSQL;
- referências desatualizadas;
- arquivos órfãos;
- auditorias duplicadas;
- transições inválidas.

Na moderação, verificar o estado da denúncia antes e depois da tentativa concorrente.

### 13.6. Falhas de tratamento HTTP

Verificar:

- rota;
- método;
- controller;
- parâmetros;
- exceção;
- `@RestControllerAdvice`;
- handlers locais;
- `ProblemDetail`;
- código esperado.

Na exclusão de obras, `WorkHasInstallmentsException` é tratado por `CatalogExceptionHandler`, retornando `409 Conflict`.

Na moderação, transições inválidas recebem tratamento HTTP de conflito.

### 13.7. Falhas na descoberta pública

Verificar:

- installments publicados;
- obras apenas com drafts;
- visibilidade `PUBLIC`;
- visibilidade `RESTRICTED`;
- agrupamento por `work_id`;
- filtros antes da paginação;
- consulta de contagem;
- obras distintas;
- UUIDs;
- gêneros;
- normalização do título;
- escape de `%`, `_` e `!`;
- ordenação por `MAX(updated_at)`;
- desempate por UUID;
- metadados da resposta.

Para falhas relacionadas à moderação, verificar se a visibilidade foi efetivamente persistida antes da consulta pública.

### 13.8. Falhas de moderação

Quando um teste de moderação falhar, verificar:

- existência da denúncia;
- estado inicial;
- decisão anterior;
- justificativa;
- identificação do administrador;
- papel `ADMIN`;
- auditoria;
- bloqueio pessimista;
- transação;
- visibilidade atual da obra.

#### Falha no início de análise

Confirmar que a denúncia estava `OPEN`.

Uma denúncia `UNDER_REVIEW` não pode iniciar novamente a mesma análise.

#### Falha na resolução

Confirmar que a denúncia estava `UNDER_REVIEW`.

Verificar a decisão informada e a visibilidade da obra.

#### Falha na restauração

Confirmar:

- denúncia `RESOLVED`;
- decisão `WORK_RESTRICTED`;
- obra `RESTRICTED`;
- ausência de restauração anterior incompatível.

#### Falha de auditoria

Verificar se a ação esperada foi persistida somente após uma operação válida.

Uma operação rejeitada não deve criar auditoria indevida.

### 13.9. Falhas na integração contínua

Quando um Pull Request falhar na CI, verificar:

- compilação;
- testes;
- empacotamento;
- inicialização do PostgreSQL;
- datasource;
- migrations;
- diferenças entre ambientes;
- dados residuais;
- arquivos de teste;
- concorrência;
- dependências;
- configuração do Maven.

O log do GitHub Actions deve indicar a etapa afetada.

Quando necessário, execute a classe individualmente antes da suíte completa.

## 14. Critérios de conclusão de uma alteração

Antes de concluir uma feature ou correção no backend, recomenda-se verificar:

- [ ] Regras de negócio implementadas.
- [ ] Testes relevantes criados ou atualizados.
- [ ] Testes relacionados aprovados.
- [ ] Cenários de erro considerados.
- [ ] Autenticação e autorização verificadas.
- [ ] CSRF considerado nas operações de escrita.
- [ ] Efeitos persistidos verificados.
- [ ] Rollback analisado quando necessário.
- [ ] Integridade referencial verificada.
- [ ] Concorrência considerada quando relevante.
- [ ] Consultas e paginação verificadas.
- [ ] Transições de estado validadas.
- [ ] Auditoria verificada quando aplicável.
- [ ] Visibilidade pública validada.
- [ ] Limites modulares preservados.
- [ ] Suíte completa executada com `./mvnw clean verify`.
- [ ] Documentação atualizada.
- [ ] `git diff --check` sem problemas.
- [ ] Pull Request preparada.
- [ ] Checks obrigatórios aprovados.
- [ ] CI após merge verificada quando aplicável.

Para alterações exclusivamente documentais, a execução completa da suíte Java não é obrigatória quando nenhum código, teste ou arquivo de configuração de execução foi modificado.

## 15. Evolução da estratégia

A estratégia de testes deve acompanhar o desenvolvimento do MVP.

Novas funcionalidades podem exigir:

- novos testes de integração;
- testes de concorrência;
- contratos HTTP;
- cenários de segurança;
- integridade referencial;
- falhas de armazenamento;
- reconciliação de arquivos;
- testes de modularidade;
- desempenho de consultas;
- planos de execução PostgreSQL;
- estratégias de indexação;
- maior isolamento de banco;
- novas verificações na CI.

### Próxima etapa: reconciliação de arquivos órfãos

A próxima funcionalidade planejada é a reconciliação de arquivos órfãos.

A estratégia de testes deverá considerar:

- arquivos com referências válidas;
- arquivos sem referências;
- capas e páginas;
- arquivos pertencentes a obras `RESTRICTED`;
- arquivos criados durante transações ainda abertas;
- uploads em andamento;
- falhas de exclusão;
- repetição segura da limpeza;
- preservação de arquivos válidos;
- integridade entre PostgreSQL e filesystem.

Um arquivo pertencente a uma obra restrita continua válido quando possui referência persistida.

A reconciliação não deve confundir indisponibilidade pública com ausência de referência.

A implementação ainda precisa ser definida e não faz parte da suíte atual.

### Evoluções futuras

Também poderão ser avaliados:

- otimizações de consulta;
- índices PostgreSQL;
- novos filtros de descoberta;
- novos fluxos administrativos;
- testes de desempenho;
- infraestrutura de armazenamento diferente;
- estratégias de limpeza durável;
- maior isolamento dos testes;
- verificações adicionais de observabilidade.

A prioridade é testar comportamentos importantes e riscos identificados, evitando adicionar testes apenas para aumentar a contagem total.

## Documentação relacionada

- [README — Visão geral do projeto](../README.md)
- [API HTTP](API.md)
- [Arquitetura](ARCHITECTURE.md)
