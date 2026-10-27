# Metopa — Estratégia de Testes

Documentação da estratégia de testes automatizados do backend do Metopa.

Os testes são utilizados para validar regras de domínio, persistência, autenticação, autorização, contratos HTTP, modularidade, armazenamento binário, consistência transacional e concorrência.

O projeto utiliza Java 25, Spring Boot 4.1, JUnit, Mockito, Spring Test e PostgreSQL.

## 1. Estado atual

A última execução completa reportada da suíte apresentou:

```text
Tests run: 248
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A execução foi realizada em 26 de outubro de 2026, através do comando:

```bash
./mvnw clean test
```

O tempo total registrado foi de aproximadamente 2 minutos e 23 segundos.

A referência anterior era de 242 testes aprovados, estabelecida após a implementação da compensação de uploads de páginas em caso de rollback.

A execução atual contempla também os testes introduzidos durante o desenvolvimento da `feature/cover-transaction-consistency`, incluindo:

- compensação de uploads de capas após rollback;
- remoção da capa anterior após commit;
- coordenação de substituições concorrentes;
- proteção contra conflitos entre atualização de metadados e substituição de capa.

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

### 4.3. Executar a suíte completa

```bash
./mvnw clean test
```

O comando remove os artefatos anteriores de build, compila o projeto e executa os testes configurados para a fase `test` do Maven.

O resultado deve ser verificado no terminal.

A execução completa é a validação principal antes da conclusão de alterações no código da aplicação.

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

Para os testes de integração de capas:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests test
```

Também é possível executar um método específico:

```bash
./mvnw -Dtest=WorkCoverIntegrationTests#shouldRemoveNewCoverWhenReplacementRollsBack test
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
- concorrência entre atualizações de metadados e substituições de capas.

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
- concorrência entre operações.

Esse módulo concentra alguns dos testes de integração mais relevantes para a consistência entre o banco de dados e o filesystem.

### 6.4. Library

Os testes verificam:

- inclusão de obras na biblioteca;
- restrição a obras com conteúdo publicado;
- prevenção de duplicidade;
- listagem das entradas do usuário;
- isolamento entre bibliotecas;
- ordenação das entradas;
- remoção idempotente.

### 6.5. Reading

Os testes verificam:

- registro de progresso;
- atualização da posição existente;
- consulta da última posição registrada;
- identificação da obra e do installment a partir da página;
- restrição a páginas publicadas;
- tratamento de páginas inexistentes ou não publicadas;
- independência em relação à biblioteca pessoal.

### 6.6. Media

Os testes verificam:

- armazenamento de conteúdo binário;
- geração interna de `storageKey`;
- leitura de arquivos;
- exclusão;
- exclusão idempotente de arquivos inexistentes;
- proteção contra resolução de caminhos fora da raiz configurada;
- integração do armazenamento com os módulos que utilizam `BinaryStorage`.

## 7. Segurança

Os testes de segurança verificam os mecanismos de autenticação e autorização utilizados pela aplicação.

### 7.1. Autenticação

Operações privadas exigem uma sessão autenticada.

Os testes verificam respostas apropriadas quando o usuário não está autenticado.

### 7.2. Autorização

Operações administrativas sobre obras, installments, páginas e capas são restritas aos proprietários correspondentes.

Os testes verificam que um usuário não pode modificar ou consultar indevidamente o conteúdo administrativo pertencente a outro usuário.

### 7.3. CSRF

Operações protegidas que alteram estado exigem um token CSRF válido.

Os testes verificam a proteção contra requisições que não atendem a essa exigência.

### 7.4. Respostas HTTP

Os contratos utilizam códigos adequados para diferentes situações.

Entre os códigos verificados estão:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
```

Os testes também verificam a preservação dos dados quando operações inválidas ou não autorizadas são rejeitadas.

### 7.5. Visão pública e administrativa

Os testes verificam a separação entre:

- conteúdo público;
- conteúdo em estado `DRAFT`;
- operações administrativas do proprietário.

Installments não publicados não devem ficar disponíveis através dos endpoints públicos.

O proprietário pode acessar conteúdos em draft através dos endpoints administrativos apropriados.

## 8. Testes de persistência

O PostgreSQL é utilizado nos testes de integração que dependem de comportamento relacional real.

Esses testes verificam situações que não seriam suficientemente representadas por mocks.

Entre elas:

- persistência de entidades;
- atualização de registros;
- restrições de unicidade;
- relacionamentos;
- exclusão em cascata;
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

### 8.3. Rollback

Os testes de integração verificam que alterações não confirmadas são revertidas pelo PostgreSQL.

Quando uma operação envolve arquivos físicos, a validação também considera o comportamento de `BinaryStorage`.

Na substituição de capas, os testes verificam especificamente que o rollback restaura a referência anterior no PostgreSQL e que o arquivo da nova capa é removido.

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

### 9.4. Segurança dos caminhos

A implementação de filesystem verifica a resolução dos caminhos em relação à raiz configurada.

Os testes incluem a proteção contra operações fora dessa raiz.

## 10. Testes de consistência transacional

O PostgreSQL e o filesystem não compartilham uma transação distribuída.

Por esse motivo, o Metopa utiliza estratégias complementares para operações de criação, exclusão e substituição de arquivos.

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

O cenário de regressão foi implementado em:

```text
PageUploadIntegrationTests
```

Método:

```java
shouldRemoveUploadedPageFileWhenTransactionRollsBack();
```

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

Essas garantias se referem às transações e aos callbacks que terminam normalmente. Falhas abruptas ou erros durante a limpeza física continuam sujeitos às limitações descritas na seção 10.8.

### 10.6. Testes de regressão das capas

Os principais cenários de integração são implementados em:

```text
WorkCoverIntegrationTests
```

**Rollback de substituição**

```java
shouldRemoveNewCoverWhenReplacementRollsBack();
```

O teste:

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

```java
shouldRemovePreviousCoverAfterSuccessfulReplacement();
```

O teste:

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

### 10.7. Testes unitários dos callbacks

Os serviços `PublicationService` e `CatalogService` possuem testes unitários relacionados às operações com arquivos.

Esses testes não possuem automaticamente uma transação Spring real.

Quando precisam verificar o registro de callbacks transacionais, o ambiente de sincronização pode ser preparado explicitamente por meio de `TransactionSynchronizationManager`.

No `CatalogServiceTests`, o helper:

```java
simulateTransactionCompletion(
        Runnable operation,
        int completionStatus
);
```

inicializa a sincronização, executa a operação, obtém os callbacks registrados e simula sua conclusão.

Para o estado `STATUS_COMMITTED`, executa `afterCommit()` e `afterCompletion()`.

Para `STATUS_ROLLED_BACK`, executa `afterCompletion()` com o status correspondente.

O contexto de sincronização é limpo ao final do teste.

Entre os testes relevantes estão:

```java
shouldSetCoverForWorkOwner();
shouldDeleteNewCoverAfterTransactionRollback();
shouldDeletePreviousCoverAfterTransactionCommit();
shouldRejectCoverFromUserWhoDoesNotOwnWork();
```

Os testes verificam as interações com `BinaryStorage` e a utilização do repository apropriado.

Essa abordagem não substitui os testes de integração com PostgreSQL.

Ela complementa a verificação do comportamento do serviço isolado.

### 10.8. Limitações da cobertura transacional

Os testes atuais validam comportamentos em transações normais e cenários de rollback controlados.

Eles não demonstram garantia de limpeza em todas as situações possíveis.

Entre as limitações conhecidas estão:

- encerramento abrupto da JVM;
- falhas durante a exclusão física;
- interrupção entre armazenamento e registro da compensação;
- resultado transacional incerto;
- ausência de reconciliação durável de arquivos órfãos.

A compensação de rollback está implementada nos fluxos de upload de páginas e de substituição de capas descritos neste documento.

Não se deve assumir que todos os fluxos de armazenamento possuem automaticamente a mesma proteção.

Em particular, uma falha na exclusão da capa anterior durante `afterCommit()` pode deixar o arquivo antigo armazenado mesmo após a confirmação da nova referência no PostgreSQL.

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

O método `WorkRepository.findByIdForUpdate()` utiliza:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Work w WHERE w.id = :workId")
Optional<Work> findByIdForUpdate(
        @Param("workId") UUID workId
);
```

O `CatalogService.setCover()` utiliza essa consulta antes de chamar `BinaryStorage.store()`.

O cenário de integração é:

```java
shouldWaitForWorkLockBeforeStoringReplacementCover();
```

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

O cenário de integração é:

```java
shouldPreserveCoverWhenMetadataUpdateCommitsConcurrently();
```

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

### 11.5. Por que utilizar PostgreSQL real?

Mocks podem verificar chamadas a métodos, mas não reproduzem integralmente o comportamento de bloqueios e isolamento transacional do banco.

Os testes com PostgreSQL real ajudam a verificar:

- aquisição dos bloqueios;
- espera entre transações;
- visibilidade do estado confirmado;
- preservação das restrições;
- resultado final das operações concorrentes.

Os testes unitários do `CatalogService` verificam a utilização de `findByIdForUpdate()`.

Os testes de integração verificam o comportamento transacional efetivo.

### 11.6. Limites dos testes

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

### Recomendações

- evitar dependência da ordem de execução;
- não depender de identificadores gerados em outro teste;
- preparar explicitamente os dados necessários;
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
- estado final persistido.

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

## 14. Critérios de conclusão de uma alteração

Antes de concluir uma feature ou correção no backend, recomenda-se verificar:

- [ ] Regras de negócio implementadas.
- [ ] Testes relevantes criados ou atualizados.
- [ ] Testes relacionados à alteração aprovados.
- [ ] Cenários de erro e autorização considerados.
- [ ] Efeitos persistidos verificados quando necessário.
- [ ] Comportamento de rollback analisado em operações transacionais.
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
- testes de falhas de armazenamento;
- testes relacionados à reconciliação de arquivos;
- verificações adicionais de fronteiras arquiteturais.

A prioridade é testar comportamentos importantes e riscos identificados, evitando adicionar testes apenas para aumentar a contagem total.

## Documentação relacionada

- [README — Visão geral do projeto](../README.md)
- [API HTTP](API.md)
- [Arquitetura](ARCHITECTURE.md)
