# Metopa — Estratégia de Testes

Documentação da estratégia de testes automatizados do backend do Metopa.

Os testes são utilizados para validar regras de domínio, persistência, autenticação, autorização, contratos HTTP, modularidade, armazenamento binário, consistência transacional e concorrência.

O projeto utiliza Java 25, Spring Boot 4.1, JUnit, Mockito, Spring Test e PostgreSQL.

## 1. Estado atual

A última execução completa reportada da suíte apresentou:

```text
Tests run: 273
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A execução foi realizada em **2 de novembro de 2026**, através do comando:

```bash
./mvnw clean test
```

O tempo total registrado foi de aproximadamente 3 minutos e 24 segundos.

A referência anterior era de 258 testes aprovados, estabelecida em 29 de outubro de 2026, após a implementação da `feature/creator-cover-removal`.

A execução atual contempla os 15 testes de integração introduzidos durante o desenvolvimento da `feature/creator-work-deletion`.

Entre os novos comportamentos validados estão:

- exclusão de obras sem installments;
- rejeição da exclusão de obras com installments `DRAFT`;
- rejeição da exclusão de obras com installments `PUBLISHED`;
- exclusão física da capa após o commit;
- preservação da obra e da capa após rollback;
- autorização por propriedade da obra;
- rejeição de operações sem autenticação;
- proteção CSRF;
- tratamento de obras inexistentes com HTTP `404`;
- tratamento de obras com installments com HTTP `409`;
- concorrência entre exclusão de obras e criação de installments;
- exclusão de referências de biblioteca e progresso de leitura;
- proteção contra exclusão direta de obras com installments no PostgreSQL;
- exclusão dos gêneros associados à obra.

A suíte também preserva os testes introduzidos nas features anteriores de consistência transacional e remoção de capas, incluindo:

- compensação de uploads de capas após rollback;
- remoção da capa anterior após commit;
- coordenação de substituições concorrentes;
- proteção contra conflitos entre atualização de metadados e substituição de capa;
- remoção idempotente de capas em obras que já não possuem capa;
- concorrência entre remoção e substituição de capas.

A migration V10 introduziu novas foreign keys entre `work` e as tabelas `installment`, `library_entry` e `reading_progress`.

Como consequência, quatro classes de testes de repositório foram ajustadas para preparar registros válidos de obras antes de persistir entidades dependentes:

- `InstallmentRepositoryTests`;
- `PageRepositoryTests`;
- `LibraryEntryRepositoryTests`;
- `ReadingProgressRepositoryTests`.

A suíte utiliza diferentes níveis de teste para validar tanto o comportamento isolado das classes quanto o funcionamento integrado da aplicação.

O número de testes não é utilizado isoladamente como indicador de qualidade. O objetivo principal é verificar comportamentos relevantes, regras de negócio e possíveis regressões.

## 2. Objetivos

A estratégia de testes busca:

- verificar as regras de negócio;
- identificar regressões;
- garantir a integridade dos dados;
- validar os contratos HTTP;
- verificar autenticação e autorização;
- preservar os limites arquiteturais;
- validar operações com arquivos binários;
- verificar comportamentos transacionais;
- testar situações de concorrência;
- verificar restrições de integridade referencial;
- permitir a evolução incremental do MVP.

O Metopa utiliza testes automatizados como parte do fluxo de desenvolvimento, especialmente em alterações que envolvem persistência e operações destrutivas.

## 3. Tipos de teste

### 3.1. Testes unitários

Os testes unitários verificam comportamentos específicos de classes e componentes, geralmente sem iniciar toda a aplicação Spring.

O Mockito é utilizado para simular dependências quando necessário.

Esses testes são adequados para:

- validações de comandos;
- normalização de dados;
- regras de domínio;
- tratamento de conflitos;
- interações entre serviços e suas dependências;
- verificação de chamadas a repositories;
- verificação das operações realizadas através de `BinaryStorage`;
- verificação do registro e comportamento de callbacks transacionais.

Os testes unitários permitem identificar problemas rapidamente e fornecer feedback durante o desenvolvimento.

Entretanto, não substituem os testes de integração quando o comportamento depende de transações reais, consultas SQL ou configuração do Spring.

No módulo `catalog`, os testes também verificam a utilização de `findByIdForUpdate()` nas operações de escrita sobre obras.

Essa verificação confirma a interação com o repository, mas não demonstra, isoladamente, a aquisição de bloqueios no PostgreSQL.

Os testes unitários da remoção de capas verificam que:

- a referência da capa é removida da entidade;
- a exclusão física é solicitada após commit;
- a exclusão física não é solicitada após rollback;
- o serviço utiliza o repository com bloqueio pessimista.

A implementação da exclusão de obras introduziu a dependência `WorkDeletionGuard` no `CatalogService`.

Os testes unitários existentes foram ajustados para fornecer essa dependência ao serviço.

Os testes de `PublicationService` também foram atualizados para refletir o uso de `CatalogService.findWorkForInstallmentCreation()` na criação de installments.

Essas adaptações verificam as interações com os novos contratos, enquanto os testes de integração validam o comportamento transacional efetivo.

A reversão real dos dados persistidos é verificada separadamente pelos testes de integração.

### 3.2. Testes de integração

Os testes de integração verificam o comportamento conjunto de diferentes componentes da aplicação.

Conforme o cenário, utilizam:

- contexto real do Spring Boot;
- endpoints Spring MVC;
- autenticação e sessões HTTP;
- proteção CSRF;
- persistência real no PostgreSQL;
- transações reais;
- armazenamento binário no filesystem local.

Esses testes são especialmente relevantes para funcionalidades cujo comportamento depende da integração entre o banco, a aplicação e o armazenamento físico.

Os testes de consistência de capas verificam simultaneamente o estado persistido no PostgreSQL e a presença dos arquivos no filesystem.

Na remoção de capas, os testes verificam tanto a exclusão confirmada quanto a preservação dos dados e arquivos em cenários de rollback ou operações não autorizadas.

Na exclusão de obras, os testes verificam:

- a remoção efetiva do registro da obra;
- a preservação de obras com installments;
- o comportamento da capa física após commit e rollback;
- as restrições relacionais do PostgreSQL;
- a coordenação entre transações concorrentes de exclusão e criação de installments;
- a remoção das referências associadas à obra excluída.

Esses cenários não poderiam ser reproduzidos integralmente apenas com mocks dos repositories.

### 3.3. Testes de contratos HTTP

Os testes HTTP verificam o comportamento observado pelo cliente da aplicação.

Entre os aspectos validados estão:

- rotas e métodos HTTP;
- formatos de requisição;
- validações de entrada;
- autenticação;
- autorização;
- proteção CSRF;
- códigos de resposta;
- representações retornadas;
- persistência dos efeitos das operações.

O Spring MockMvc é utilizado nos testes que exercitam a camada HTTP sem exigir um servidor externo iniciado manualmente.

O endpoint de remoção de capas utiliza:

```http
DELETE /api/works/{workId}/cover
```

Os testes verificam as respostas `204 No Content`, `401 Unauthorized`, `403 Forbidden` e `404 Not Found`, conforme o cenário.

O endpoint de exclusão de obras utiliza:

```http
DELETE /api/works/{workId}
```

Os testes verificam as respostas:

- `204 No Content` quando o proprietário exclui uma obra sem installments;
- `401 Unauthorized` quando não existe sessão autenticada;
- `403 Forbidden` quando o usuário não é proprietário ou a requisição não possui CSRF válido;
- `404 Not Found` quando a obra não existe;
- `409 Conflict` quando a obra possui installments.

O conflito de exclusão utiliza `ProblemDetail` com os seguintes campos:

```text
Title:  Work cannot be deleted
Detail: A work with installments cannot be deleted.
```

A verificação do contrato HTTP é complementada pela consulta ao estado persistido após cada operação.

### 3.4. Testes de modularidade

O Spring Modulith é utilizado para verificar os limites arquiteturais da aplicação.

Esses testes procuram identificar dependências inadequadas entre os módulos definidos no projeto.

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

O objetivo é preservar o encapsulamento dos domínios e impedir que mudanças introduzam acoplamentos incompatíveis com a arquitetura modular.

A exclusão de obras utiliza o contrato `WorkDeletionGuard`, definido pelo módulo `catalog` e implementado em `publication.internal`.

Essa abordagem permite verificar a existência de installments sem introduzir uma dependência direta de `CatalogService` sobre `InstallmentRepository`.

O teste de modularidade ajuda a identificar regressões nessa organização.

## 4. Executando os testes

### 4.1. Pré-requisitos

Para executar a suíte completa, o ambiente deve possuir:

- JDK 25;
- Docker e Docker Compose;
- PostgreSQL disponível conforme a configuração local;
- Maven Wrapper do projeto.

O Maven Wrapper já está incluído no repositório.

### 4.2. Iniciar o PostgreSQL

Na raiz do projeto:

```bash
docker compose up -d
```

Verifique os containers:

```bash
docker compose ps
```

Os testes de integração que utilizam o banco dependem de uma instância PostgreSQL acessível com as configurações esperadas pela aplicação.

O Spring Boot também pode gerenciar o ciclo de vida do Docker Compose conforme a configuração da aplicação.

Na última execução de um teste individual, o PostgreSQL foi iniciado pelo Spring Boot e a migration V10 foi validada pelo Flyway.

### 4.3. Executar a suíte completa

```bash
./mvnw clean test
```

O comando remove os artefatos anteriores de build, compila o projeto e executa os testes configurados para a fase `test` do Maven.

O resultado deve ser verificado no terminal.

A execução completa é a validação principal antes da conclusão de alterações no código da aplicação.

O resultado mais recente reportado foi:

```text
Tests run: 273, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 4.4. Executar uma classe específica

Durante o desenvolvimento, é possível executar apenas a classe relacionada à funcionalidade modificada.

Exemplo:

```bash
./mvnw -Dtest=PublicationServiceTests test
```

Para os testes de integração de upload de páginas:

```bash
./mvnw -Dtest=PageUploadIntegrationTests test
```

Para os testes unitários do módulo `catalog`:

```bash
./mvnw -Dtest=CatalogServiceTests test
```

Para os testes HTTP do controller de capas:

```bash
./mvnw -Dtest=WorkCoverControllerTests test
```

Para os testes de integração de capas:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests test
```

Para os testes de integração de exclusão de obras:

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests test
```

Também é possível executar um método específico.

Para verificar o rollback da substituição de capas:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests#shouldRemoveNewCoverWhenReplacementRollsBack test
```

Para executar o teste de remoção de capa existente:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests#shouldRemoveExistingCoverForWorkOwner test
```

Para executar o teste de rollback da remoção:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests#shouldPreserveCoverWhenRemovalRollsBack test
```

Para executar o teste de concorrência entre remoção e substituição:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests#shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently test
```

Para verificar o callback de remoção após commit:

```bash
./mvnw -Dtest=CatalogServiceTests#shouldDeleteCoverFileAfterRemovalCommit test
```

Para verificar a preservação do arquivo após rollback:

```bash
./mvnw -Dtest=CatalogServiceTests#shouldPreserveCoverFileAfterRemovalRollback test
```

Para executar somente o cenário de exclusão de obra sem installments:

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests#shouldDeleteEmptyWorkForOwner test
```

Para verificar a rejeição da exclusão de uma obra com installment `DRAFT`:

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests#shouldRejectDeletingWorkWithDraftInstallment test
```

Para verificar a preservação da obra e da capa após rollback:

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests#shouldPreserveWorkAndCoverWhenDeletionRollsBack test
```

Para executar o teste da restrição de integridade referencial:

```bash
./mvnw -Dtest=WorkDeletionIntegrationTests#shouldPreventDeletingWorkWithInstallmentsAtDatabaseLevel test
```

Essa abordagem permite obter feedback mais rápido durante as etapas de implementação.

Após a correção, a suíte completa deve ser executada para identificar possíveis regressões em outros módulos.

### 4.5. Relatórios de execução

O Maven Surefire disponibiliza relatórios em:

```text
target/surefire-reports/
```

Esses relatórios ajudam a identificar:

- testes executados;
- falhas;
- erros;
- testes ignorados;
- detalhes das exceções;
- resultados individuais das classes.

Em caso de falha, a primeira etapa é identificar o teste afetado e compreender qual comportamento esperado deixou de ser atendido.

## 5. Estratégia de desenvolvimento

O desenvolvimento de novas funcionalidades e correções utiliza incrementos pequenos.

Quando adequado, o fluxo segue as etapas:

```text
RED
  ↓
GREEN
  ↓
REFACTOR
```

### 5.1. RED

Primeiro, é criado um teste que reproduz o comportamento esperado.

Antes da implementação, esse teste deve falhar por causa da ausência do comportamento desejado.

A falha confirma que o teste consegue identificar o problema.

### 5.2. GREEN

Em seguida, é implementada a menor alteração necessária para satisfazer o teste.

A prioridade é corrigir o comportamento e preservar os contratos existentes.

### 5.3. REFACTOR

Após a aprovação dos testes, a implementação é revisada.

São avaliados aspectos como:

- clareza dos métodos;
- responsabilidades;
- duplicação;
- encapsulamento;
- nomes;
- necessidade real de novas abstrações;
- respeito às fronteiras dos módulos.

O refactor não deve alterar o comportamento observado pelos testes.

### 5.4. Validação final

Depois da implementação e da revisão:

```bash
./mvnw clean test
```

A suíte completa ajuda a verificar se a alteração introduziu regressões em funcionalidades existentes.

### 5.5. Exemplo: consistência transacional das capas

A `feature/cover-transaction-consistency` foi desenvolvida através de quatro ciclos RED → GREEN.

**Primeiro ciclo — rollback de substituição**

O teste identificou que o arquivo da capa substituta permanecia armazenado mesmo depois de o PostgreSQL reverter a transação.

A implementação passou a utilizar `TransactionSynchronization.afterCompletion()` para remover a nova capa quando a transação termina com `STATUS_ROLLED_BACK`.

**Segundo ciclo — commit de substituição**

O teste identificou que a capa anterior permanecia armazenada depois de uma substituição confirmada.

A implementação passou a utilizar `TransactionSynchronization.afterCommit()` para remover o arquivo anterior após o commit.

**Terceiro ciclo — substituições concorrentes**

O teste identificou a criação de um novo arquivo enquanto outra transação mantinha a obra bloqueada.

A implementação passou a utilizar `PESSIMISTIC_WRITE` antes de realizar o armazenamento da nova capa.

**Quarto ciclo — metadados e capa**

O teste identificou uma situação na qual a atualização dos metadados sobrescrevia a referência de uma capa substituída por outra transação.

A implementação passou a coordenar `updateWork()` e `setCover()` através do mesmo bloqueio pessimista sobre a entidade `Work`.

Depois desses ciclos, a suíte completa foi executada com 248 testes aprovados.

### 5.6. Exemplo: remoção de capas

A `feature/creator-cover-removal` foi desenvolvida de forma incremental, acrescentando testes para os comportamentos funcionais, transacionais, de segurança e de concorrência.

**Primeiro cenário — remoção de capa existente**

O método de teste `shouldRemoveExistingCoverForWorkOwner()` foi criado para verificar a exclusão de uma capa existente pelo proprietário da obra.

Durante o RED, o endpoint retornava `405 Method Not Allowed`, pois o método `DELETE` ainda não estava implementado.

A implementação introduziu:

- `DELETE /api/works/{workId}/cover`;
- `CatalogService.removeCover()`;
- `Work.clearCover()`;
- limpeza física após commit.

**Segundo cenário — rollback da remoção**

O teste `shouldPreserveCoverWhenRemovalRollsBack()` verifica que a referência original e o arquivo físico permanecem preservados quando a transação sofre rollback.

**Terceiro cenário — idempotência**

O teste `shouldRemoveCoverIdempotentlyWhenWorkHasNoCover()` verifica que uma obra existente sem capa pode receber chamadas repetidas de remoção, retornando `204 No Content`.

**Quarto cenário — propriedade da obra**

O teste `shouldRejectCoverRemovalFromAnotherUser()` verifica que outro usuário autenticado recebe `403 Forbidden` e não consegue alterar a referência nem remover o arquivo físico da capa.

**Quinto cenário — autenticação**

O teste `shouldRejectUnauthenticatedCoverRemoval()` verifica que uma requisição sem sessão autenticada recebe `401 Unauthorized`.

**Sexto cenário — proteção CSRF**

O teste `shouldRequireCsrfForCoverRemoval()` verifica que uma requisição autenticada sem token CSRF válido recebe `403 Forbidden`.

**Sétimo cenário — obra inexistente**

O teste `shouldReturnNotFoundWhenRemovingCoverFromNonexistentWork()` identificou uma exceção `WorkNotFoundException` não traduzida pela camada HTTP.

A implementação passou a tratar essa exceção em `CatalogExceptionHandler`, retornando `404 Not Found` através de `ProblemDetail`.

**Oitavo cenário — concorrência com substituição**

O teste `shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()` verifica o comportamento de duas transações independentes, uma removendo a capa e outra realizando sua substituição.

O resultado esperado é preservar apenas a nova capa e sua referência correspondente no PostgreSQL.

**Nono cenário — callback de commit**

O teste unitário `shouldDeleteCoverFileAfterRemovalCommit()` verifica que a remoção solicita a exclusão física da capa depois do commit.

**Décimo cenário — callback de rollback**

O teste unitário `shouldPreserveCoverFileAfterRemovalRollback()` verifica que a exclusão física não é solicitada quando a remoção sofre rollback.

Depois desses cenários, a suíte completa foi executada com 258 testes aprovados.

### 5.7. Exemplo: exclusão de obras

A `feature/creator-work-deletion` foi desenvolvida incrementalmente, utilizando testes para orientar as regras de domínio, a autorização, a consistência transacional e o comportamento concorrente.

A classe principal é `WorkDeletionIntegrationTests`.

**Primeiro conjunto — regras funcionais**

Os testes iniciais verificaram que o proprietário consegue excluir uma obra sem installments e que a exclusão é rejeitada quando existem installments `DRAFT` ou `PUBLISHED`.

A implementação introduziu:

- `DELETE /api/works/{workId}`;
- `WorkDeletionController`;
- `CatalogService.deleteWork()`;
- `WorkDeletionGuard`;
- `PublicationWorkDeletionGuard`;
- `WorkHasInstallmentsException`;
- tratamento HTTP `409 Conflict`.

O contrato `WorkDeletionGuard` permite que `catalog` consulte informações pertencentes a `publication` sem acessar diretamente seu repository interno.

**Segundo conjunto — consistência transacional**

Os testes verificaram que a capa física é removida somente após a confirmação da exclusão da obra.

Também foi criado um cenário de rollback que garante a preservação da obra, de sua referência de capa e do arquivo físico.

A implementação utiliza `TransactionSynchronization.afterCommit()` para solicitar a exclusão do arquivo depois do commit.

**Terceiro conjunto — segurança**

Os testes verificaram:

- rejeição de exclusão por outro usuário autenticado;
- rejeição de requisição não autenticada;
- rejeição de requisição sem CSRF válido;
- resposta `404 Not Found` para uma obra inexistente.

**Quarto conjunto — concorrência**

Foram implementados testes para os dois possíveis ordenamentos de commit entre:

- exclusão de uma obra;
- criação de um installment na mesma obra.

Para coordenar esses fluxos, `PublicationService.createInstallment()` passou a utilizar `CatalogService.findWorkForInstallmentCreation()`.

O método exige uma transação existente e adquire bloqueio pessimista sobre a obra.

**Quinto conjunto — integridade referencial**

A migration V10 introduziu foreign keys para impedir a exclusão de obras com installments e remover referências dependentes quando uma obra é excluída.

Os testes verificam:

- remoção de entradas de biblioteca;
- remoção de registros de progresso de leitura;
- rejeição de exclusão direta no PostgreSQL quando existem installments;
- remoção de gêneros associados à obra.

A nova integridade referencial também exigiu ajustes nos testes de repositório que anteriormente utilizavam identificadores de obras inexistentes.

Após essas alterações, a suíte completa foi executada com **273 testes aprovados**.

## 6. Cobertura funcional

Os testes estão distribuídos entre os domínios e responsabilidades da aplicação.

### 6.1. Identity

Os testes verificam comportamentos relacionados a:

- criação de contas;
- validação de campos obrigatórios;
- normalização de username e e-mail;
- unicidade de username e e-mail;
- persistência dos usuários;
- criação de identificadores;
- armazenamento seguro de senhas;
- autenticação;
- sessões HTTP;
- login e logout;
- consulta do usuário autenticado;
- respostas apropriadas para credenciais inválidas.

### 6.2. Catalog

Os testes verificam:

- criação de obras;
- associação com o proprietário autenticado;
- normalização e validação dos metadados;
- atualização dos metadados pelo proprietário;
- rejeição de alterações não autorizadas;
- persistência de gêneros;
- substituição dos gêneros existentes;
- listagem das obras do criador;
- consulta administrativa de obras;
- isolamento entre usuários;
- definição e leitura de capas;
- autorização para acesso a capas;
- exposição de informações públicas e administrativas;
- compensação de uploads de capas após rollback;
- remoção de capas anteriores após commit;
- substituições concorrentes de capas;
- concorrência entre atualizações de metadados e substituições de capas;
- remoção de capas pelo proprietário;
- remoção idempotente de capas em obras sem capa;
- limpeza física após commit de remoção;
- preservação de referências e arquivos após rollback de remoção;
- rejeição de remoções não autorizadas;
- proteção CSRF nas operações de remoção;
- resposta `404 Not Found` para obras inexistentes;
- concorrência entre remoção e substituição de capas;
- exclusão de obras sem installments;
- rejeição de exclusão de obras com installments `DRAFT` ou `PUBLISHED`;
- autorização e proteção CSRF na exclusão de obras;
- tratamento do conflito de exclusão com `409 Conflict`;
- remoção física de capas após exclusão confirmada de obras;
- preservação de obras e capas após rollback;
- coordenação entre exclusão de obras e criação de installments;
- remoção das referências de gêneros, biblioteca e progresso de leitura;
- integridade referencial da exclusão de obras no PostgreSQL.

A integração com PostgreSQL verifica a persistência real dos dados e de suas associações.

Os testes de capas também utilizam o filesystem para verificar que os arquivos físicos permanecem consistentes com as referências persistidas.

### 6.3. Publication

Os testes verificam:

- criação de installments;
- unicidade de tipo e número;
- atualização de metadados;
- restrições do estado `DRAFT`;
- imutabilidade de installments `PUBLISHED`;
- criação de páginas;
- upload multipart;
- armazenamento e leitura de arquivos;
- numeração e ordenação de páginas;
- reordenação;
- exclusão de páginas;
- exclusão de installments;
- publicação;
- leitura pública de conteúdo publicado;
- consultas administrativas do criador;
- validações de propriedade;
- transações e rollback;
- concorrência entre operações;
- consulta da existência de installments para impedir a exclusão de obras;
- utilização do bloqueio da obra durante a criação de installments;
- preservação dos installments quando a exclusão da obra é rejeitada;
- prevenção de referências a obras inexistentes.

Esse módulo concentra alguns dos testes de integração mais relevantes para a consistência entre o banco de dados e o filesystem.

### 6.4. Library

Os testes verificam:

- inclusão de obras na biblioteca;
- restrição a obras com conteúdo publicado;
- prevenção de duplicidade;
- listagem das entradas do usuário;
- isolamento entre bibliotecas;
- ordenação das entradas;
- remoção idempotente;
- integridade referencial com a tabela `work`;
- exclusão em cascata de entradas quando uma obra é excluída.

A exclusão em cascata é validada pelo teste `WorkDeletionIntegrationTests.shouldRemoveLibraryEntriesWhenDeletingWork()`.

### 6.5. Reading

Os testes verificam:

- registro de progresso;
- atualização da posição existente;
- consulta da última posição registrada;
- identificação da obra e do installment a partir da página;
- restrição a páginas publicadas;
- tratamento de páginas inexistentes ou não publicadas;
- independência em relação à biblioteca pessoal;
- integridade referencial com a tabela `work`;
- exclusão em cascata de progresso associado a uma obra removida.

A exclusão em cascata é validada pelo teste `WorkDeletionIntegrationTests.shouldRemoveReadingProgressWhenDeletingWork()`.

Esse cenário de integridade referencial utiliza um registro de progresso preparado para testar a restrição do banco.

No fluxo normal da aplicação, uma obra com progresso possui conteúdo publicado e, portanto, não pode ser excluída enquanto existirem installments.

### 6.6. Media

Os testes verificam:

- armazenamento de conteúdo binário;
- geração interna de `storageKey`;
- leitura de arquivos;
- exclusão;
- exclusão idempotente de arquivos inexistentes;
- proteção contra resolução de caminhos fora da raiz configurada;
- integração do armazenamento com os módulos que utilizam `BinaryStorage`;
- remoção de capas após exclusão confirmada de obras;
- preservação dos arquivos físicos quando uma exclusão de obra sofre rollback.

## 7. Segurança

Os testes de segurança verificam os mecanismos de autenticação e autorização utilizados pela aplicação.

### 7.1. Autenticação

Operações privadas exigem uma sessão autenticada.

Os testes verificam respostas apropriadas quando o usuário não está autenticado.

Na remoção de capas, uma requisição sem sessão autenticada recebe `401 Unauthorized`.

Na exclusão de obras, o teste `shouldRejectUnauthenticatedWorkDeletion()` verifica a mesma exigência.

### 7.2. Autorização

Operações administrativas sobre obras, installments, páginas e capas são restritas aos proprietários correspondentes.

Os testes verificam que um usuário não pode modificar ou consultar indevidamente o conteúdo administrativo pertencente a outro usuário.

Na remoção de capas, uma tentativa realizada por outro usuário autenticado deve retornar `403 Forbidden`, preservando os dados e o arquivo físico.

Na exclusão de obras, o teste `shouldRejectWorkDeletionFromNonOwner()` verifica que outro usuário autenticado recebe `403 Forbidden`.

O cenário também valida a preservação da obra e da capa.

A verificação de propriedade acontece antes da consulta à existência de installments.

### 7.3. CSRF

Operações protegidas que alteram estado exigem um token CSRF válido.

Os testes verificam a proteção contra requisições que não atendem a essa exigência.

O endpoint de remoção de capas é protegido por CSRF:

```http
DELETE /api/works/{workId}/cover
```

Uma tentativa de remoção autenticada sem o token exigido deve retornar `403 Forbidden`, sem modificar a capa.

O endpoint de exclusão de obras também exige CSRF:

```http
DELETE /api/works/{workId}
```

O teste `shouldRejectWorkDeletionWithoutCsrf()` verifica que a operação é rejeitada com `403 Forbidden`.

### 7.4. Respostas HTTP

Os contratos utilizam códigos adequados para diferentes situações.

Entre os códigos verificados estão:

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

Os testes também verificam a preservação dos dados quando operações inválidas ou não autorizadas são rejeitadas.

Para o endpoint de remoção de capas, os códigos esperados são:

| Situação | Resposta |
|----------|----------|
| Proprietário remove capa existente | `204 No Content` |
| Proprietário remove capa já ausente | `204 No Content` |
| Usuário não autenticado | `401 Unauthorized` |
| Usuário não é proprietário | `403 Forbidden` |
| Token CSRF ausente ou inválido | `403 Forbidden` |
| Obra inexistente | `404 Not Found` |

Para o endpoint de exclusão de obras:

| Situação | Resposta |
|----------|----------|
| Proprietário exclui obra sem installments | `204 No Content` |
| Obra possui installment `DRAFT` | `409 Conflict` |
| Obra possui installment `PUBLISHED` | `409 Conflict` |
| Usuário não autenticado | `401 Unauthorized` |
| Usuário não é proprietário | `403 Forbidden` |
| Token CSRF ausente ou inválido | `403 Forbidden` |
| Obra inexistente | `404 Not Found` |

Após a exclusão bem-sucedida de uma obra, uma nova tentativa com o mesmo identificador retorna `404 Not Found`, pois o recurso não existe mais.

O método HTTP `DELETE` continua sendo idempotente quanto ao efeito pretendido sobre o estado do recurso: executar a mesma exclusão repetidamente mantém a obra ausente.

A idempotência HTTP não exige que todas as requisições retornem o mesmo código de resposta.

### 7.5. Visão pública e administrativa

Os testes verificam a separação entre:

- conteúdo público;
- conteúdo em estado `DRAFT`;
- operações administrativas do proprietário.

Installments não publicados não devem ficar disponíveis através dos endpoints públicos.

O proprietário pode acessar conteúdos em draft através dos endpoints administrativos apropriados.

A remoção da capa é uma operação administrativa e não depende da existência de installments publicados.

Ela não altera o estado de publicação do conteúdo.

A exclusão de uma obra também é uma operação administrativa, mas só pode ocorrer quando não existem installments associados.

## 8. Testes de persistência

O PostgreSQL é utilizado nos testes de integração que dependem de comportamento relacional real.

Esses testes verificam situações que não seriam suficientemente representadas por mocks.

Entre elas:

- persistência de entidades;
- atualização de registros;
- restrições de unicidade;
- relacionamentos;
- foreign keys;
- exclusão em cascata;
- restrições de exclusão;
- rollback;
- visibilidade de alterações entre transações;
- bloqueios pessimistas.

### 8.1. Restrições de unicidade

As validações da aplicação são complementadas pelas restrições do banco.

Exemplos relevantes incluem:

- combinações únicas de tipo e número dos installments dentro de uma obra;
- números únicos de páginas dentro de um installment.

### 8.2. Exclusão em cascata

A exclusão de installments utiliza o relacionamento `ON DELETE CASCADE` existente no PostgreSQL.

Os testes verificam a remoção dos registros das páginas associadas ao installment após uma exclusão confirmada.

A exclusão física dos arquivos é validada separadamente, respeitando a estratégia de limpeza pós-commit.

A exclusão de obras também utiliza regras de integridade referencial, mas com uma distinção importante.

**Installments não são excluídos em cascata quando a obra é removida.**

A migration V10 define `ON DELETE RESTRICT` para `installment.work_id`, impedindo a remoção de obras que ainda possuem installments.

Para outras referências, são utilizadas restrições de `ON DELETE CASCADE`:

| Tabela | Restrição | Comportamento |
|--------|-----------|---------------|
| `installment` | `fk_installment_work` | Impede excluir obra com installments |
| `library_entry` | `fk_library_entry_work` | Remove entradas associadas à obra excluída |
| `reading_progress` | `fk_reading_progress_work` | Remove progresso associado à obra excluída |
| `work_genre` | Restrição introduzida na V9 | Remove gêneros associados à obra excluída |

Os testes validam essas regras utilizando PostgreSQL real.

### 8.3. Rollback

Os testes de integração verificam que alterações não confirmadas são revertidas pelo PostgreSQL.

Quando uma operação envolve arquivos físicos, a validação também considera o comportamento de `BinaryStorage`.

Na substituição de capas, os testes verificam especificamente que o rollback restaura a referência anterior no PostgreSQL e que o arquivo da nova capa é removido.

Na remoção de capas, os testes verificam que o rollback preserva a referência anterior no PostgreSQL e mantém o arquivo original disponível.

Na exclusão de obras, os testes verificam que o rollback preserva:

- o registro da obra;
- a referência da capa;
- o arquivo físico.

### 8.4. Persistência da remoção de capas

A operação `CatalogService.removeCover()` altera a entidade `Work`, removendo os valores associados à capa.

Os campos afetados são:

```text
cover_storage_key
cover_content_type
```

Após um commit bem-sucedido, ambos devem permanecer nulos no PostgreSQL.

Quando a transação sofre rollback, os valores anteriores devem permanecer persistidos.

Os testes também verificam que a remoção da capa não implica a exclusão da obra.

### 8.5. Integridade referencial da exclusão de obras

A migration V10 introduziu três foreign keys:

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

A integridade dessas relações é validada pelos seguintes testes.

**Restrição de exclusão com installments**

O teste `shouldPreventDeletingWorkWithInstallmentsAtDatabaseLevel()` cria uma obra com installment e tenta excluir diretamente o registro da obra no PostgreSQL.

A operação deve ser rejeitada por violar a foreign key `fk_installment_work`.

O teste verifica que a obra e o installment permanecem preservados.

**Remoção de entradas de biblioteca**

O teste `shouldRemoveLibraryEntriesWhenDeletingWork()` verifica que a exclusão de uma obra permitida remove as referências correspondentes em `library_entry`.

**Remoção de progresso de leitura**

O teste `shouldRemoveReadingProgressWhenDeletingWork()` prepara uma referência de progresso para verificar diretamente o comportamento da foreign key.

A exclusão da obra deve remover o registro associado em `reading_progress`.

**Remoção de gêneros**

O teste `shouldRemoveWorkGenresWhenDeletingWork()` cria uma obra com gêneros e verifica que as associações deixam de existir após sua exclusão.

A associação de gêneros já utiliza exclusão em cascata desde a migration V9.

O teste valida o resultado observado pela aplicação, embora a limpeza das associações também possa ocorrer por meio do gerenciamento da coleção JPA. Por isso, ele não demonstra isoladamente que a foreign key realizou a exclusão.

### 8.6. Ajustes nos testes de repositório após a V10

A introdução de foreign keys mudou uma premissa utilizada por alguns testes de persistência.

Anteriormente, determinados testes criavam entidades com um `workId` gerado aleatoriamente, sem criar primeiro o registro correspondente na tabela `work`.

Isso era aceito pelo schema anterior, mas passou a violar as novas restrições relacionais.

As classes afetadas foram:

```text
InstallmentRepositoryTests
PageRepositoryTests
LibraryEntryRepositoryTests
ReadingProgressRepositoryTests
```

Os testes foram ajustados para criar uma obra válida antes da persistência dos registros dependentes.

A preparação utiliza `JdbcTemplate` para inserir os dados necessários na tabela `work`.

Essa abordagem mantém o foco dos testes nos repositories correspondentes, sem exigir a criação completa de uma obra por meio dos serviços da aplicação.

Os testes continuam verificando suas responsabilidades originais, agora utilizando dados compatíveis com as restrições do banco.

Após os ajustes, os testes afetados foram executados individualmente e a suíte completa foi validada com sucesso.

## 9. Testes de armazenamento binário

Arquivos de páginas e capas são armazenados fora do PostgreSQL.

Por isso, os testes relacionados ao armazenamento não verificam apenas os registros do banco.

Quando necessário, verificam também a existência, leitura ou remoção dos arquivos físicos.

### 9.1. Upload

Os testes verificam o armazenamento de arquivos e a persistência dos metadados associados.

Os endpoints multipart são testados em integração com a camada HTTP.

### 9.2. Leitura

Os testes verificam que o conteúdo binário pode ser recuperado pelos endpoints apropriados.

Também validam:

- preservação do `Content-Type`;
- autorização do proprietário em consultas administrativas;
- indisponibilidade pública de conteúdo em draft.

### 9.3. Exclusão

Os testes verificam a remoção dos registros e o comportamento do arquivo físico correspondente.

A exclusão é validada tanto em situações de sucesso quanto em cenários de rollback.

Na substituição de capas, a limpeza física segue duas regras complementares:

- a capa anterior é removida após o commit;
- a capa recém-armazenada é removida após rollback.

Na remoção explícita de capas:

- a referência é removida no PostgreSQL;
- o arquivo original é excluído somente após commit;
- em caso de rollback, a referência e o arquivo original permanecem preservados.

Na exclusão de obras com capa:

- o registro da obra é removido no PostgreSQL;
- a chave de armazenamento da capa é capturada antes da exclusão;
- o arquivo físico é removido somente após o commit;
- em caso de rollback, a obra, a referência e o arquivo permanecem preservados.

Os testes de integração verificam que a exclusão física não acontece antes da confirmação da transação.

### 9.4. Segurança dos caminhos

A implementação de filesystem verifica a resolução dos caminhos em relação à raiz configurada.

Os testes incluem a proteção contra operações fora dessa raiz.

## 10. Testes de consistência transacional

O PostgreSQL e o filesystem não compartilham uma transação distribuída.

Por esse motivo, o Metopa utiliza estratégias complementares para operações de criação, exclusão, substituição e remoção de arquivos.

Os testes de integração são importantes para verificar o comportamento dessas estratégias.

### 10.1. Exclusão de páginas após commit

A operação `deletePage()` registra a exclusão física para execução depois do commit.

O callback utiliza `TransactionSynchronization.afterCommit()`.

Comportamento esperado:

| Resultado | Registro PostgreSQL | Arquivo físico |
|-----------|---------------------|----------------|
| Commit | Removido | Removido |
| Rollback | Preservado | Preservado |

O teste de rollback verifica que o filesystem não perde o arquivo quando a transação de exclusão é revertida.

### 10.2. Exclusão de installments após commit

A operação `deleteInstallment()` utiliza a mesma estratégia de limpeza física pós-commit.

Os registros das páginas são removidos no PostgreSQL através de `ON DELETE CASCADE`.

As referências dos arquivos são coletadas antes da exclusão.

Comportamento esperado:

| Resultado | Installment e páginas | Arquivos físicos |
|-----------|-----------------------|------------------|
| Commit | Removidos | Removidos |
| Rollback | Preservados | Preservados |

Os testes verificam tanto o comportamento transacional quanto a proteção dos arquivos físicos.

### 10.3. Compensação de upload de páginas após rollback

A operação `createPage()` armazena o arquivo físico antes da confirmação da transação PostgreSQL.

Sem compensação, um rollback poderia remover o registro da página enquanto o arquivo continuaria presente no filesystem.

A implementação utiliza `TransactionSynchronization.afterCompletion()` para remover o arquivo recém-criado quando a transação termina com `STATUS_ROLLED_BACK`.

Comportamento esperado:

| Resultado | Registro PostgreSQL | Arquivo físico |
|-----------|---------------------|----------------|
| Commit | Persistido | Preservado |
| Rollback | Não persistido | Removido |

### 10.4. Teste de regressão do upload de páginas

O cenário de regressão foi implementado na classe `PageUploadIntegrationTests`, pelo método `shouldRemoveUploadedPageFileWhenTransactionRollsBack()`.

O teste utiliza uma transação real e verifica o comportamento do banco e do filesystem.

Fluxo simplificado:

1. iniciar uma transação;
2. criar uma página;
3. verificar que o arquivo foi armazenado;
4. solicitar rollback da transação;
5. verificar que o registro da página não foi persistido;
6. verificar que o arquivo físico foi removido.

Durante a etapa RED, o teste encontrou a falha esperada:

```text
Expecting value to be false but was true
```

O resultado indicava que o arquivo continuava presente após o rollback.

Depois da implementação da compensação transacional, o teste passou.

Esse cenário protege contra a regressão do comportamento corrigido.

### 10.5. Consistência transacional de capas

A operação `CatalogService.setCover()` permite definir ou substituir a capa de uma obra.

O arquivo físico é armazenado através de `BinaryStorage`, enquanto o PostgreSQL mantém a referência correspondente.

Quando uma obra já possui uma capa, a substituição precisa coordenar três elementos:

- a referência da capa anterior;
- o arquivo recém-armazenado;
- a atualização da entidade `Work`.

A implementação utiliza `TransactionSynchronization` para executar operações compensatórias conforme o resultado da transação.

**Commit**

Quando a substituição é confirmada:

- a referência da nova capa permanece persistida;
- o arquivo da nova capa é preservado;
- o arquivo da capa anterior é removido por `afterCommit()`, quando aplicável.

**Rollback**

Quando a substituição é revertida:

- o PostgreSQL preserva a referência anterior;
- o arquivo da capa anterior permanece disponível;
- o arquivo recém-armazenado é removido por `afterCompletion(STATUS_ROLLED_BACK)`.

Comportamento esperado:

| Resultado | Referência no PostgreSQL | Capa anterior | Nova capa |
|-----------|--------------------------|---------------|-----------|
| Commit | Nova capa | Removida | Preservada |
| Rollback | Capa anterior | Preservada | Removida |

No primeiro upload de uma obra, não existe arquivo anterior para remover após o commit.

Essas garantias se referem às transações e aos callbacks que terminam normalmente. Falhas abruptas ou erros durante a limpeza física continuam sujeitos às limitações descritas na seção 10.10.

### 10.6. Testes de regressão das capas

Os principais cenários de integração são implementados em `WorkCoverIntegrationTests`.

**Rollback de substituição**

O teste `shouldRemoveNewCoverWhenReplacementRollsBack()`:

1. cria uma obra;
2. armazena a capa original;
3. inicia uma transação para substituí-la;
4. verifica a criação do arquivo da nova capa;
5. solicita rollback;
6. verifica que o PostgreSQL mantém a referência original;
7. verifica que o arquivo original permanece disponível;
8. verifica que o arquivo novo foi removido.

Durante o RED, o teste identificou dois arquivos presentes após o rollback, quando somente o arquivo original deveria permanecer.

Depois da implementação de `afterCompletion(STATUS_ROLLED_BACK)`, o teste passou.

**Commit de substituição**

O teste `shouldRemovePreviousCoverAfterSuccessfulReplacement()`:

1. cria uma obra;
2. armazena a capa original;
3. confirma a primeira operação;
4. substitui a capa em uma nova transação;
5. verifica que o PostgreSQL referencia a nova capa;
6. verifica que a nova capa pode ser lida;
7. verifica que o arquivo anterior foi removido;
8. verifica que somente o arquivo da nova capa permanece no diretório da obra.

Durante o RED, o arquivo original continuava presente após o commit.

Depois da implementação de `afterCommit()`, o teste passou.

Esses cenários validam os efeitos da operação sobre o PostgreSQL e o filesystem, e não somente os métodos chamados pela aplicação.

### 10.7. Consistência transacional da remoção de capas

A operação `CatalogService.removeCover()` permite remover a capa de uma obra pertencente ao usuário autenticado.

A implementação utiliza bloqueio pessimista sobre a entidade `Work` antes de modificar os dados.

Quando existe uma capa, o serviço:

1. adquire o bloqueio da obra;
2. valida a propriedade;
3. captura o `storageKey` anterior;
4. remove a referência e o `Content-Type` através de `Work.clearCover()`;
5. registra um callback `afterCommit()` para excluir o arquivo original.

A exclusão física não ocorre imediatamente.

**Commit**

Quando a transação é confirmada:

- a referência da capa deixa de existir no PostgreSQL;
- o `Content-Type` passa a ser `null`;
- o arquivo físico anterior é removido através de `BinaryStorage`.

**Rollback**

Quando a transação é revertida:

- o PostgreSQL preserva a referência original;
- o `Content-Type` anterior permanece persistido;
- o callback `afterCommit()` não é executado;
- o arquivo físico original permanece disponível.

Comportamento esperado:

| Resultado | Referência no PostgreSQL | Arquivo original |
|-----------|--------------------------|------------------|
| Commit | Removida | Removido após commit |
| Rollback | Preservada | Preservado |

**Obra sem capa**

Quando a obra existe, mas não possui capa, a operação retorna normalmente.

Nenhum arquivo é excluído.

A resposta HTTP permanece `204 No Content`.

**Obra inexistente**

Quando a obra não existe, a operação retorna `404 Not Found`.

A idempotência da remoção de capa aplica-se à ausência de capa em uma obra existente, não à ausência da própria obra.

#### Testes de integração da remoção

Os cenários são implementados em `WorkCoverIntegrationTests`.

**Remoção de capa existente**

O teste `shouldRemoveExistingCoverForWorkOwner()` verifica que:

- o proprietário pode remover a capa;
- a resposta HTTP é `204 No Content`;
- a referência no PostgreSQL é removida;
- o `Content-Type` é removido;
- o arquivo físico anterior é excluído após commit.

**Rollback da remoção**

O teste `shouldPreserveCoverWhenRemovalRollsBack()` utiliza `TransactionTemplate` para executar a operação dentro de uma transação real.

O teste verifica:

1. a capa original existe;
2. a remoção é executada dentro da transação;
3. o arquivo físico ainda existe antes do commit;
4. a transação é revertida;
5. a referência anterior permanece persistida;
6. o arquivo original permanece disponível.

**Remoção idempotente**

O teste `shouldRemoveCoverIdempotentlyWhenWorkHasNoCover()` verifica que chamadas repetidas à operação sobre uma obra sem capa retornam `204 No Content`.

Os campos persistidos permanecem nulos e nenhum arquivo físico é criado ou removido indevidamente.

**Usuário sem propriedade**

O teste `shouldRejectCoverRemovalFromAnotherUser()` verifica que outro usuário autenticado recebe `403 Forbidden`.

A referência e o arquivo físico permanecem preservados.

**Usuário não autenticado**

O teste `shouldRejectUnauthenticatedCoverRemoval()` verifica que uma requisição sem sessão autenticada recebe `401 Unauthorized`.

A capa permanece inalterada.

**Requisição sem CSRF**

O teste `shouldRequireCsrfForCoverRemoval()` verifica que uma requisição autenticada sem o token CSRF exigido recebe `403 Forbidden`.

A referência e o arquivo permanecem preservados.

**Obra inexistente**

O teste `shouldReturnNotFoundWhenRemovingCoverFromNonexistentWork()` verifica que uma tentativa de remoção para um `workId` inexistente retorna `404 Not Found`.

O teste também protege contra regressões no tratamento HTTP de `WorkNotFoundException`.

**Concorrência entre remoção e substituição**

O teste `shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()` verifica que uma remoção em transação aberta impede que uma substituição concorrente avance antes da liberação do bloqueio.

Após a conclusão das duas operações:

- o PostgreSQL referencia a nova capa;
- o `Content-Type` corresponde ao novo conteúdo;
- o conteúdo binário pode ser recuperado;
- o arquivo anterior foi removido;
- somente a nova capa permanece no filesystem.

O teste utiliza transações independentes, `CountDownLatch`, `Future` e verificações do estado final persistido.

### 10.8. Testes unitários dos callbacks

Os serviços `PublicationService` e `CatalogService` possuem testes unitários relacionados às operações com arquivos.

Esses testes não possuem automaticamente uma transação Spring real.

Quando precisam verificar o registro de callbacks transacionais, o ambiente de sincronização pode ser preparado explicitamente por meio de `TransactionSynchronizationManager`.

No `CatalogServiceTests`, o helper `simulateTransactionCompletion(Runnable operation, int completionStatus)` inicializa a sincronização, executa a operação, obtém os callbacks registrados e simula sua conclusão.

Para o estado `STATUS_COMMITTED`, executa `afterCommit()` e `afterCompletion()`.

Para `STATUS_ROLLED_BACK`, executa `afterCompletion()` com o status correspondente.

O contexto de sincronização é limpo ao final do teste.

Entre os testes relevantes de definição e substituição de capas estão:

- `shouldSetCoverForWorkOwner()`;
- `shouldDeleteNewCoverAfterTransactionRollback()`;
- `shouldDeletePreviousCoverAfterTransactionCommit()`;
- `shouldRejectCoverFromUserWhoDoesNotOwnWork()`.

Para a remoção de capas, foram adicionados:

- `shouldDeleteCoverFileAfterRemovalCommit()`;
- `shouldPreserveCoverFileAfterRemovalRollback()`.

O primeiro teste verifica que `CatalogService.removeCover()`:

- utiliza `findByIdForUpdate()`;
- remove os metadados da capa da entidade;
- registra a exclusão física após commit;
- solicita `BinaryStorage.delete()` quando o callback é executado.

O segundo teste verifica que:

- o serviço utiliza `findByIdForUpdate()`;
- o callback de exclusão física não é executado no rollback;
- `BinaryStorage.delete()` não é chamado para o arquivo original.

Esses testes validam os callbacks simulados e as interações do serviço.

Eles não demonstram a reversão real de uma entidade JPA em memória.

A preservação da referência persistida após rollback é validada pelos testes de integração com PostgreSQL.

### 10.9. Consistência transacional da exclusão de obras

A operação `CatalogService.deleteWork()` permite excluir uma obra que não possui installments.

A implementação utiliza bloqueio pessimista sobre a entidade `Work` e valida a propriedade antes de consultar a existência de installments.

Quando a exclusão é permitida, o serviço:

1. adquire o bloqueio pessimista da obra;
2. valida o proprietário;
3. verifica se existem installments associados;
4. captura a referência da capa, se existir;
5. solicita a exclusão do registro da obra;
6. registra um callback `afterCommit()` para remover o arquivo físico da capa, quando aplicável.

A exclusão da obra não remove installments em cascata.

A foreign key `fk_installment_work` impede a exclusão se existirem installments associados.

**Commit**

Quando a transação é confirmada:

- a obra é removida do PostgreSQL;
- as associações dependentes abrangidas pelas restrições de cascata são removidas;
- se houver capa, o arquivo físico é removido através de `BinaryStorage`.

**Rollback**

Quando a transação é revertida:

- a obra permanece persistida;
- a referência da capa permanece válida;
- o callback `afterCommit()` não é executado;
- o arquivo físico permanece disponível.

Comportamento esperado:

| Resultado | Registro da obra | Referência da capa | Arquivo físico |
|-----------|------------------|--------------------|----------------|
| Commit | Removido | Removida com a obra | Removido após commit |
| Rollback | Preservado | Preservada | Preservado |
| Obra com installments | Preservado | Preservada | Preservado |

Quando a obra não possui capa, nenhuma exclusão física é necessária.

#### Testes de integração da exclusão de obras

Os cenários são implementados em `WorkDeletionIntegrationTests`.

A classe contém 15 testes.

**1. Exclusão de obra sem installments**

O teste `shouldDeleteEmptyWorkForOwner()` verifica que o proprietário consegue excluir uma obra sem installments.

O endpoint retorna `204 No Content`, e a obra deixa de existir no PostgreSQL.

**2. Rejeição de obra com installment DRAFT**

O teste `shouldRejectDeletingWorkWithDraftInstallment()` verifica que a exclusão é rejeitada com `409 Conflict` quando existe um installment `DRAFT`.

A obra e o installment permanecem preservados.

**3. Rejeição de obra com installment PUBLISHED**

O teste `shouldRejectDeletingWorkWithPublishedInstallment()` verifica que a exclusão é rejeitada quando existe um installment publicado.

O conteúdo publicado e suas páginas permanecem preservados.

**4. Remoção da capa após commit**

O teste `shouldDeleteWorkCoverAfterSuccessfulWorkDeletion()` verifica que:

- a obra é excluída;
- sua referência de capa deixa de existir no banco;
- o arquivo físico da capa é removido após o commit.

**5. Preservação após rollback**

O teste `shouldPreserveWorkAndCoverWhenDeletionRollsBack()` utiliza uma transação real para executar a exclusão e solicitar rollback.

O teste verifica que a obra, a referência da capa e o arquivo físico permanecem preservados.

**6. Rejeição por usuário não proprietário**

O teste `shouldRejectWorkDeletionFromNonOwner()` verifica que outro usuário autenticado recebe `403 Forbidden`.

A obra e sua capa permanecem preservadas.

**7. Rejeição sem autenticação**

O teste `shouldRejectUnauthenticatedWorkDeletion()` verifica que uma requisição sem sessão autenticada recebe `401 Unauthorized`.

**8. Rejeição sem CSRF**

O teste `shouldRejectWorkDeletionWithoutCsrf()` verifica que uma requisição autenticada sem token CSRF válido recebe `403 Forbidden`.

**9. Obra inexistente**

O teste `shouldReturnNotFoundWhenDeletingNonexistentWork()` verifica que a exclusão de um `workId` inexistente retorna `404 Not Found`.

**10. Exclusão confirma antes da criação de installment**

O teste `shouldRejectInstallmentCreationWhenWorkDeletionCommitsFirst()` verifica que a criação concorrente de um installment não consegue persistir conteúdo em uma obra que já foi excluída.

**11. Criação de installment confirma antes da exclusão**

O teste `shouldRejectWorkDeletionWhenInstallmentCreationCommitsFirst()` verifica que a exclusão concorrente encontra o installment recém-confirmado e rejeita a remoção da obra.

**12. Remoção de entradas de biblioteca**

O teste `shouldRemoveLibraryEntriesWhenDeletingWork()` verifica a exclusão das referências de `library_entry` relacionadas à obra.

**13. Remoção de progresso de leitura**

O teste `shouldRemoveReadingProgressWhenDeletingWork()` verifica a exclusão das referências de `reading_progress` relacionadas à obra.

O cenário é preparado para exercitar a restrição referencial, inclusive em uma situação que não seria normalmente produzida pelo fluxo público de leitura.

**14. Proteção diretamente no PostgreSQL**

O teste `shouldPreventDeletingWorkWithInstallmentsAtDatabaseLevel()` verifica que uma tentativa de excluir diretamente uma obra com installments é rejeitada pela foreign key `fk_installment_work`.

O banco preserva os registros relacionados.

**15. Remoção de gêneros**

O teste `shouldRemoveWorkGenresWhenDeletingWork()` verifica que as associações de gêneros deixam de existir após a exclusão da obra.

O teste exercita o resultado persistido da operação.

Os cenários foram executados com PostgreSQL real.

A suíte completa confirmou a compatibilidade das alterações com os testes existentes.

### 10.10. Limitações da cobertura transacional

Os testes atuais validam comportamentos em transações normais e cenários de rollback controlados.

Eles não demonstram garantia de limpeza em todas as situações possíveis.

Entre as limitações conhecidas estão:

- encerramento abrupto da JVM;
- falhas durante a exclusão física;
- interrupção entre armazenamento e registro da compensação;
- resultado transacional incerto;
- ausência de reconciliação durável de arquivos órfãos.

A compensação de rollback está implementada nos fluxos de upload de páginas e de substituição de capas descritos neste documento.

A remoção de capas utiliza uma estratégia complementar: preservar o arquivo original durante a transação e excluí-lo apenas após commit.

A exclusão de obras utiliza a mesma premissa para a capa associada à obra.

Não se deve assumir que todos os fluxos de armazenamento possuem automaticamente a mesma proteção.

Em particular, uma falha na exclusão física durante `afterCommit()` pode deixar um arquivo armazenado mesmo após a confirmação da exclusão ou alteração do registro no PostgreSQL.

A suíte atual não representa uma garantia de atomicidade distribuída entre banco de dados e filesystem.

## 11. Testes de concorrência

O Metopa utiliza bloqueios pessimistas para coordenar operações de escrita sobre o mesmo installment ou a mesma obra, conforme o domínio envolvido.

A implementação utiliza `PESSIMISTIC_WRITE` no PostgreSQL.

Os testes de concorrência exercitam transações independentes para verificar os efeitos da coordenação.

### 11.1. Reordenação concorrente com publicação

Um cenário de integração verifica o conflito entre:

- reordenar páginas;
- publicar o mesmo installment.

Uma transação mantém o bloqueio enquanto executa a reordenação.

Outra transação tenta publicar o installment e precisa respeitar a coordenação estabelecida no PostgreSQL.

O teste verifica a preservação da ordem persistida e a consistência do estado final.

### 11.2. Exclusão concorrente com publicação

Outro cenário verifica a interação entre:

- publicação;
- exclusão do mesmo installment.

Se a publicação for confirmada antes de a exclusão prosseguir, a operação de exclusão deve encontrar o installment publicado e rejeitar sua remoção.

O resultado deve preservar o conteúdo publicado e seus arquivos.

### 11.3. Substituições concorrentes de capas

O módulo `catalog` utiliza um bloqueio pessimista na entidade `Work` antes de armazenar uma nova capa.

O método `WorkRepository.findByIdForUpdate(UUID workId)` é declarado no repository com a anotação `@Lock(LockModeType.PESSIMISTIC_WRITE)`.

A consulta utiliza JPQL para localizar a obra pelo identificador e adquirir o bloqueio necessário.

O `CatalogService.setCover()` utiliza essa consulta antes de chamar `BinaryStorage.store()`.

O cenário de integração é `shouldWaitForWorkLockBeforeStoringReplacementCover()`.

O teste utiliza duas transações independentes.

Uma transação adquire o bloqueio da obra diretamente no PostgreSQL.

A segunda tenta substituir sua capa.

Enquanto o bloqueio permanece ativo, a substituição não deve avançar até o armazenamento do novo arquivo.

O teste utiliza `WatchService` para observar eventos de criação no diretório da obra.

Durante o RED, um evento de criação foi observado antes da liberação do bloqueio.

Depois da utilização de `findByIdForUpdate()`, o teste passou.

Após a conclusão das transações, também é verificado que o filesystem contém a capa correspondente à referência persistida.

### 11.4. Concorrência entre metadados e capa

O módulo `catalog` permite atualizar os metadados da obra e substituir sua capa.

Essas operações modificam a mesma entidade `Work`.

Antes da coordenação, uma atualização de metadados podia utilizar uma versão anteriormente carregada da entidade e sobrescrever a referência de uma capa confirmada por outra transação.

Esse comportamento representa uma situação de *lost update*.

O cenário de integração é `shouldPreserveCoverWhenMetadataUpdateCommitsConcurrently()`.

O teste utiliza duas transações independentes.

A primeira atualiza os metadados da obra e permanece aberta.

A segunda tenta substituir a capa enquanto a primeira ainda não concluiu sua transação.

O teste verifica que a substituição precisa aguardar a conclusão da atualização de metadados.

Após os commits, são verificados:

- o título atualizado;
- a referência da nova capa no PostgreSQL;
- o `Content-Type` da nova capa;
- a disponibilidade do conteúdo binário;
- a ausência de arquivos de capas anteriores no diretório da obra.

Durante o RED, a referência persistida no PostgreSQL não correspondia à nova capa registrada pela operação de substituição.

A correção passou a utilizar `findByIdForUpdate()` também em `CatalogService.updateWork()`.

Assim, `updateWork()` e `setCover()` utilizam o mesmo mecanismo de coordenação sobre a obra.

O teste passou após essa alteração.

### 11.5. Concorrência entre remoção e substituição de capas

O módulo `catalog` também utiliza o bloqueio pessimista na remoção de capas.

As operações `removeCover()` e `setCover()` acessam a obra através de `findByIdForUpdate()`.

O cenário de integração é `shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()`.

O teste utiliza duas transações independentes.

A primeira remove a capa e mantém a transação aberta.

A segunda tenta substituir a capa enquanto a primeira ainda possui o bloqueio da obra.

A substituição não deve ser concluída antes da liberação do bloqueio.

Durante esse período, o arquivo original permanece armazenado até o commit da remoção.

Após a confirmação da primeira transação, a segunda pode prosseguir com a definição da nova capa.

O teste verifica:

- aquisição do bloqueio durante a remoção;
- preservação do arquivo original antes do commit;
- ausência de conclusão prematura da substituição;
- persistência da referência da nova capa;
- persistência do novo `Content-Type`;
- leitura do conteúdo binário da nova capa;
- exclusão física da capa original;
- existência de somente uma capa no diretório ao final.

Esse cenário protege contra inconsistências entre a referência persistida e o arquivo físico quando remoção e substituição acontecem concorrentemente.

### 11.6. Concorrência entre exclusão de obras e criação de installments

A exclusão de obras introduz uma situação de concorrência entre os módulos `catalog` e `publication`.

O problema ocorre quando uma transação tenta excluir uma obra enquanto outra tenta criar um installment associado à mesma obra.

Sem coordenação, a exclusão poderia consultar a ausência de installments antes de a outra transação confirmar sua criação.

A implementação utiliza bloqueio pessimista sobre a linha da obra nos dois fluxos.

**Fluxo de exclusão da obra**

A exclusão é coordenada pelo método `CatalogService.deleteWork(UUID userId, UUID workId)`, chamado pelo `WorkDeletionController`.

Esse método:

1. inicia ou participa de uma transação;
2. obtém a obra através de `WorkRepository.findByIdForUpdate()`;
3. valida a propriedade;
4. consulta a existência de installments através de `WorkDeletionGuard`;
5. exclui a obra somente quando não existem installments associados.

**Fluxo de criação de installments**

A criação de installments é coordenada por `PublicationService.createInstallment()`.

Durante esse fluxo, o serviço utiliza o método `CatalogService.findWorkForInstallmentCreation(UUID workId)`.

Esse segundo método exige uma transação existente por meio de `Propagation.MANDATORY` e adquire bloqueio pessimista utilizando `WorkRepository.findByIdForUpdate()`.

Assim, a criação e a exclusão de installments não são responsáveis pelo mesmo conjunto de regras, mas a criação de um installment e a exclusão da obra compartilham o mecanismo de bloqueio necessário para preservar a integridade dos dados.

Os testes exercitam dois ordenamentos.

**Cenário A — exclusão confirma primeiro**

O teste `shouldRejectInstallmentCreationWhenWorkDeletionCommitsFirst()` verifica:

1. a transação de exclusão adquire o bloqueio da obra;
2. a criação concorrente tenta utilizar a mesma obra;
3. a exclusão é confirmada;
4. a criação prossegue após a liberação do bloqueio;
5. a criação falha porque a obra não existe mais;
6. nenhum installment órfão é persistido.

**Cenário B — criação confirma primeiro**

O teste `shouldRejectWorkDeletionWhenInstallmentCreationCommitsFirst()` verifica:

1. a criação do installment adquire o bloqueio da obra;
2. a exclusão concorrente tenta acessar a mesma obra;
3. a criação é confirmada;
4. a exclusão prossegue;
5. o serviço identifica a existência do installment;
6. a exclusão é rejeitada;
7. a obra e o installment permanecem preservados.

Os testes utilizam transações independentes, sincronização entre threads e verificação do estado final no PostgreSQL.

A foreign key `fk_installment_work` oferece uma proteção adicional de integridade referencial.

### 11.7. Por que utilizar PostgreSQL real?

Mocks podem verificar chamadas a métodos, mas não reproduzem integralmente o comportamento de bloqueios e isolamento transacional do banco.

Os testes com PostgreSQL real ajudam a verificar:

- aquisição dos bloqueios;
- espera entre transações;
- visibilidade do estado confirmado;
- preservação das restrições;
- resultado final das operações concorrentes;
- comportamento das foreign keys;
- integridade referencial após exclusões.

Os testes unitários do `CatalogService` verificam a utilização de `findByIdForUpdate()`.

Os testes de integração verificam o comportamento transacional efetivo.

### 11.8. Limites dos testes

Os cenários de concorrência verificam situações específicas e conhecidas.

Não representam uma prova exaustiva de ausência de problemas de concorrência em todas as interações possíveis.

Os testes que utilizam `CountDownLatch`, `Future`, `WatchService` e intervalos de espera precisam ser interpretados considerando possíveis diferenças de escalonamento e desempenho do ambiente.

As janelas temporais utilizadas ajudam a reproduzir e verificar comportamentos concorrentes, mas não constituem, isoladamente, uma prova formal de ordenação.

As verificações do estado final persistido e dos arquivos físicos complementam essas observações.

Novos testes devem ser adicionados quando surgirem operações ou riscos concretos que justifiquem sua criação.

## 12. Isolamento dos testes

Os testes devem evitar dependência de dados deixados por execuções anteriores.

Cada cenário precisa preparar suas próprias condições iniciais e verificar seus resultados.

Nos testes que utilizam armazenamento físico, os arquivos gerados devem permanecer separados dos arquivos de uso normal da aplicação.

Quando necessário, diretórios de armazenamento destinados aos testes são limpos durante a preparação dos cenários.

Os testes de integração de capas utilizam uma configuração de armazenamento específica:

```properties
metopa.storage.root=target/test-work-cover-storage
```

As verificações devem considerar tanto o estado do PostgreSQL quanto o estado do filesystem.

A migration V10 exige que registros dependentes de uma obra utilizem um `workId` existente.

Os testes de repositório devem preparar explicitamente essas referências, em vez de depender de registros pré-existentes ou de identificadores sem correspondência no banco.

### Recomendações

- evitar dependência da ordem de execução;
- não depender de identificadores gerados em outro teste;
- preparar explicitamente os dados necessários;
- respeitar as foreign keys do schema;
- utilizar configurações de armazenamento próprias dos testes;
- verificar o estado final persistido;
- limpar recursos criados pelos cenários de teste;
- evitar sleeps arbitrários em testes concorrentes;
- utilizar mecanismos de sincronização controlada quando necessário;
- utilizar timeouts para evitar esperas indefinidas;
- liberar bloqueios e encerrar executores em blocos `finally`;
- validar referências do PostgreSQL e arquivos físicos após operações concorrentes.

## 13. Diagnóstico de falhas

Quando um teste falhar, a investigação deve começar pelo comportamento esperado.

### 13.1. Falhas de validação

Verificar:

- dados de entrada;
- normalização;
- validações de comandos;
- regras de domínio;
- resposta HTTP esperada.

### 13.2. Falhas de persistência

Verificar:

- disponibilidade do PostgreSQL;
- configuração do datasource;
- execução do Flyway;
- restrições do banco;
- fronteiras transacionais;
- estado final persistido;
- integridade referencial entre entidades.

Após a migration V10, falhas de persistência envolvendo `work_id` também devem considerar se a obra referenciada foi criada corretamente.

Uma tentativa de persistir um installment, uma entrada de biblioteca ou um progresso de leitura com um `workId` inexistente pode violar a foreign key correspondente.

### 13.3. Falhas de autenticação

Verificar:

- sessão autenticada;
- permissões do usuário;
- propriedade da obra;
- token CSRF;
- código HTTP esperado.

### 13.4. Falhas de armazenamento

Verificar:

- configuração do diretório de armazenamento;
- criação do arquivo;
- geração do `storageKey`;
- operações de leitura e exclusão;
- momento da execução dos callbacks transacionais;
- resultado da transação PostgreSQL;
- correspondência entre referências persistidas e arquivos físicos.

Na remoção de capas, verificar especialmente se o arquivo foi excluído antes ou depois do commit e se os metadados da entidade correspondem ao estado final do filesystem.

Na exclusão de obras, verificar se o registro foi removido antes da limpeza física e se o arquivo da capa foi preservado em caso de rollback.

### 13.5. Falhas de concorrência

Verificar:

- utilização do bloqueio pessimista;
- início das transações independentes;
- ordem dos eventos;
- momento do commit ou rollback;
- estado final observado pelas transações;
- restrições do PostgreSQL;
- atualização de referências com valores anteriormente carregados;
- existência de arquivos órfãos após operações concorrentes.

Quando o cenário envolve remoção e substituição de capas, verificar se ambas as operações utilizam `findByIdForUpdate()` e se a referência final corresponde ao arquivo físico preservado.

Quando o cenário envolve exclusão de obras e criação de installments, verificar se ambos os fluxos adquirem o bloqueio pessimista da mesma obra.

Também verificar se a criação ocorre dentro de uma transação que permanece ativa até a persistência do installment.

### 13.6. Falhas de tratamento HTTP

Verificar:

- existência do endpoint e método HTTP correto;
- mapeamento do controller;
- exceção lançada pelo serviço;
- tratamento no `@RestControllerAdvice`;
- resposta `ProblemDetail`;
- código HTTP esperado.

Na implementação de remoção de capas, o teste de obra inexistente identificou uma `WorkNotFoundException` não tratada adequadamente pela camada HTTP.

A inclusão do tratamento em `CatalogExceptionHandler` corrigiu esse cenário, permitindo retornar `404 Not Found`.

Na exclusão de obras, a exceção `WorkHasInstallmentsException` é tratada pelo `CatalogExceptionHandler`, retornando `409 Conflict`.

O erro diferencia a tentativa de excluir uma obra com installments de uma tentativa de excluir uma obra inexistente.

## 14. Critérios de conclusão de uma alteração

Antes de concluir uma feature ou correção no backend, recomenda-se verificar:

- [ ] Regras de negócio implementadas.
- [ ] Testes relevantes criados ou atualizados.
- [ ] Testes relacionados à alteração aprovados.
- [ ] Cenários de erro e autorização considerados.
- [ ] Efeitos persistidos verificados quando necessário.
- [ ] Comportamento de rollback analisado em operações transacionais.
- [ ] Restrições de integridade referencial verificadas quando aplicável.
- [ ] Cenários de concorrência considerados quando relevantes.
- [ ] Limites entre módulos preservados.
- [ ] Suíte completa executada com sucesso.
- [ ] Documentação atualizada quando o comportamento ou contrato mudou.
- [ ] `git diff --check` sem problemas.
- [ ] Pull Request preparada com resumo e validação.

Para mudanças exclusivamente documentais, a execução completa da suíte Java não é obrigatória quando nenhum código, teste ou arquivo de configuração de execução foi modificado.

## 15. Evolução da estratégia

A estratégia de testes deve acompanhar a evolução do MVP.

Novas funcionalidades podem exigir:

- testes adicionais de integração;
- testes de concorrência;
- validações de contratos HTTP;
- novos cenários de segurança;
- testes de integridade referencial;
- testes de falhas de armazenamento;
- testes relacionados à reconciliação de arquivos;
- verificações adicionais de fronteiras arquiteturais.

A prioridade é testar comportamentos importantes e riscos identificados, evitando adicionar testes apenas para aumentar a contagem total.

## Documentação relacionada

- [README — Visão geral do projeto](../README.md)
- [API HTTP](API.md)
- [Arquitetura](ARCHITECTURE.md)