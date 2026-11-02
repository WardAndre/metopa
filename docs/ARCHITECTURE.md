# Metopa — Arquitetura

Documentação da arquitetura do backend do Metopa, incluindo organização modular, responsabilidades dos módulos, persistência, segurança, concorrência e consistência entre PostgreSQL e filesystem.

O documento descreve as decisões adotadas durante o desenvolvimento do MVP e as limitações conhecidas da implementação atual.

## 1. Visão geral

O Metopa é uma plataforma para publicação, descoberta e leitura de quadrinhos, mangás, graphic novels, webtoons e outros formatos de narrativa visual.

O backend é implementado em Java 25 com Spring Boot 4.1 e Spring Modulith.

A arquitetura utiliza um **monólito modular**: uma única aplicação executável, organizada em módulos com responsabilidades e fronteiras explícitas.

A infraestrutura do MVP é executada localmente, sem dependência obrigatória de serviços cloud pagos.

### Principais componentes

| Componente | Tecnologia | Responsabilidade |
|------------|------------|------------------|
| Aplicação | Java 25 / Spring Boot 4.1 | Execução do backend |
| Modularidade | Spring Modulith | Organização e verificação das fronteiras entre módulos |
| HTTP | Spring Web MVC | Exposição dos endpoints |
| Segurança | Spring Security | Sessões, autenticação, autorização e CSRF |
| Persistência | Spring Data JPA / Hibernate | Mapeamento e acesso aos dados relacionais |
| Banco de dados | PostgreSQL 18 | Persistência de dados estruturados |
| Migrações | Flyway | Evolução versionada do schema |
| Armazenamento binário | Filesystem local | Armazenamento físico de páginas e capas |
| Infraestrutura local | Docker Compose | Execução do PostgreSQL |
| Build | Maven Wrapper | Compilação, execução e testes |

## 2. Decisão arquitetural: monólito modular

O Metopa utiliza Spring Modulith para manter os domínios organizados dentro de uma única aplicação.

Essa escolha permite:

- desenvolver o MVP sem infraestrutura distribuída;
- manter uma única aplicação para execução e deployment;
- estabelecer responsabilidades claras entre módulos;
- reduzir o acoplamento entre domínios;
- realizar testes de integração sem introduzir comunicação entre serviços;
- evoluir a arquitetura conforme surgirem necessidades concretas.

O uso de um monólito modular não elimina a necessidade de definir fronteiras arquiteturais.

As dependências entre módulos são verificadas por testes utilizando Spring Modulith.

### Por que não microsserviços?

Nesta fase, não existem requisitos demonstrados que justifiquem a complexidade operacional de múltiplos serviços independentes.

Microsserviços acrescentariam preocupações como comunicação distribuída, observabilidade, deployment individual, consistência eventual e infraestrutura adicional.

O monólito modular permite priorizar as regras de negócio e a estabilidade do MVP.

A arquitetura pode evoluir no futuro, mas não pressupõe a extração obrigatória dos módulos em serviços separados.

## 3. Organização modular

Os módulos atuais estão organizados sob o pacote principal `metopa`:

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

Cada módulo concentra responsabilidades pertencentes ao seu domínio.

### 3.1. Identity

Responsável por contas de usuários e autenticação.

Principais funcionalidades:

- criação de contas;
- normalização de username e e-mail;
- validação de dados;
- unicidade de username e e-mail;
- identificação de usuários através de UUID;
- armazenamento de senhas utilizando hash bcrypt;
- autenticação baseada em sessão HTTP;
- login e logout;
- consulta do usuário autenticado;
- integração com Spring Security e proteção CSRF.

As regras de autenticação não devem ser duplicadas nos módulos de catálogo, publicação ou biblioteca.

A identidade autenticada é obtida pela aplicação e utilizada nas operações que precisam reconhecer o usuário.

### 3.2. Catalog

Responsável pelas obras e seus metadados.

Principais funcionalidades:

- criação e atualização de obras;
- associação da obra ao proprietário autenticado;
- consulta administrativa das obras do criador;
- definição do formato da obra;
- direção de leitura e modo de apresentação;
- descrição e título;
- associação de gêneros;
- definição, substituição, remoção e leitura de capas;
- remoção idempotente de capas;
- compensação de arquivos de capas em commits e rollbacks;
- exclusão física de capas somente após a confirmação de operações de remoção;
- exclusão de obras que não possuem installments;
- rejeição da exclusão de obras com installments `DRAFT` ou `PUBLISHED`;
- coordenação de substituições, remoções de capas, atualizações de metadados e exclusões de obras por bloqueio pessimista;
- coordenação da criação de installments com a exclusão de obras;
- exposição de informações da obra para outros módulos através de APIs internas.

A propriedade de uma obra pertence ao domínio `catalog`.

Outros módulos não devem modificar diretamente suas entidades ou acessar livremente seus repositories.

Quando o módulo `publication` precisa validar a propriedade de uma obra, utiliza uma API disponibilizada por `catalog`.

Um exemplo dessa integração é a utilização de `CatalogService.findWork()`, que fornece uma referência da obra contendo as informações necessárias à operação.

Para a criação de installments, o módulo `catalog` também disponibiliza `CatalogService.findWorkForInstallmentCreation()`, responsável por adquirir um bloqueio pessimista na obra dentro de uma transação existente.

A exclusão de obras é coordenada pelo método `CatalogService.deleteWork()`.

O módulo `catalog` define o contrato `WorkDeletionGuard`, utilizado para verificar se existem installments antes de permitir a exclusão.

A implementação concreta desse contrato pertence ao módulo `publication`.

Essa separação permite que o catálogo aplique sua regra de exclusão sem acessar diretamente os repositories internos de publicação.

A coordenação das operações sobre capas pertence ao módulo `catalog`, enquanto o módulo `media` continua responsável pelo armazenamento físico dos arquivos.

### 3.3. Publication

Responsável pelo conteúdo publicado nas obras.

O módulo administra installments e páginas.

Um installment pode representar:

```text
CHAPTER
ISSUE
EPISODE
STRIP
```

Os estados utilizados atualmente são:

```text
DRAFT
PUBLISHED
```

Responsabilidades principais:

- criação de installments;
- atualização dos metadados de installments em `DRAFT`;
- exclusão de installments em `DRAFT`;
- upload de páginas;
- exclusão e reordenação de páginas;
- publicação de installments;
- validação das regras de imutabilidade;
- consulta administrativa para o proprietário;
- leitura pública do conteúdo publicado;
- coordenação das operações transacionais com arquivos binários;
- implementação da consulta necessária para impedir a exclusão de obras que possuem installments;
- utilização do bloqueio da obra durante a criação de installments.

O módulo é responsável pelas regras que determinam quando uma página ou um installment pode ser modificado.

Operações sobre conteúdos publicados não podem alterar seus metadados ou páginas no MVP.

A classe `PublicationWorkDeletionGuard`, localizada em `publication.internal`, implementa o contrato público `WorkDeletionGuard`, definido pelo módulo `catalog`.

Essa implementação consulta `InstallmentRepository.existsByWorkId()`.

O repository permanece encapsulado em `publication.internal`.

O módulo também utiliza `CatalogService.findWorkForInstallmentCreation()` durante a criação de installments, garantindo a coordenação transacional com a exclusão de obras.

### 3.4. Library

Responsável pela biblioteca pessoal dos usuários.

Principais funcionalidades:

- inclusão de obras na biblioteca;
- prevenção de entradas duplicadas;
- listagem das obras adicionadas;
- isolamento por usuário;
- remoção idempotente de entradas da biblioteca;
- restrição das operações a obras com conteúdo publicado.

A biblioteca não é responsável pela publicação do conteúdo.

Sua responsabilidade é registrar a relação entre um usuário e as obras que ele adicionou à biblioteca.

A migration V10 introduziu uma foreign key entre `library_entry.work_id` e `work.id`, utilizando `ON DELETE CASCADE`.

Com isso, a exclusão de uma obra também remove automaticamente suas referências de biblioteca, sem exigir uma chamada direta de `CatalogService` ao módulo `library`.

No fluxo normal da aplicação, apenas obras com conteúdo publicado podem ser adicionadas à biblioteca. Como obras com installments não podem ser excluídas, essa limpeza em cascata também atua como uma proteção de integridade referencial.

### 3.5. Reading

Responsável pelo progresso de leitura.

Principais funcionalidades:

- registro da página atual;
- identificação da obra e do installment a partir da página;
- uma posição de leitura por usuário e obra;
- atualização do progresso existente;
- consulta da última posição registrada;
- validação de que a página pertence a conteúdo publicado.

O progresso de leitura é independente da biblioteca pessoal.

Uma obra não precisa estar adicionada à biblioteca para que seu progresso seja registrado.

A migration V10 introduziu uma foreign key entre `reading_progress.work_id` e `work.id`, utilizando `ON DELETE CASCADE`.

Essa restrição impede que referências de progresso permaneçam associadas a uma obra excluída.

No fluxo normal, o progresso exige uma página publicada e, consequentemente, um installment associado à obra.

Portanto, a exclusão de uma obra que possui progresso de leitura normalmente é impedida pela existência de installments.

A regra de `ON DELETE CASCADE` funciona como uma proteção adicional para situações em que possam existir referências legadas ou inconsistentes.

### 3.6. Media

Responsável pela abstração de armazenamento de arquivos binários.

O módulo disponibiliza a interface `BinaryStorage`.

Principais operações:

- armazenar conteúdo binário;
- gerar internamente uma chave de armazenamento;
- recuperar o conteúdo a partir da chave;
- excluir conteúdo binário;
- tratar de forma idempotente a exclusão de arquivos inexistentes;
- proteger operações de leitura e exclusão contra caminhos fora da raiz configurada.

A implementação atual utiliza o filesystem local.

Páginas e capas compartilham essa abstração.

O módulo `media` não decide se um installment pode ser publicado, se uma página pode ser excluída ou se um usuário é proprietário de determinada obra.

Também não determina se uma obra pode ser excluída.

Essas regras pertencem aos módulos de domínio.

Quando uma obra é excluída, o módulo `catalog` coordena a remoção do registro e solicita ao `BinaryStorage` a exclusão da capa após o commit.

### 3.7. Moderation

Módulo previsto para denúncias e ações de moderação.

As funcionalidades de moderação estão planejadas para etapas posteriores do MVP.

O módulo não deve ser considerado implementado apenas por existir na estrutura da aplicação.

## 4. Comunicação entre módulos

As interações entre módulos utilizam interfaces e APIs expostas pelos próprios módulos.

As entidades JPA e repositories são mantidos como detalhes internos.

O projeto evita criar relacionamentos JPA diretos entre entidades pertencentes a módulos diferentes.

Em seu lugar, utiliza identificadores e referências de domínio.

### Exemplo: criação de uma página

```text
Cliente HTTP
    |
    v
Publication
    |
    | consulta installment
    | valida estado DRAFT
    |
    +----> Catalog
    |      verifica a propriedade da obra
    |
    +----> Media
    |      armazena o arquivo binário
    |      retorna storageKey
    |
    +----> PostgreSQL
           persiste os metadados da página
```

O módulo `publication` coordena a operação.

O módulo `catalog` fornece as informações necessárias para verificar a propriedade.

O módulo `media` realiza o armazenamento físico.

O PostgreSQL mantém o registro e a referência do arquivo.

### Exemplo: exclusão de uma obra

A exclusão de obras exige uma verificação que depende de informações pertencentes ao módulo `publication`.

Entretanto, o módulo `catalog` não deve acessar diretamente as entidades e repositories internos de publicação.

Para preservar essa separação, foi definido o contrato `WorkDeletionGuard`.

```text
Cliente HTTP
    |
    v
WorkDeletionController
    |
    | identifica usuário autenticado
    |
    v
CatalogService.deleteWork()
    |
    | bloqueia Work
    | valida proprietário
    |
    v
WorkDeletionGuard
    ^
    |
PublicationWorkDeletionGuard
    |
    v
InstallmentRepository
    |
    | existsByWorkId(workId)
    |
    v
PostgreSQL
```

O contrato `WorkDeletionGuard` pertence ao módulo `catalog`.

A implementação `PublicationWorkDeletionGuard` pertence ao módulo `publication`.

O Spring injeta a implementação no serviço de catálogo através do contrato.

Essa abordagem permite que o catálogo consulte a existência de installments sem introduzir uma dependência direta sobre os detalhes internos de persistência de publicação.

A existência do contrato não significa que o módulo `catalog` passe a ser responsável pelo gerenciamento de installments.

### Benefícios dessa separação

- regras de domínio permanecem próximas de seus responsáveis;
- detalhes de armazenamento não contaminam os contratos HTTP;
- alterações no filesystem não exigem modificar as regras de publicação;
- relações entre módulos não dependem de associações JPA;
- as fronteiras arquiteturais podem ser verificadas automaticamente;
- dependências entre módulos são expressas por contratos públicos;
- repositories internos continuam encapsulados;
- operações transacionais podem ser coordenadas sem duplicar regras de domínio.

## 5. Persistência

O Metopa utiliza PostgreSQL 18 como banco de dados relacional.

O acesso aos dados é realizado através de Spring Data JPA e Hibernate.

As entidades possuem identificadores UUID definidos no domínio.

### 5.1. Responsabilidade do PostgreSQL

O PostgreSQL armazena dados estruturados e referências relacionadas às operações da aplicação.

Entre eles:

- contas de usuário;
- obras e metadados;
- gêneros das obras;
- installments;
- metadados das páginas;
- referências das capas;
- entradas da biblioteca;
- progresso de leitura.

O conteúdo binário das imagens não é armazenado diretamente no banco.

### 5.2. Relacionamentos entre módulos

Os relacionamentos que atravessam as fronteiras dos módulos são representados por identificadores e contratos internos.

Por exemplo, um installment mantém a identificação da obra à qual pertence, mas o módulo `publication` não precisa manipular diretamente uma entidade JPA do módulo `catalog`.

Essa estratégia preserva o encapsulamento dos módulos.

Entretanto, evitar associações JPA entre módulos não significa que o PostgreSQL não possa possuir foreign keys entre suas tabelas.

A partir da migration V10, o banco reforça os relacionamentos entre obras, installments, biblioteca e progresso de leitura.

As foreign keys representam garantias de integridade referencial no banco, enquanto os contratos Java continuam definindo as responsabilidades e dependências entre os módulos.

### 5.3. Integridade dos dados

O PostgreSQL é utilizado para reforçar regras de integridade.

Entre as restrições relevantes estão:

- unicidade de combinações de tipo e número dos installments dentro da mesma obra;
- unicidade do número de página dentro de um installment;
- relacionamentos necessários para manter a consistência dos registros;
- exclusão em cascata dos registros das páginas quando um installment é removido;
- impedimento da exclusão de obras com installments;
- exclusão em cascata de referências de biblioteca associadas a uma obra removida;
- exclusão em cascata de registros de progresso de leitura associados a uma obra removida;
- exclusão em cascata dos gêneros associados a uma obra removida.

As restrições relacionadas à exclusão de obras são:

| Tabela | Referência | Comportamento |
|--------|------------|---------------|
| `installment` | `work_id → work.id` | `ON DELETE RESTRICT` |
| `library_entry` | `work_id → work.id` | `ON DELETE CASCADE` |
| `reading_progress` | `work_id → work.id` | `ON DELETE CASCADE` |
| `work_genre` | `work_id → work.id` | `ON DELETE CASCADE` |

A restrição de `work_genre` foi introduzida na migration V9.

As três primeiras foram adicionadas pela migration V10.

A aplicação também valida regras de domínio antes de persistir alterações.

No caso da exclusão de obras, `CatalogService.deleteWork()` verifica a existência de installments antes de remover a obra.

A foreign key com `ON DELETE RESTRICT` oferece uma proteção adicional caso uma operação de exclusão contorne essa validação.

Validações na aplicação e restrições no banco possuem responsabilidades complementares.

## 6. Migrations

O Flyway é responsável pelo versionamento do schema do PostgreSQL.

Os arquivos ficam em:

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
V10__add_work_reference_constraints.sql
```

A migration `V9__create_work_genre.sql` introduziu a persistência de múltiplos gêneros associados a uma obra.

As operações de exclusão de installments, reordenação de páginas, compensação transacional de uploads, substituição e remoção de capas, bem como o bloqueio pessimista das escritas sobre obras, foram implementadas sem necessidade de novas migrations.

A remoção de capas reutiliza os campos existentes `cover_storage_key` e `cover_content_type`, definidos como opcionais no modelo de persistência.

### Migration V10 — Integridade referencial de obras

A migration `V10__add_work_reference_constraints.sql` introduziu três foreign keys:

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

Essas restrições garantem que:

1. uma obra não possa ser excluída enquanto possuir installments;
2. referências da biblioteca sejam removidas quando sua obra for excluída;
3. registros de progresso não permaneçam associados a obras inexistentes.

A associação `work_genre` já utiliza `ON DELETE CASCADE` desde a migration V9.

Antes da aplicação da V10 no ambiente local, foram identificados registros órfãos gerados durante testes anteriores.

Esses registros foram analisados e tratados antes da criação das novas restrições, preservando a integridade do banco.

Os testes de repositório também foram ajustados para criar obras válidas antes de persistir registros dependentes de `work`.

A migration foi aplicada e validada pelo Flyway.

A validação posterior confirmou:

```text
Successfully validated 10 migrations
Current version of schema "public": 10
Schema "public" is up to date.
No migration necessary.
```

### Política de evolução do schema

Migrations já aplicadas não devem ser editadas.

Mudanças posteriores devem utilizar novas migrations versionadas, preservando o histórico de evolução do banco.

A adição de novas foreign keys exige considerar a existência de registros legados ou órfãos, pois restrições relacionais não podem ser adicionadas normalmente quando os dados existentes violam suas regras.

## 7. Modelo de publicação

### 7.1. Installments

Um installment pertence a uma obra e possui tipo, número, título opcional e estado de publicação.

A combinação de tipo e número deve ser única dentro da obra.

Ao ser criado, um installment começa no estado `DRAFT`.

A partir da migration V10, `installment.work_id` possui uma foreign key para `work.id`.

Isso impede a persistência de installments associados a obras inexistentes e protege contra a exclusão de obras que ainda possuem installments.

### 7.2. Páginas

As páginas pertencem a um installment.

Cada página possui:

- identificador UUID;
- identificador do installment;
- número de leitura;
- referência interna de armazenamento;
- `Content-Type`.

Os arquivos físicos das páginas são armazenados pelo módulo `media`.

### 7.3. Estado DRAFT

Enquanto um installment está em `DRAFT`, o proprietário da obra pode:

- atualizar seus metadados;
- adicionar páginas;
- excluir páginas;
- reordenar páginas;
- excluir o próprio installment;
- publicar o conteúdo quando existir pelo menos uma página.

### 7.4. Estado PUBLISHED

Após a publicação:

- o installment fica disponível para leitura pública;
- suas páginas ficam disponíveis através dos endpoints públicos;
- seus metadados e páginas não podem ser modificados;
- o installment não pode ser excluído no MVP.

O estado `PUBLISHED` é tratado como imutável.

A imutabilidade dos installments publicados não impede que o proprietário altere ou remova a capa da obra, pois a capa pertence ao domínio `catalog`.

### 7.5. Visão pública e visão do criador

O Metopa distingue duas perspectivas.

**Visão pública:**

- apresenta somente obras com conteúdo publicado;
- apresenta somente installments `PUBLISHED`;
- não expõe drafts;
- disponibiliza capas e páginas conforme as regras de publicação.

**Visão do criador:**

- requer autenticação;
- valida a propriedade da obra;
- apresenta obras ainda sem conteúdo publicado;
- permite consultar installments `DRAFT` e `PUBLISHED`;
- disponibiliza metadados e conteúdo binário para gerenciamento.

Uma obra com conteúdo publicado permanece disponível publicamente após a remoção da capa.

Nessa situação, a capa deixa de estar disponível, mas os installments e suas páginas permanecem acessíveis conforme as regras de publicação.

Essa separação faz parte das regras de acesso do domínio e não depende apenas da interface utilizada pelo cliente.

### 7.6. Exclusão de obras

O MVP permite excluir uma obra somente quando ela não possui installments.

Essa restrição vale independentemente do estado de publicação:

- obras sem installments podem ser excluídas;
- obras com installments `DRAFT` não podem ser excluídas;
- obras com installments `PUBLISHED` não podem ser excluídas.

A exclusão da obra não utiliza uma política de remoção em cascata para installments e páginas.

A operação é implementada no módulo `catalog` através de `CatalogService.deleteWork()`.

A existência de installments é verificada por meio do contrato `WorkDeletionGuard`.

Se a obra possuir installments, a aplicação lança `WorkHasInstallmentsException`, convertida em `409 Conflict` pelo `CatalogExceptionHandler`.

Quando a exclusão é permitida:

- o registro da obra é removido;
- os gêneros associados são removidos;
- referências de biblioteca associadas à obra são removidas;
- registros de progresso de leitura associados à obra são removidos;
- se houver uma capa, seu arquivo físico é removido após o commit.

A exclusão não é idempotente quanto à resposta HTTP.

Uma primeira operação válida retorna `204 No Content`.

Após a exclusão, uma nova tentativa de remover a mesma obra retorna `404 Not Found`.

## 8. Concorrência e bloqueio pessimista

O Metopa utiliza bloqueios pessimistas no PostgreSQL para coordenar operações de escrita concorrentes sobre o mesmo installment ou a mesma obra.

A estratégia utiliza `PESSIMISTIC_WRITE`, por meio de consultas `findByIdForUpdate()` nos repositories responsáveis por cada entidade.

### Operações coordenadas

No módulo `publication`, a estratégia se aplica às operações de escrita sobre o mesmo installment, incluindo:

- criação de páginas;
- exclusão de páginas;
- reordenação de páginas;
- atualização de metadados;
- publicação;
- exclusão do installment.

No módulo `catalog`, a estratégia coordena:

- atualização dos metadados da obra;
- definição e substituição de capas;
- remoção de capas;
- exclusão de obras;
- criação de installments associados à mesma obra.

A criação de installments é coordenada com a exclusão de obras porque ambas as operações adquirem bloqueio na linha de `Work`.

### Funcionamento

```text
Transação A
    |
    v
Obtém bloqueio da entidade
    |
    v
Valida e modifica os dados
    |
    | Transação B tenta modificar
    | a mesma entidade
    |
    +----> Transação B aguarda
    |
    v
Commit ou rollback
    |
    v
Bloqueio liberado
    |
    v
Transação B continua
    |
    v
Reavalia o estado persistido
```

A coordenação evita que duas operações incompatíveis alterem simultaneamente a mesma entidade sem observar o estado consolidado da transação anterior.

### 8.1. Reordenação concorrente com publicação

A estratégia foi validada com duas transações independentes no PostgreSQL.

Uma transação reordena as páginas enquanto outra tenta publicar o mesmo installment.

O bloqueio mantém a coordenação entre as operações.

O resultado esperado é preservar a ordem confirmada das páginas e o estado final correto do installment.

### 8.2. Exclusão concorrente com publicação

A exclusão de installments também utiliza o bloqueio pessimista.

Quando a publicação é confirmada antes de a exclusão prosseguir, a operação de exclusão encontra o installment em `PUBLISHED` e é rejeitada.

O conteúdo publicado e seus arquivos permanecem preservados.

### 8.3. Limites da estratégia para installments

O bloqueio pessimista coordena operações sobre a mesma linha do installment.

Ele não substitui restrições de unicidade e integridade do PostgreSQL.

Conflitos envolvendo registros diferentes continuam exigindo validações apropriadas e restrições no banco.

### 8.4. Substituição concorrente de capas

No módulo `catalog`, o `WorkRepository` disponibiliza `findByIdForUpdate()` com `@Lock(LockModeType.PESSIMISTIC_WRITE)`.

O método `CatalogService.setCover()` adquire o bloqueio da obra antes de verificar a propriedade, identificar a capa anterior ou armazenar o novo arquivo em `BinaryStorage`.

Se outra transação já estiver modificando a mesma obra, a substituição aguarda a liberação do bloqueio antes de começar o armazenamento físico da nova capa.

Isso evita que duas substituições utilizem simultaneamente uma referência anterior desatualizada e reduz o risco de arquivos órfãos decorrentes de atualizações concorrentes.

O bloqueio permanece associado à transação de banco até seu commit ou rollback.

### 8.5. Concorrência entre metadados e capas

O método `CatalogService.updateWork()` também utiliza `findByIdForUpdate()`.

Como a atualização de metadados e a substituição de capa alteram a mesma entidade `Work`, ambas precisam observar o estado consolidado da transação anterior.

A coordenação impede que uma atualização de metadados baseada em uma entidade carregada anteriormente sobrescreva a referência de uma capa recém-confirmada (*lost update*).

Esse cenário foi validado com transações independentes no PostgreSQL, preservando tanto os metadados atualizados quanto a referência e o conteúdo da capa substituta.

### 8.6. Concorrência entre remoção e substituição de capas

A operação `CatalogService.removeCover()` também utiliza `WorkRepository.findByIdForUpdate()`.

A mesma estratégia de bloqueio é aplicada às operações de remoção e substituição, permitindo coordenar alterações concorrentes sobre a mesma obra.

Um cenário de integração verifica o seguinte fluxo:

1. uma transação inicia a remoção da capa e adquire o bloqueio da obra;
2. a referência da capa é removida da entidade, mas a transação permanece aberta;
3. outra transação tenta substituir a capa da mesma obra;
4. a substituição aguarda a liberação do bloqueio;
5. a primeira transação confirma a remoção e executa a limpeza física após commit;
6. a segunda transação prossegue, armazena a nova capa e confirma a nova referência.

O estado final esperado é:

- a referência no PostgreSQL aponta para a nova capa;
- o `Content-Type` corresponde ao novo arquivo;
- o conteúdo binário da nova capa pode ser recuperado;
- o arquivo anterior foi removido;
- somente o arquivo da nova capa permanece no diretório da obra.

Esse cenário foi validado em `WorkCoverIntegrationTests.shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()`.

O teste utiliza transações independentes, mecanismos de sincronização entre threads e verificações do estado final do PostgreSQL e do filesystem.

### 8.7. Exclusão concorrente de obra e criação de installment

A exclusão de obras introduz um cenário de concorrência que envolve dois módulos diferentes:

- `catalog`, responsável pela obra e sua exclusão;
- `publication`, responsável pela criação de installments.

Apenas consultar a existência de installments durante a exclusão não seria suficiente para impedir uma condição de corrida.

Sem coordenação, uma transação poderia verificar que a obra não possui installments enquanto outra transação estivesse criando um novo installment.

Para resolver esse problema, a criação e a exclusão utilizam o mesmo bloqueio pessimista sobre a linha da obra.

**Exclusão da obra:**

```java
@Transactional
public void deleteWork(UUID userId, UUID workId);
```

O método adquire o bloqueio por meio de `WorkRepository.findByIdForUpdate()`, valida a propriedade e consulta `WorkDeletionGuard.hasInstallments()` antes de remover a obra.

**Criação de installment:**

```java
@Transactional(propagation = Propagation.MANDATORY)
public WorkReference findWorkForInstallmentCreation(
        UUID workId
);
```

O método exige uma transação existente e também utiliza `WorkRepository.findByIdForUpdate()`.

`PublicationService.createInstallment()` utiliza esse método antes de persistir o installment.

A estratégia estabelece dois cenários.

**Cenário A — exclusão confirma primeiro:**

1. a transação de exclusão bloqueia a obra;
2. verifica que não existem installments;
3. remove a obra;
4. a transação de criação aguarda a liberação do bloqueio;
5. a exclusão confirma a transação;
6. a criação prossegue, mas não encontra a obra;
7. nenhum installment é persistido para a obra removida.

**Cenário B — criação confirma primeiro:**

1. a transação de criação bloqueia a obra;
2. cria e persiste o installment;
3. a transação de exclusão tenta bloquear a obra e aguarda;
4. a criação confirma a transação;
5. a exclusão prossegue e verifica a existência do installment;
6. a exclusão é rejeitada;
7. a obra e o installment permanecem preservados.

Ambos os cenários foram validados em `WorkDeletionIntegrationTests`, utilizando transações independentes no PostgreSQL.

A foreign key `fk_installment_work`, introduzida pela V10, complementa o bloqueio pessimista.

Ela impede que operações SQL fora do fluxo de serviço removam uma obra que ainda possui installments.

### 8.8. Limites da coordenação

O bloqueio protege os fluxos que utilizam a mesma estratégia de acesso à linha.

Ele não torna operações de filesystem transacionais, não elimina falhas de infraestrutura e não substitui as restrições relacionais do banco.

A coordenação também pressupõe que outros fluxos que modifiquem a mesma entidade respeitem os mecanismos de bloqueio definidos.

Testes concorrentes baseados em sincronização de threads e janelas de tempo oferecem evidência de comportamento sob os cenários exercitados, mas não demonstram ausência de todas as condições de corrida possíveis.

A integridade referencial da V10 fornece uma camada adicional de proteção, especialmente para operações que não utilizam diretamente os serviços de domínio.

## 9. Armazenamento binário

O Metopa mantém arquivos binários fora do PostgreSQL.

O banco persiste as referências necessárias para localizar o conteúdo.

A interface `BinaryStorage` abstrai o armazenamento físico.

### 9.1. Storage keys

A chave de armazenamento, denominada `storageKey`, é gerada internamente.

Ela não é fornecida diretamente pelo cliente e não é exposta nos contratos HTTP públicos.

Os endpoints retornam o conteúdo binário por meio de identificadores de domínio, como `pageId` e `workId`.

### 9.2. Filesystem local

No MVP, os arquivos são armazenados em um diretório local configurado para a aplicação.

Essa implementação permite executar o projeto sem contratar um serviço externo de object storage.

A abstração `BinaryStorage` reduz o acoplamento entre os módulos de domínio e o mecanismo físico utilizado.

### 9.3. Proteção de caminhos

As operações de leitura e exclusão validam a resolução dos caminhos para impedir acesso fora da raiz de armazenamento configurada.

A exclusão de arquivos inexistentes é idempotente.

### 9.4. Limite transacional

O PostgreSQL oferece transações para os registros relacionais.

O filesystem local não participa automaticamente dessas transações.

Assim, uma operação que combina persistência de metadados e alteração de arquivos não possui atomicidade distribuída.

O Metopa utiliza mecanismos compensatórios para reduzir as inconsistências nesses fluxos.

## 10. Consistência transacional entre PostgreSQL e filesystem

A implementação atual diferencia cinco situações:

1. exclusão de páginas e installments existentes;
2. upload de conteúdo que está sendo criado;
3. substituição de uma capa existente por um novo arquivo;
4. remoção explícita de uma capa;
5. exclusão de uma obra que pode possuir uma capa.

Cada situação exige coordenação entre os registros do PostgreSQL e os arquivos administrados por `BinaryStorage`.

### 10.1. Exclusão física após commit

Nas operações `deletePage()` e `deleteInstallment()`, a aplicação não remove os arquivos imediatamente após enviar a exclusão ao PostgreSQL.

O `PublicationService` utiliza um helper compartilhado, `deleteFilesAfterCommit()`, para registrar a limpeza física após a confirmação da transação.

O mecanismo utiliza:

- `TransactionSynchronizationManager`;
- `TransactionSynchronization`;
- callback `afterCommit()`.

Fluxo:

```text
DELETE de página ou installment
    |
    v
Validar estado e autorização
    |
    v
Remover registros na transação
    |
    v
Registrar callback afterCommit()
    |
    +----------------------------+
    |                            |
    v                            v
 COMMIT                       ROLLBACK
    |                            |
    v                            v
Excluir arquivos             Preservar arquivos
```

A exclusão física acontece somente quando o banco confirma a remoção dos registros.

Se a transação sofrer rollback, a limpeza física não é executada.

### 10.2. Exclusão de installments

A exclusão de installments em `DRAFT` utiliza o relacionamento `ON DELETE CASCADE` já existente no PostgreSQL.

A operação:

1. localiza e bloqueia o installment;
2. valida a propriedade da obra;
3. verifica se o installment está em `DRAFT`;
4. coleta os `storageKey` das páginas associadas;
5. exclui o registro do installment;
6. utiliza `flush()` para enviar a operação ao banco;
7. registra a limpeza física após commit.

A exclusão em cascata remove os registros das páginas.

Os arquivos correspondentes são excluídos somente depois da confirmação da transação.

A exclusão de installments `PUBLISHED` é rejeitada.

### 10.3. Compensação de uploads após rollback

Na criação de páginas, o fluxo é diferente.

O arquivo precisa ser armazenado antes de persistir a referência correspondente no PostgreSQL.

Se a transação falhar depois do armazenamento físico, o banco pode desfazer o registro enquanto o arquivo permanece no filesystem.

Para evitar esse comportamento em rollbacks normais, `createPage()` registra uma compensação logo após o armazenamento bem-sucedido.

O mecanismo utiliza `TransactionSynchronization.afterCompletion()`.

O callback verifica o estado de conclusão da transação.

Quando recebe `STATUS_ROLLED_BACK`, solicita a exclusão do arquivo recém-criado através de `BinaryStorage`.

Quando a transação é confirmada, o arquivo permanece armazenado.

Fluxo:

```text
Upload de página
    |
    v
Validar installment e propriedade
    |
    v
Armazenar arquivo em BinaryStorage
    |
    v
Registrar callback afterCompletion()
    |
    v
Persistir metadados no PostgreSQL
    |
    +----------------------------+
    |                            |
    v                            v
 COMMIT                       ROLLBACK
    |                            |
    v                            v
Preservar arquivo            Excluir arquivo
                             recém-criado
```

### 10.4. Consistência transacional de capas

O módulo `catalog` aplica compensações na criação e na substituição de capas por meio de `CatalogService.setCover()`.

Após adquirir o bloqueio pessimista da obra e validar seu proprietário, o método:

1. captura o `previousStorageKey` da capa anterior, se existir;
2. armazena o novo arquivo utilizando `BinaryStorage.store()`;
3. registra uma sincronização transacional com `afterCommit()` e `afterCompletion()`;
4. atualiza a referência e o `Content-Type` da capa na entidade `Work`;
5. deixa a confirmação ou reversão dos dados relacionais sob responsabilidade da transação PostgreSQL.

**Quando há commit:** o arquivo novo é preservado e `afterCommit()` solicita a exclusão do arquivo anterior, desde que exista uma chave anterior diferente da nova.

**Quando há rollback:** `afterCompletion(STATUS_ROLLED_BACK)` solicita a exclusão do arquivo recém-armazenado. A referência anterior permanece persistida no banco e seu arquivo não é excluído.

**No primeiro upload:** como não existe capa anterior, não há arquivo antigo a remover após commit. Se a transação for revertida, o novo arquivo é excluído pela compensação.

```text
Upload ou substituição de capa
    |
    v
Bloquear obra com PESSIMISTIC_WRITE
    |
    v
Validar proprietário e capturar chave anterior
    |
    v
Armazenar nova capa em BinaryStorage
    |
    v
Registrar callbacks e atualizar Work
    |
    +-----------------------------------------+
    |                                         |
    v                                         v
 COMMIT                                   ROLLBACK
    |                                         |
    v                                         v
Preservar capa nova                     Preservar capa antiga
Excluir capa antiga                     Excluir capa nova
(se existir e for diferente)            via afterCompletion()
via afterCommit()
```

Essa estratégia foi validada por testes de integração com PostgreSQL e filesystem local para substituições confirmadas, rollbacks e operações concorrentes sobre a mesma obra.

### 10.5. Remoção transacional de capas

A operação `CatalogService.removeCover()` permite que o proprietário remova explicitamente a capa de uma obra.

O endpoint HTTP correspondente é:

```http
DELETE /api/works/{workId}/cover
```

A operação reutiliza a mesma estratégia de bloqueio pessimista adotada para substituições de capas e atualizações de metadados.

O fluxo é:

1. localizar a obra utilizando `WorkRepository.findByIdForUpdate()`;
2. validar que o usuário autenticado é proprietário da obra;
3. capturar a referência da capa atual;
4. retornar normalmente se a obra não possuir capa;
5. remover `coverStorageKey` e `coverContentType` através de `Work.clearCover()`;
6. atualizar `updatedAt` na entidade;
7. registrar um callback `afterCommit()` para excluir o arquivo físico anterior;
8. confirmar ou reverter a alteração através da transação PostgreSQL.

O método de domínio `Work.clearCover()` modifica apenas os atributos associados à capa e a data de atualização.

A entidade `Work` permanece persistida, assim como seus metadados e relacionamentos.

**Quando há commit:**

- a referência da capa é removida no PostgreSQL;
- o `Content-Type` da capa passa a ser `null`;
- o callback `afterCommit()` solicita a exclusão do arquivo anterior;
- a obra permanece existente e pode receber uma nova capa posteriormente.

**Quando há rollback:**

- a referência anterior permanece persistida;
- o `Content-Type` original é preservado;
- o callback `afterCommit()` não é executado;
- o arquivo físico original permanece disponível.

Fluxo:

```text
Remoção de capa
    |
    v
Bloquear obra com PESSIMISTIC_WRITE
    |
    v
Validar proprietário
    |
    v
Verificar existência de capa
    |
    +----> Sem capa
    |      Retornar normalmente
    |
    v
Capturar storageKey anterior
    |
    v
Limpar referência e Content-Type em Work
    |
    v
Registrar callback afterCommit()
    |
    +--------------------------------+
    |                                |
    v                                v
 COMMIT                          ROLLBACK
    |                                |
    v                                v
Referência removida             Referência preservada
Arquivo excluído                Arquivo preservado
após commit
```

#### Idempotência

A remoção de capas é idempotente.

Quando a obra existe, mas não possui capa, `removeCover()` retorna sem modificar seus atributos e sem registrar uma operação de exclusão física.

Chamadas repetidas ao endpoint retornam `204 No Content`, desde que o usuário esteja autorizado e a obra continue existindo.

A idempotência não elimina as validações de propriedade ou existência da obra.

#### Obra inexistente

Quando `findByIdForUpdate()` não encontra a obra, o serviço lança `WorkNotFoundException`.

O `CatalogExceptionHandler` traduz essa exceção para uma resposta HTTP `404 Not Found`, utilizando `ProblemDetail`.

Isso diferencia uma obra existente sem capa de uma obra inexistente.

#### Limitação da exclusão após commit

Assim como nas outras operações compensatórias, a exclusão física depende da execução bem-sucedida de `BinaryStorage.delete()` após a confirmação da transação.

Se a exclusão física falhar, a referência já poderá estar removida do PostgreSQL enquanto o arquivo anterior permanece armazenado.

Esse cenário exige mecanismos adicionais de reconciliação para uma garantia durável de limpeza, ainda não implementados no MVP.

### 10.6. Exclusão transacional de obras

A operação `CatalogService.deleteWork()` permite que o proprietário exclua uma obra que não possui installments.

O endpoint HTTP correspondente é:

```http
DELETE /api/works/{workId}
```

A exclusão utiliza uma transação PostgreSQL e bloqueio pessimista sobre a linha da obra.

O método segue este fluxo:

1. localizar a obra utilizando `WorkRepository.findByIdForUpdate()`;
2. validar que o usuário é proprietário da obra;
3. consultar `WorkDeletionGuard.hasInstallments(workId)`;
4. rejeitar a operação se houver qualquer installment;
5. capturar o `coverStorageKey` atual, caso exista;
6. remover a entidade `Work` pelo repository;
7. registrar um callback `afterCommit()` para remover o arquivo físico da capa, quando aplicável;
8. confirmar ou reverter a transação PostgreSQL.

A verificação de propriedade acontece antes da consulta aos installments.

Isso impede que usuários sem autorização obtenham informações sobre a possibilidade de exclusão da obra.

**Quando a obra possui installments:**

- a operação lança `WorkHasInstallmentsException`;
- o `CatalogExceptionHandler` retorna `409 Conflict`;
- a obra permanece preservada;
- os installments e páginas permanecem preservados;
- a capa física não é removida.

**Quando a obra não possui installments:**

- a exclusão pode prosseguir;
- os gêneros associados são removidos pelo relacionamento existente desde a V9;
- referências de biblioteca e progresso de leitura são removidas pelas restrições da V10;
- a remoção do arquivo físico da capa é condicionada ao commit.

A operação não possui uma exclusão em cascata de installments.

O PostgreSQL utiliza `ON DELETE RESTRICT` para impedir a remoção de obras que ainda possuem installments.

#### Exclusão de obra sem capa

Quando a obra não possui capa, a exclusão não registra uma operação de limpeza física.

O registro da obra é removido normalmente, desde que não possua installments.

A ausência de capa não torna a exclusão idempotente quanto à resposta HTTP.

Após a exclusão, uma nova tentativa retorna `404 Not Found`.

#### Exclusão de obra com capa

Quando a obra possui capa, o serviço captura a chave de armazenamento antes de remover o registro.

O arquivo não é excluído imediatamente.

A limpeza é registrada em um callback `TransactionSynchronization.afterCommit()`.

**Quando há commit:**

- o registro da obra é removido do PostgreSQL;
- as referências relacionadas abrangidas por `ON DELETE CASCADE` são removidas;
- o callback solicita a exclusão física da capa através de `BinaryStorage`.

**Quando há rollback:**

- a obra permanece persistida;
- seus metadados e referências são preservados;
- o callback `afterCommit()` não é executado;
- o arquivo físico da capa permanece disponível.

Fluxo:

```text
Exclusão de obra
    |
    v
Bloquear Work com PESSIMISTIC_WRITE
    |
    v
Validar proprietário
    |
    v
Verificar existência de installments
    |
    +----> Existem installments
    |      Rejeitar com 409 Conflict
    |
    v
Capturar coverStorageKey
    |
    v
Remover Work na transação PostgreSQL
    |
    v
Registrar afterCommit() se houver capa
    |
    +--------------------------------+
    |                                |
    v                                v
 COMMIT                          ROLLBACK
    |                                |
    v                                v
Obra removida                    Obra preservada
Referências removidas            Referências preservadas
Capa excluída após commit        Capa preservada
```

#### Coordenação com a criação de installments

A exclusão de obras utiliza bloqueio pessimista para impedir condições de corrida com a criação de installments.

A criação utiliza `CatalogService.findWorkForInstallmentCreation()`, enquanto a exclusão utiliza `CatalogService.deleteWork()`.

Ambas adquirem o bloqueio da mesma linha de `Work`.

A coordenação impede que a exclusão conclua baseada em uma verificação de installments desatualizada.

Os dois possíveis ordenamentos de commit foram validados por testes de integração.

#### Limitação da exclusão após commit

A limpeza física da capa não faz parte da transação PostgreSQL.

Se `BinaryStorage.delete()` falhar durante o callback `afterCommit()`, o registro da obra já terá sido excluído do banco.

A falha poderá deixar um arquivo órfão e a exceção poderá ser propagada ao fluxo da requisição, sem desfazer a transação confirmada.

O MVP ainda não possui uma fila persistente de limpeza ou mecanismo automático de reconciliação de arquivos órfãos.

### 10.7. Comparação das estratégias

| Operação | Momento da limpeza | Objetivo |
|----------|--------------------|----------|
| Exclusão de página | Após commit | Não remover arquivo de registro que pode ser restaurado |
| Exclusão de installment | Após commit | Não remover arquivos de páginas cuja exclusão pode ser revertida |
| Upload de página | Após rollback | Não manter arquivo sem registro persistido |
| Primeiro upload de capa | Após rollback | Não manter capa nova se o registro não for confirmado |
| Substituição de capa — commit | Após commit | Remover arquivo da capa anterior sem afetar a nova referência |
| Substituição de capa — rollback | Após rollback | Remover arquivo recém-armazenado e preservar a capa anterior |
| Remoção de capa — commit | Após commit | Excluir o arquivo somente depois de remover sua referência |
| Remoção de capa — rollback | Nenhuma limpeza física | Preservar a referência anterior e o arquivo correspondente |
| Exclusão de obra sem capa | Nenhuma limpeza física | Remover a obra sem tentar excluir arquivos inexistentes |
| Exclusão de obra com capa — commit | Após commit | Remover o arquivo físico somente depois da exclusão confirmada da obra |
| Exclusão de obra com capa — rollback | Nenhuma limpeza física | Preservar a obra, sua referência e o arquivo da capa |

As estratégias são complementares.

O armazenamento físico permanece encapsulado por `BinaryStorage`.

A coordenação transacional pertence aos fluxos de negócio implementados nos módulos `publication` e `catalog`.

### 10.8. Limitações conhecidas

Os callbacks transacionais não transformam PostgreSQL e filesystem em um sistema com transações distribuídas.

A solução atual não oferece garantia durável de limpeza em todos os cenários.

Situações que ainda podem produzir arquivos órfãos ou inconsistências incluem:

- encerramento abrupto da JVM antes da execução de um callback;
- falhas de acesso ao filesystem durante a exclusão;
- interrupção da aplicação após o commit e antes de completar a limpeza física;
- resultado transacional incerto, incluindo `STATUS_UNKNOWN`;
- falhas entre o armazenamento físico e o registro da compensação;
- outros fluxos binários que não adotem essa estratégia.

A compensação de rollback cobre o fluxo de `PublicationService.createPage()` e o fluxo de `CatalogService.setCover()` nos rollbacks observados pelo Spring.

O fluxo de `CatalogService.removeCover()` preserva o arquivo original durante a transação e solicita sua exclusão apenas após o commit.

O fluxo de `CatalogService.deleteWork()` também preserva a capa durante a transação, removendo o arquivo somente após a confirmação da exclusão da obra.

Se `binaryStorage.delete()` falhar durante `afterCommit()`, a transação relacional já terá sido confirmada. A limpeza poderá ficar pendente e a exceção poderá ser propagada, sem desfazer o commit.

Os callbacks atuais não persistem tarefas de limpeza para repetição após falha ou reinicialização da aplicação.

### 10.9. Possíveis evoluções

Uma implementação futura poderá avaliar mecanismos de limpeza durável ou reconciliação de arquivos.

Entre as possibilidades estão:

- identificação de arquivos sem referência no PostgreSQL;
- verificação periódica de inconsistências;
- registro persistente de operações de limpeza pendentes;
- estratégias de repetição para falhas temporárias.

Esses mecanismos não fazem parte da implementação atual do MVP.

A escolha de uma solução deverá considerar necessidades reais de operação e armazenamento.

## 11. Segurança

O Metopa utiliza Spring Security com autenticação baseada em sessão HTTP.

O identificador da sessão é transmitido por cookie.

### 11.1. Senhas

As senhas são processadas utilizando `PasswordEncoder` e armazenadas como hash bcrypt.

Senhas não são persistidas em texto puro.

### 11.2. Proteção CSRF

A proteção CSRF permanece habilitada.

Operações protegidas que alteram estado exigem um token válido.

A remoção de capas utiliza o método HTTP `DELETE` e exige um token CSRF válido.

A exclusão de obras também utiliza `DELETE` e exige autenticação e proteção CSRF.

Uma requisição autenticada sem o token exigido recebe `403 Forbidden`.

### 11.3. Autorização por propriedade

A autorização administrativa utiliza a identidade do usuário autenticado e a propriedade da obra.

O cliente não informa diretamente o `ownerId` nas operações de gerenciamento.

A propriedade é verificada pela aplicação antes de permitir alterações ou leituras administrativas de conteúdo.

Na remoção de capas, a validação de propriedade acontece antes de modificar a entidade ou registrar a limpeza física.

Tentativas de remoção por usuários que não são proprietários recebem `403 Forbidden`, preservando a referência e o arquivo da capa.

Na exclusão de obras, a validação de propriedade ocorre antes da consulta aos installments.

Usuários que não são proprietários não podem excluir a obra, independentemente de ela possuir ou não installments.

### 11.4. Ordem das validações

Na exclusão de installments, a validação de propriedade ocorre antes da verificação do estado `DRAFT` ou `PUBLISHED`.

Isso evita revelar o estado de publicação para um usuário que não possui autorização para gerenciar a obra.

Na remoção de capas, a propriedade também é validada antes de verificar se existe uma capa a remover.

Assim, a idempotência da operação não permite que um usuário sem autorização modifique ou consulte indevidamente o estado administrativo da obra.

Na exclusão de obras, a ordem das validações é:

1. localizar e bloquear a obra;
2. verificar se o usuário é proprietário;
3. consultar a existência de installments;
4. excluir a obra somente quando não existirem installments.

Essa ordem impede que usuários não autorizados descubram se determinada obra possui installments através da tentativa de exclusão.

### 11.5. Separação de conteúdo público

Os endpoints públicos apresentam somente conteúdo que atende às regras de publicação.

Drafts são tratados como indisponíveis nessas operações.

As consultas administrativas possuem contratos específicos e exigem autorização.

A remoção da capa não modifica os estados `DRAFT` ou `PUBLISHED` dos installments.

Uma obra publicada continua disponível publicamente após a remoção de sua capa, mas o endpoint público da capa deixa de disponibilizar conteúdo até que uma nova seja definida.

A exclusão de uma obra publicada não é permitida no MVP enquanto existirem installments associados.

### 11.6. Respostas HTTP

O projeto utiliza respostas HTTP adequadas para representar falhas de autenticação, autorização e regras de negócio.

Entre os códigos utilizados estão:

- `401 Unauthorized`;
- `403 Forbidden`;
- `404 Not Found`;
- `409 Conflict`.

A remoção de capas utiliza:

- `204 No Content` para remoção bem-sucedida;
- `204 No Content` quando a obra existe, mas não possui capa;
- `401 Unauthorized` quando não existe sessão autenticada;
- `403 Forbidden` quando o usuário não é proprietário ou o token CSRF é inválido;
- `404 Not Found` quando a obra não existe.

A exclusão de obras utiliza:

- `204 No Content` quando uma obra sem installments é excluída;
- `401 Unauthorized` quando não existe sessão autenticada;
- `403 Forbidden` quando o usuário não é proprietário ou o token CSRF é inválido;
- `404 Not Found` quando a obra não existe;
- `409 Conflict` quando a obra possui installments `DRAFT` ou `PUBLISHED`.

O tratamento de `WorkNotFoundException` e `WorkHasInstallmentsException` é realizado em `CatalogExceptionHandler`, com respostas estruturadas através de `ProblemDetail`.

Para o conflito de exclusão de obras, são utilizados:

```text
Title:  Work cannot be deleted
Detail: A work with installments cannot be deleted.
```

Os contratos específicos de cada endpoint são descritos em [API.md](API.md).

## 12. Testes de arquitetura e integração

Na validação completa de 02/11/2026, a suíte automatizada executou **273 testes com sucesso**, sem falhas, erros ou testes ignorados.

Comando utilizado:

```bash
./mvnw clean test
```

Resultado:

```text
Tests run: 273
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A validação anterior, realizada em 29/10/2026, havia executado 258 testes aprovados após a implementação da remoção de capas.

A feature de exclusão de obras acrescentou 15 cenários de integração à suíte.

Entre os aspectos relacionados à arquitetura estão:

- validação das fronteiras entre módulos com Spring Modulith;
- regras de domínio;
- persistência real no PostgreSQL;
- autenticação e autorização;
- proteção CSRF;
- integração com Spring Web MVC;
- armazenamento e leitura de arquivos;
- exclusões transacionais;
- compensação de upload em rollback para páginas e capas;
- exclusão de capas anteriores após commit de substituição;
- remoção física de capas após commit;
- preservação de referências e arquivos após rollback de remoção;
- idempotência na remoção de capas de obras sem capa;
- tratamento de obras inexistentes;
- concorrência com transações independentes;
- bloqueios pessimistas para installments e obras;
- substituições concorrentes de capas;
- concorrência entre atualização de metadados e substituição de capa;
- concorrência entre remoção e substituição de capas;
- exclusão de obras sem installments;
- rejeição da exclusão de obras com installments `DRAFT` e `PUBLISHED`;
- preservação de obras e capas após rollback de exclusão;
- concorrência entre exclusão de obras e criação de installments;
- integridade referencial introduzida pela migration V10;
- remoção de referências de biblioteca e progresso de leitura;
- proteção contra exclusão direta no PostgreSQL;
- remoção de gêneros associados à obra;
- preservação de registros e arquivos após rollback;
- validação unitária dos callbacks transacionais.

### Testes específicos da remoção de capas

A feature `creator-cover-removal` introduziu testes de integração e testes unitários para validar:

1. remoção de capa existente após commit;
2. preservação da capa quando a remoção sofre rollback;
3. remoção idempotente de capa de uma obra sem capa;
4. rejeição de remoção por usuário que não é proprietário;
5. rejeição de remoção sem autenticação;
6. rejeição de remoção sem token CSRF;
7. resposta `404 Not Found` para obra inexistente;
8. concorrência entre remoção e substituição de capas;
9. execução da exclusão física após commit em teste unitário;
10. ausência de exclusão física após rollback em teste unitário.

Os testes de integração exercitam transações reais, persistência no PostgreSQL e armazenamento no filesystem local.

Os testes unitários complementam essa validação, verificando as interações com `WorkRepository`, `BinaryStorage` e os callbacks transacionais.

### Testes específicos da exclusão de obras

A feature `creator-work-deletion` introduziu a classe `WorkDeletionIntegrationTests`, responsável pela validação dos seguintes cenários:

1. exclusão de obra sem installments pelo proprietário;
2. rejeição da exclusão de obra com installment `DRAFT`;
3. rejeição da exclusão de obra com installment `PUBLISHED`;
4. remoção física da capa após a exclusão confirmada da obra;
5. preservação da obra e da capa quando a transação sofre rollback;
6. rejeição da exclusão por usuário que não é proprietário;
7. rejeição da exclusão sem autenticação;
8. rejeição da exclusão sem token CSRF;
9. resposta `404 Not Found` para obra inexistente;
10. rejeição da criação de installment quando a exclusão da obra confirma primeiro;
11. rejeição da exclusão da obra quando a criação de installment confirma primeiro;
12. remoção de entradas de biblioteca associadas a uma obra excluída;
13. remoção de registros de progresso de leitura associados a uma obra excluída;
14. proteção da foreign key `fk_installment_work` contra exclusão direta de obras com installments;
15. remoção dos gêneros associados à obra excluída.

Os cenários de concorrência utilizam transações independentes e sincronização entre threads.

Os cenários transacionais verificam o estado real do PostgreSQL e, quando aplicável, os arquivos físicos administrados por `BinaryStorage`.

Os cenários de integridade referencial verificam as restrições introduzidas pela migration V10.

Os testes de repositório de installments, páginas, biblioteca e progresso de leitura foram ajustados para criar registros válidos de obras antes de persistir entidades dependentes.

Essas alterações preservam o propósito original dos testes e respeitam as novas foreign keys.

A validação completa confirmou:

```text
Tests run: 273, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

O teste de modularidade ajuda a identificar dependências indevidas entre módulos.

A estratégia completa de testes é descrita em [TESTING.md](TESTING.md).

## 13. Princípios de evolução

A evolução do Metopa segue algumas diretrizes:

- preservar os limites entre módulos;
- manter regras de negócio próximas de seus respectivos domínios;
- evitar dependências diretas entre entidades JPA de módulos diferentes;
- utilizar APIs internas para comunicação entre módulos;
- manter detalhes de armazenamento encapsulados;
- reforçar integridade com validações e restrições no PostgreSQL;
- proteger operações concorrentes quando necessário;
- introduzir abstrações quando exista uma necessidade concreta;
- desenvolver funcionalidades em incrementos pequenos;
- validar alterações por testes automatizados;
- registrar decisões e limitações arquiteturais relevantes.

### Evoluções futuras possíveis

Algumas necessidades poderão ser avaliadas em etapas posteriores:

- implementação das funcionalidades do módulo `moderation`;
- armazenamento binário em infraestrutura diferente do filesystem local;
- reconciliação de arquivos órfãos;
- mecanismos duráveis para operações de limpeza;
- observabilidade e requisitos adicionais de operação;
- revisão das estratégias de consistência conforme o volume de dados e usuários aumentar.

Essas possibilidades não constituem funcionalidades já implementadas.

## Documentação relacionada

- [README — Visão geral do Metopa](../README.md)
- [API HTTP](API.md)
- [Estratégia de testes](TESTING.md)
