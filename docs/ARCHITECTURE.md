# Metopa — Arquitetura

Documentação da arquitetura do backend do Metopa, incluindo organização modular, responsabilidades dos módulos, comunicação entre domínios, persistência, descoberta pública, moderação administrativa, segurança, concorrência e consistência entre PostgreSQL e filesystem.

O documento descreve as decisões adotadas durante o desenvolvimento do MVP, as garantias oferecidas pela implementação atual e suas limitações conhecidas.

## 1. Visão geral

O Metopa é uma plataforma para publicação, descoberta e leitura de quadrinhos, mangás, graphic novels, webtoons e outros formatos de narrativa visual.

O backend é implementado em Java 25 com Spring Boot 4.1.1 e Spring Modulith.

A arquitetura utiliza um **monólito modular**: uma única aplicação executável, organizada em módulos com responsabilidades e fronteiras explícitas.

A infraestrutura do MVP é executada localmente, sem dependência obrigatória de serviços cloud pagos.

### Principais componentes

| Componente | Tecnologia | Responsabilidade |
|------------|------------|------------------|
| Aplicação | Java 25 / Spring Boot 4.1.1 | Execução do backend |
| Modularidade | Spring Modulith | Organização e verificação das fronteiras entre módulos |
| HTTP | Spring Web MVC | Exposição dos endpoints |
| Segurança | Spring Security | Sessões, autenticação, autorização por papéis e CSRF |
| Validação | Bean Validation | Validação dos contratos de entrada |
| Persistência | Spring Data JPA / Hibernate | Mapeamento e acesso aos dados relacionais |
| Banco de dados | PostgreSQL 18 | Persistência, integridade e consultas públicas |
| Migrações | Flyway | Evolução versionada do schema |
| Armazenamento binário | Filesystem local | Armazenamento físico de páginas e capas |
| Infraestrutura local | Docker Compose | Execução do PostgreSQL |
| Build | Maven Wrapper | Compilação, execução, testes e empacotamento |
| Integração contínua | GitHub Actions | Validação automatizada do backend |

O backend inclui os domínios de identidade, catálogo, publicação, biblioteca, leitura, armazenamento binário e moderação administrativa.

O MVP prioriza a implementação e a validação do backend antes do desenvolvimento da interface.

## 2. Decisão arquitetural: monólito modular

O Metopa utiliza Spring Modulith para manter os domínios organizados dentro de uma única aplicação.

Essa escolha permite:

- desenvolver o MVP sem infraestrutura distribuída;
- manter uma única aplicação para execução e deployment;
- estabelecer responsabilidades claras entre módulos;
- reduzir o acoplamento entre domínios;
- realizar testes de integração sem introduzir comunicação entre serviços;
- utilizar transações locais para coordenar operações entre módulos;
- evoluir a arquitetura conforme surgirem necessidades concretas.

O uso de um monólito modular não elimina a necessidade de definir fronteiras arquiteturais.

As dependências entre módulos Java são verificadas por testes utilizando Spring Modulith.

### Por que não microsserviços?

Nesta fase, não existem requisitos demonstrados que justifiquem a complexidade operacional de múltiplos serviços independentes.

Microsserviços acrescentariam preocupações como comunicação distribuída, observabilidade, deployment individual, consistência eventual e infraestrutura adicional.

O monólito modular permite priorizar as regras de negócio e a estabilidade do MVP.

A arquitetura pode evoluir no futuro, mas não pressupõe a extração obrigatória dos módulos em serviços separados.

### Transações entre módulos

Os módulos compartilham a mesma aplicação e o mesmo banco PostgreSQL.

Isso permite coordenar operações relacionadas por meio de transações Spring.

Por exemplo, a resolução administrativa de uma denúncia pode envolver:

- alteração do estado da denúncia em `moderation`;
- alteração da visibilidade da obra em `catalog`;
- persistência da auditoria em `moderation`.

Quando essas operações participam da mesma transação, uma falha pode provocar o rollback das alterações relacionais.

Essa garantia não se estende automaticamente ao filesystem, que utiliza mecanismos específicos de compensação.

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

Responsável por contas de usuários, autenticação e papéis de acesso.

Principais funcionalidades:

- criação de contas;
- normalização de username e e-mail;
- validação dos dados;
- unicidade de username e e-mail;
- identificação de usuários por UUID;
- armazenamento de senhas utilizando hash bcrypt;
- autenticação baseada em sessão HTTP;
- login e logout;
- consulta do usuário autenticado;
- integração com Spring Security;
- proteção CSRF;
- definição dos papéis `USER` e `ADMIN`;
- disponibilização das autoridades de acesso ao Spring Security.

O domínio utiliza `AccountRole` para representar os papéis de uma conta.

As contas regulares utilizam o papel `USER`.

O papel `ADMIN` é utilizado para autorizar operações administrativas de moderação.

O Spring Security utiliza autoridades como `ROLE_ADMIN` para aplicar as regras de autorização.

A identidade autenticada é obtida pela aplicação e utilizada nas operações que precisam reconhecer o usuário.

O cliente não deve fornecer diretamente o identificador do administrador responsável por uma ação de moderação.

As regras de autenticação não devem ser duplicadas nos módulos de catálogo, publicação, biblioteca ou moderação.

### 3.2. Catalog

Responsável pelas obras, seus metadados e seu estado de visibilidade.

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
- exclusão física de capas após operações confirmadas;
- exclusão de obras sem installments;
- rejeição da exclusão de obras com installments;
- coordenação de operações concorrentes por bloqueio pessimista;
- coordenação da criação de installments com a exclusão de obras;
- controle da visibilidade `PUBLIC` e `RESTRICTED`;
- restrição administrativa de obras;
- restauração administrativa de obras;
- exposição de informações da obra para outros módulos através de APIs públicas do módulo.

A propriedade de uma obra pertence ao domínio `catalog`.

Outros módulos não devem modificar diretamente suas entidades ou acessar livremente seus repositories.

Quando `publication` precisa validar a propriedade de uma obra, utiliza uma API disponibilizada por `catalog`.

Um exemplo é `CatalogService.findWork()`, que fornece uma referência da obra com as informações necessárias à operação.

Para a criação de installments, o catálogo disponibiliza `CatalogService.findWorkForInstallmentCreation()`, responsável por adquirir bloqueio pessimista na obra dentro de uma transação existente.

A exclusão de obras é coordenada por `CatalogService.deleteWork()`.

O módulo define o contrato `WorkDeletionGuard`, utilizado para verificar se existem installments antes de permitir a exclusão.

A implementação concreta desse contrato pertence a `publication`.

Essa separação permite que o catálogo aplique sua regra de exclusão sem acessar diretamente os repositories internos de publicação.

#### Visibilidade das obras

A entidade `Work` possui um estado de visibilidade representado por `WorkVisibility`.

Estados:

```text
PUBLIC
RESTRICTED
```

A visibilidade é independente da publicação dos installments.

Uma obra `PUBLIC` pode participar da descoberta quando possui conteúdo publicado.

Uma obra `RESTRICTED` permanece persistida, mas não deve ser disponibilizada nos endpoints públicos.

As transições são realizadas através do domínio `catalog`.

Operações disponibilizadas por `CatalogService`:

```text
restrictWork(UUID workId)
restoreWork(UUID workId)
hasPublicVisibility(UUID workId)
```

As operações de restrição e restauração localizam a obra utilizando bloqueio pessimista e delegam a alteração de estado à entidade `Work`.

A entidade mantém as regras de transição através de:

```text
restrictVisibility()
restoreVisibility()
```

O módulo `moderation` solicita essas operações através de `CatalogService`.

Ele não altera diretamente a entidade JPA `Work`.

A restrição não exclui os registros ou arquivos associados à obra.

### 3.3. Publication

Responsável pelo conteúdo publicado nas obras e pela descoberta pública.

O módulo administra installments e páginas.

Um installment pode representar:

```text
CHAPTER
ISSUE
EPISODE
STRIP
```

Estados:

```text
DRAFT
PUBLISHED
```

Responsabilidades principais:

- criação de installments;
- atualização de installments em `DRAFT`;
- exclusão de installments em `DRAFT`;
- upload de páginas;
- exclusão e reordenação de páginas;
- publicação de installments;
- validação das regras de imutabilidade;
- consulta administrativa para o proprietário;
- leitura pública do conteúdo publicado;
- descoberta pública de obras;
- busca parcial por título;
- filtragem por gênero;
- combinação de filtros de título e gênero;
- paginação, contagem e ordenação;
- verificação da visibilidade pública;
- bloqueio de acesso público a obras restritas;
- coordenação transacional com arquivos binários;
- implementação do contrato utilizado na exclusão de obras;
- utilização do bloqueio da obra durante a criação de installments.

Operações sobre installments publicados não podem alterar seus metadados ou páginas no MVP.

A classe `PublicationWorkDeletionGuard`, localizada em `publication.internal`, implementa o contrato `WorkDeletionGuard`, definido por `catalog`.

Essa implementação consulta `InstallmentRepository.existsByWorkId()`.

O repository permanece encapsulado em `publication.internal`.

#### Responsabilidade pela descoberta pública

A descoberta pública é coordenada por `publication` porque a disponibilidade de uma obra depende da existência de conteúdo publicado.

O `PublicationService` utiliza `InstallmentRepository` para identificar as obras elegíveis para descoberta.

Depois de selecionar os identificadores, utiliza `CatalogService.findWorkSummary()` para recuperar os metadados necessários à resposta.

A implementação estabelece as seguintes responsabilidades:

- `publication` determina a elegibilidade de publicação;
- `catalog` mantém os metadados e a visibilidade;
- o controller expõe os resultados HTTP.

A descoberta precisa satisfazer simultaneamente as condições:

```text
Installment.status = PUBLISHED
Work.visibility = PUBLIC
```

A implementação utiliza consultas SQL nativas que acessam tabelas pertencentes a módulos diferentes.

Essa dependência relacional é uma decisão arquitetural explícita do MVP.

### 3.4. Library

Responsável pela biblioteca pessoal dos usuários.

Principais funcionalidades:

- inclusão de obras na biblioteca;
- prevenção de entradas duplicadas;
- listagem das obras adicionadas;
- isolamento por usuário;
- remoção idempotente de entradas;
- restrição das operações a obras com conteúdo publicado.

A biblioteca não é responsável pela publicação ou moderação do conteúdo.

Sua responsabilidade é registrar a relação entre um usuário e as obras que ele adicionou.

A migration V10 introduziu uma foreign key entre `library_entry.work_id` e `work.id`, utilizando `ON DELETE CASCADE`.

Com isso, a exclusão de uma obra remove automaticamente suas referências de biblioteca.

A restrição administrativa de visibilidade não corresponde à exclusão da obra.

Consequentemente, uma restrição não deve ser confundida com uma operação de limpeza das entradas existentes de biblioteca.

### 3.5. Reading

Responsável pelo progresso de leitura.

Principais funcionalidades:

- registro da página atual;
- identificação da obra e do installment a partir da página;
- uma posição de leitura por usuário e obra;
- atualização do progresso existente;
- consulta da última posição registrada;
- validação de conteúdo publicado;
- bloqueio de criação e atualização de progresso em obras restritas.

O progresso de leitura é independente da biblioteca pessoal.

Uma obra não precisa estar adicionada à biblioteca para que seu progresso seja registrado.

A migration V10 introduziu uma foreign key entre `reading_progress.work_id` e `work.id`, utilizando `ON DELETE CASCADE`.

Essa restrição impede que referências permaneçam associadas a obras excluídas.

A moderação não utiliza exclusão física como mecanismo de restrição.

Portanto, o progresso de leitura existente permanece persistido quando uma obra recebe visibilidade `RESTRICTED`.

Novas atualizações de progresso são bloqueadas enquanto a obra estiver restrita.

### 3.6. Media

Responsável pela abstração de armazenamento de arquivos binários.

O módulo disponibiliza a interface `BinaryStorage`.

Principais operações:

- armazenar conteúdo binário;
- gerar internamente uma chave de armazenamento;
- recuperar conteúdo pela chave;
- excluir conteúdo;
- tratar de forma idempotente a exclusão de arquivos inexistentes;
- proteger leituras e exclusões contra caminhos fora da raiz configurada.

A implementação atual utiliza o filesystem local.

Páginas e capas compartilham essa abstração.

O módulo `media` não decide:

- se um installment pode ser publicado;
- se uma página pode ser excluída;
- se um usuário é proprietário de uma obra;
- se uma obra pode ser excluída;
- se uma obra deve ser restringida por moderação.

Essas regras pertencem aos módulos responsáveis pelos respectivos domínios.

O módulo `media` permanece responsável somente pelas operações de armazenamento físico.

### 3.7. Moderation

Responsável pelo registro de denúncias, análise administrativa, resolução de violações, restrição e restauração de obras e auditoria.

A moderação mínima está implementada no backend MVP.

Principais funcionalidades:

- registro de denúncias relacionadas a obras;
- validação da existência da obra denunciada;
- consulta administrativa paginada de denúncias;
- filtragem por status;
- início de análise administrativa;
- resolução sem violação;
- resolução com restrição de obra;
- restauração de obra restringida;
- validação das transições permitidas;
- persistência de justificativas administrativas;
- auditoria das ações;
- coordenação transacional com `catalog`;
- controle de concorrência com bloqueio pessimista.

#### Organização interna

O módulo contém contratos públicos, serviços, entidades internas, repositories e controllers HTTP.

Componentes principais:

```text
moderation
├── CreateReportCommand
├── ModerationDecision
├── ModerationReportSummary
├── ModerationService
├── ReportReason
├── ReportStatus
├── internal
│   ├── ModerationReport
│   ├── ModerationReportRepository
│   ├── ModerationAuditAction
│   ├── ModerationAuditEntry
│   └── ModerationAuditEntryRepository
└── web
    ├── WorkReportController
    ├── AdminModerationReportController
    ├── ModerationExceptionHandler
    └── contratos HTTP
```

`ModerationService` centraliza a orquestração das operações do domínio.

`ModerationReport` representa a denúncia e suas transições de estado.

`ModerationAuditEntry` representa o histórico das ações administrativas.

Os repositories permanecem em `moderation.internal`.

#### Ciclo de vida da denúncia

Estados:

```text
OPEN
UNDER_REVIEW
RESOLVED
```

Fluxo:

```text
OPEN
  |
  | startReview()
  v
UNDER_REVIEW
  |
  | resolveNoViolation()
  | ou
  | resolveWorkRestriction()
  v
RESOLVED
```

Uma denúncia começa em `OPEN`.

Somente denúncias abertas podem iniciar análise.

A resolução exige uma denúncia em `UNDER_REVIEW`.

Depois de resolvida, a denúncia mantém seu histórico e sua decisão.

#### Decisões

O domínio possui as decisões:

```text
NO_VIOLATION
WORK_RESTRICTED
```

`NO_VIOLATION` resolve a denúncia sem restringir a obra.

`WORK_RESTRICTED` resolve a denúncia e solicita ao catálogo a restrição da visibilidade.

A restauração não representa uma terceira decisão de resolução.

Ela é uma operação administrativa posterior.

#### Auditoria

A auditoria utiliza os eventos:

```text
REVIEW_STARTED
REPORT_RESOLVED
WORK_RESTORED
```

Cada entrada associa a operação à denúncia e ao administrador responsável.

Também registra a justificativa e a data de criação.

A restauração preserva o estado `RESOLVED` e a decisão `WORK_RESTRICTED` da denúncia.

O evento `WORK_RESTORED` registra a alteração administrativa posterior.

Essa abordagem mantém separados:

- o resultado histórico da denúncia;
- a visibilidade atual da obra;
- as ações administrativas executadas.

#### Integração com Catalog

O módulo `moderation` não modifica diretamente `Work`.

As operações de restrição e restauração são delegadas a `CatalogService`.

Isso mantém a responsabilidade da visibilidade dentro do catálogo.

O módulo de moderação coordena a operação completa, incluindo:

- validação da denúncia;
- transição de estado, quando aplicável;
- chamada ao catálogo;
- persistência da auditoria.

#### Autorização

Os endpoints administrativos utilizam o prefixo:

```text
/api/admin/moderation
```

O Spring Security exige `ROLE_ADMIN` para essas operações.

A identificação do administrador é obtida a partir da sessão autenticada.

Essa identidade é utilizada nos registros de auditoria.

## 4. Comunicação entre módulos

As interações entre módulos utilizam interfaces e APIs expostas pelos próprios módulos.

As entidades JPA e repositories são mantidos como detalhes internos.

O projeto evita relacionamentos JPA diretos entre entidades pertencentes a módulos diferentes.

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
    |      verifica propriedade
    |
    +----> Media
    |      armazena arquivo
    |      retorna storageKey
    |
    +----> PostgreSQL
           persiste metadados
```

O módulo `publication` coordena a operação.

O catálogo fornece as informações de propriedade.

O módulo `media` realiza o armazenamento.

O PostgreSQL mantém o registro e a referência.

### Exemplo: exclusão de uma obra

A exclusão de obras exige informações pertencentes ao módulo `publication`.

O catálogo não deve acessar diretamente os repositories internos de publicação.

Para preservar essa separação, utiliza o contrato `WorkDeletionGuard`.

```text
Cliente HTTP
    |
    v
WorkDeletionController
    |
    | identifica usuário
    v
CatalogService.deleteWork()
    |
    | bloqueia Work
    | valida proprietário
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
    v
PostgreSQL
```

O contrato pertence a `catalog`.

A implementação pertence a `publication`.

Essa abordagem permite consultar a existência de installments sem introduzir dependência direta sobre a persistência interna de publicação.

### Exemplo: descoberta pública paginada

Endpoint:

```http
GET /api/works
```

Parâmetros opcionais:

```text
title
genre
page
size
```

Fluxo:

```text
Cliente HTTP
    |
    v
PublishedWorkController
    |
    | recebe filtros
    | valida paginação
    v
PublicationService
    |
    | normaliza title
    | escapa caracteres especiais
    | seleciona consulta
    v
InstallmentRepository
    |
    | filtra PUBLISHED
    | filtra visibilidade PUBLIC
    | aplica título e gênero
    | agrupa por work_id
    | ordena e pagina
    v
PostgreSQL
    |
    | retorna Page<UUID>
    v
PublicationService
    |
    | consulta CatalogService
    | recupera WorkSummary
    | monta PublishedWorkSummary
    v
PublishedWorkController
    |
    | preserva paginação
    v
PublishedWorksPageResponse
```

A paginação é aplicada no PostgreSQL antes da recuperação dos resumos.

O serviço não carrega todas as obras publicadas para paginar a coleção em memória.

### Exemplo: registro de denúncia

```text
Cliente autenticado
    |
    v
WorkReportController
    |
    | identifica usuário
    | valida entrada
    v
ModerationService.createReport()
    |
    +----> CatalogService
    |      valida existência da obra
    |
    +----> ModerationReportRepository
           persiste denúncia OPEN
```

O módulo `moderation` mantém a responsabilidade pela denúncia.

O módulo `catalog` fornece informações sobre a obra.

A denúncia utiliza o identificador da obra, sem estabelecer uma associação JPA direta com `Work`.

### Exemplo: resolução com restrição

A resolução de uma denúncia com `WORK_RESTRICTED` envolve dois módulos de domínio.

```text
Administrador autenticado
    |
    v
AdminModerationReportController
    |
    | valida ROLE_ADMIN
    | obtém adminUserId
    v
ModerationService
    |
    | inicia transação
    | bloqueia denúncia
    | valida UNDER_REVIEW
    |
    +----> ModerationReport
    |      registra decisão
    |      altera para RESOLVED
    |
    +----> CatalogService
    |      restrictWork(workId)
    |          |
    |          v
    |      WorkRepository
    |          |
    |          | bloqueio pessimista
    |          v
    |      Work.restrictVisibility()
    |
    +----> ModerationAuditEntryRepository
           registra REPORT_RESOLVED
    |
    v
Commit ou rollback
```

A operação utiliza uma transação Spring.

A alteração da denúncia, a visibilidade da obra e a auditoria devem ser confirmadas ou revertidas conjuntamente no PostgreSQL.

Se a restrição da obra falhar, a operação não deve deixar uma denúncia resolvida sem a alteração correspondente.

### Exemplo: restauração administrativa

```text
Administrador autenticado
    |
    v
AdminModerationReportController
    |
    v
ModerationService.restoreWork()
    |
    | bloqueia denúncia
    | valida RESOLVED
    | valida WORK_RESTRICTED
    |
    +----> CatalogService
    |      restoreWork(workId)
    |          |
    |          v
    |      Work.restoreVisibility()
    |
    +----> ModerationAuditEntryRepository
           registra WORK_RESTORED
    |
    v
Commit ou rollback
```

A restauração não altera a decisão original da denúncia.

A operação modifica a visibilidade atual da obra e acrescenta um novo registro ao histórico de auditoria.

### Dependência SQL entre módulos

A descoberta pública utiliza consultas SQL nativas executadas por `InstallmentRepository`.

Essas consultas acessam tabelas de dois domínios:

| Tabela | Domínio | Utilização |
|--------|---------|------------|
| `installment` | `publication` | Estado de publicação e ordenação |
| `work` | `catalog` | Identificação, título e visibilidade |
| `work_genre` | `catalog` | Filtragem por gênero |

A consulta utiliza UUIDs para relacionar as tabelas.

Não existe associação JPA direta entre `Installment` e `Work`.

Entretanto, **a ausência de relacionamentos JPA entre módulos não significa ausência de acoplamento relacional**.

As consultas SQL conhecem nomes de tabelas e colunas pertencentes a `catalog`.

O Spring Modulith verifica dependências estruturais entre módulos Java, mas não valida automaticamente dependências SQL entre tabelas.

Esse acoplamento é aceito conscientemente no MVP porque:

- a aplicação utiliza um único PostgreSQL;
- a consulta precisa combinar publicação e visibilidade;
- os filtros precisam ser aplicados antes da paginação;
- a abordagem evita carregar todos os registros em memória;
- não exige infraestrutura de pesquisa adicional.

A decisão deve ser reavaliada caso a evolução dos módulos torne o acoplamento inadequado.

Alternativas futuras incluem modelos de leitura especializados ou mecanismos de projeção.

### Benefícios e limites da separação

A arquitetura mantém os seguintes benefícios:

- regras de domínio próximas de seus responsáveis;
- entidades e repositories internos encapsulados;
- detalhes de armazenamento fora dos contratos HTTP;
- ausência de associações JPA diretas entre módulos;
- verificação automatizada das fronteiras Java;
- dependências expressas por contratos públicos;
- coordenação transacional sem duplicar regras de domínio.

A descoberta pública constitui um caso específico de dependência SQL de leitura entre tabelas de módulos distintos.

Esse acoplamento deve permanecer documentado quando o schema evoluir.

## 5. Persistência

O Metopa utiliza PostgreSQL 18 como banco de dados relacional.

O acesso aos dados é realizado por Spring Data JPA e Hibernate, complementado por consultas SQL nativas quando necessário.

As entidades possuem identificadores UUID.

### 5.1. Responsabilidade do PostgreSQL

O PostgreSQL armazena:

- contas e papéis de acesso;
- obras e metadados;
- visibilidade das obras;
- gêneros;
- installments;
- metadados das páginas;
- referências das capas;
- entradas de biblioteca;
- progresso de leitura;
- denúncias;
- decisões administrativas;
- registros de auditoria.

O conteúdo binário das imagens não é armazenado diretamente no banco.

O PostgreSQL também executa as consultas de filtragem, contagem, ordenação e paginação da descoberta pública.

### 5.2. Relacionamentos entre módulos

Os relacionamentos que atravessam as fronteiras dos módulos Java são representados por identificadores e contratos.

Por exemplo, um installment mantém o UUID da obra à qual pertence, mas `publication` não manipula diretamente a entidade JPA `Work`.

Da mesma forma, uma denúncia mantém o UUID da obra denunciada, enquanto a validação da obra é delegada ao catálogo.

Essa estratégia preserva o encapsulamento dos módulos Java.

Evitar associações JPA entre módulos não impede que o PostgreSQL utilize foreign keys entre suas tabelas.

A partir da migration V10, o banco reforça os relacionamentos entre obras, installments, biblioteca e progresso.

### 5.3. Integridade dos dados

O PostgreSQL reforça regras de integridade.

Entre as restrições relevantes estão:

- unicidade de tipo e número dos installments dentro da obra;
- unicidade do número de página por installment;
- relacionamentos necessários à consistência;
- exclusão em cascata das páginas de installments removidos;
- impedimento da exclusão de obras com installments;
- exclusão em cascata de referências de biblioteca;
- exclusão em cascata de registros de progresso;
- exclusão em cascata dos gêneros;
- valores válidos para papéis de contas;
- valores válidos para visibilidade;
- valores válidos para decisões de moderação;
- valores válidos para ações de auditoria.

As restrições relacionadas à exclusão de obras são:

| Tabela | Referência | Comportamento |
|--------|------------|---------------|
| `installment` | `work_id → work.id` | `ON DELETE RESTRICT` |
| `library_entry` | `work_id → work.id` | `ON DELETE CASCADE` |
| `reading_progress` | `work_id → work.id` | `ON DELETE CASCADE` |
| `work_genre` | `work_id → work.id` | `ON DELETE CASCADE` |

A restrição de `work_genre` foi introduzida na V9.

As três primeiras foram adicionadas na V10.

A aplicação continua responsável por validar regras de domínio antes de persistir alterações.

As constraints do banco complementam essas validações.

### 5.4. Descoberta pública e consultas paginadas

A descoberta pública utiliza consultas SQL nativas executadas pelo `InstallmentRepository`.

Endpoint:

```http
GET /api/works
```

Filtros disponíveis:

```text
title
genre
page
size
```

#### Elegibilidade

Uma obra somente participa da descoberta quando:

- possui pelo menos um installment `PUBLISHED`;
- possui visibilidade `PUBLIC`.

Obras sem installments ou apenas com installments `DRAFT` não são retornadas.

Obras `RESTRICTED` também são excluídas.

Como uma obra pode possuir vários installments publicados, as consultas agrupam os resultados por `work_id`.

Assim, cada obra aparece uma única vez.

#### Paginação

A descoberta utiliza `Pageable` e retorna `Page<UUID>` na camada de repository.

Parâmetros HTTP:

| Parâmetro | Padrão | Restrição |
|-----------|--------|-----------|
| `page` | `0` | Maior ou igual a zero |
| `size` | `20` | Entre 1 e 50 |

A paginação ocorre no PostgreSQL após os filtros e a ordenação.

A contagem utiliza consultas específicas com `COUNT(DISTINCT i.work_id)`.

Isso evita contabilizar vários installments da mesma obra como resultados independentes.

A resposta contém:

```text
content
page
size
totalElements
totalPages
```

Uma página sem resultados possui `content` vazio.

#### Ordenação

As obras são ordenadas pela atualização mais recente entre seus installments publicados.

Expressão SQL:

```sql
ORDER BY MAX(i.updated_at) DESC, i.work_id ASC
```

O UUID fornece o desempate determinístico.

A ordenação não utiliza a data de atualização dos metadados da obra nem uma pontuação de relevância textual.

#### Busca parcial por título

O filtro `title` permite pesquisar qualquer trecho do título, sem diferenciar maiúsculas e minúsculas.

A implementação utiliza PostgreSQL `ILIKE`.

Exemplo:

```sql
w.title ILIKE :titlePattern ESCAPE '!'
```

O `PublicationService` prepara o valor antes de chamar o repository.

O processamento inclui:

1. tratar `null` ou texto em branco como ausência de filtro;
2. remover espaços das extremidades com `strip()`;
3. escapar `!`;
4. escapar `%` e `_`;
5. adicionar `%` às extremidades para correspondência parcial.

O caractere `!` é utilizado como escape explícito.

Assim, `%` e `_` fornecidos pelo usuário são tratados literalmente.

A implementação não oferece busca avançada, pontuação de relevância ou normalização específica de acentos.

O uso de `ILIKE` não garante equivalência entre termos acentuados e não acentuados.

#### Combinação dos filtros

O repository disponibiliza quatro caminhos principais:

| Filtros | Método |
|---------|--------|
| Nenhum | `findPublishedWorkIds` |
| Apenas gênero | `findPublishedWorkIdsByGenre` |
| Apenas título | `findPublishedWorkIdsByTitle` |
| Título e gênero | `findPublishedWorkIdsByTitleAndGenre` |

Quando título e gênero são informados, os critérios são combinados por `AND`.

A associação de gêneros é verificada por `EXISTS`, evitando duplicações.

Exemplo ilustrativo da consulta combinada com visibilidade:

```sql
SELECT i.work_id
FROM installment i
JOIN work w ON w.id = i.work_id
WHERE i.status = 'PUBLISHED'
  AND w.visibility = 'PUBLIC'
  AND w.title ILIKE :titlePattern ESCAPE '!'
  AND EXISTS (
      SELECT 1
      FROM work_genre wg
      WHERE wg.work_id = i.work_id
        AND wg.genre = :genre
  )
GROUP BY i.work_id
ORDER BY MAX(i.updated_at) DESC, i.work_id ASC
```

A consulta de contagem aplica os mesmos filtros e utiliza `COUNT(DISTINCT i.work_id)`.

A visibilidade precisa ser considerada tanto na seleção dos resultados quanto na contagem.

#### Recuperação dos metadados

Depois de receber a página de UUIDs, o serviço consulta `CatalogService.findWorkSummary()` para recuperar os metadados necessários.

Os dados são convertidos para `PublishedWorkSummary`, preservando a ordem e os metadados da página.

A estratégia limita a recuperação dos resumos às obras selecionadas, mas pode produzir consultas individuais ao catálogo.

Esse padrão apresenta potencial de consultas N+1.

O limite de `size` reduz a quantidade máxima de resumos por requisição, mas não elimina o custo.

O desempenho deverá ser observado conforme o volume aumentar.

#### Decisões para o MVP

A implementação utiliza o PostgreSQL existente.

Não foram introduzidos:

- Elasticsearch;
- infraestrutura externa de pesquisa;
- índices específicos para `ILIKE`;
- mecanismos adicionais de indexação.

Uma evolução possível é avaliar índices com `pg_trgm`, conforme medições reais de desempenho.

A solução atual prioriza simplicidade operacional, correção dos resultados e reaproveitamento da infraestrutura existente.

### 5.5. Persistência da moderação

A moderação utiliza entidades próprias.

`ModerationReport` representa as denúncias e seus estados.

`ModerationAuditEntry` representa os eventos administrativos.

Os registros são persistidos por repositories internos ao módulo.

As decisões de resolução utilizam `ModerationDecision`.

Os eventos de auditoria utilizam `ModerationAuditAction`.

A visibilidade atual permanece no módulo `catalog`, armazenada na entidade `Work`.

Essa separação distingue:

| Informação | Domínio responsável |
|------------|---------------------|
| Denúncia | `moderation` |
| Status da denúncia | `moderation` |
| Decisão administrativa | `moderation` |
| Histórico de auditoria | `moderation` |
| Visibilidade atual da obra | `catalog` |
| Estado de publicação | `publication` |

A restauração modifica a visibilidade, mas preserva a decisão da denúncia.

O banco mantém o histórico da moderação independentemente da disponibilidade pública atual da obra.

## 6. Migrations

O Flyway é responsável pelo versionamento do schema PostgreSQL.

Arquivos:

```text
src/main/resources/db/migration/
```

As migrations existentes até a conclusão da moderação mínima são:

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
V11__create_moderation_report.sql
V12__add_user_account_role.sql
V13__create_moderation_audit_entry.sql
V14__add_moderation_report_decision.sql
V15__extend_moderation_resolution_constraints.sql
V16__add_work_visibility.sql
V17__allow_work_restricted_moderation_decision.sql
V18__allow_work_restored_moderation_audit_action.sql
```

### Migration V10 — Integridade referencial

A V10 introduziu foreign keys entre obras e registros dependentes.

As principais regras são:

- `ON DELETE RESTRICT` para installments;
- `ON DELETE CASCADE` para biblioteca;
- `ON DELETE CASCADE` para progresso.

A associação de gêneros já utilizava `ON DELETE CASCADE` desde a V9.

Essas restrições impedem a exclusão de obras com installments e preservam a integridade das referências.

### Migrations V11–V15 — Moderação e papéis

As migrations V11–V15 introduziram a base relacional da moderação:

| Migration | Responsabilidade |
|-----------|------------------|
| V11 | Criação da estrutura de denúncias |
| V12 | Inclusão do papel de acesso nas contas |
| V13 | Criação da auditoria administrativa |
| V14 | Inclusão das decisões de moderação |
| V15 | Ampliação das constraints de resolução |

Essas alterações permitem persistir denúncias, estados, decisões e histórico de ações administrativas.

### Migration V16 — Visibilidade de obras

A V16 introduziu o estado de visibilidade na entidade persistida `Work`.

Valores:

```text
PUBLIC
RESTRICTED
```

A visibilidade é armazenada independentemente do estado de publicação dos installments.

A aplicação passa a considerar essa informação nas operações públicas de descoberta e leitura.

### Migration V17 — Decisão de restrição

A V17 ampliou a constraint das decisões de moderação para permitir:

```text
WORK_RESTRICTED
```

Essa decisão permite resolver uma denúncia e restringir a obra associada.

### Migration V18 — Auditoria de restauração

A V18 ampliou a constraint de auditoria para permitir:

```text
WORK_RESTORED
```

Esse evento registra a restauração administrativa de uma obra.

A ação não substitui a decisão original da denúncia.

### Política de evolução do schema

Migrations já aplicadas não devem ser editadas.

Mudanças posteriores devem utilizar novas migrations versionadas.

A criação de novas foreign keys ou constraints exige considerar a existência de dados legados incompatíveis.

As consultas nativas que dependem de tabelas de diferentes módulos também precisam ser consideradas durante a evolução do schema.

## 7. Modelo de publicação e visibilidade

### 7.1. Installments

Um installment pertence a uma obra e possui tipo, número, título opcional e estado de publicação.

A combinação de tipo e número deve ser única dentro da obra.

Ao ser criado, começa em `DRAFT`.

A foreign key introduzida na V10 protege contra a persistência de installments associados a obras inexistentes.

### 7.2. Páginas

As páginas pertencem a um installment.

Cada página possui:

- UUID;
- identificador do installment;
- número de leitura;
- referência interna de armazenamento;
- `Content-Type`.

Os arquivos físicos são armazenados pelo módulo `media`.

### 7.3. Estado DRAFT

Enquanto um installment está em `DRAFT`, o proprietário pode:

- atualizar metadados;
- adicionar páginas;
- excluir páginas;
- reordenar páginas;
- excluir o installment;
- publicar o conteúdo quando existir pelo menos uma página.

### 7.4. Estado PUBLISHED

Após a publicação:

- o installment possui status `PUBLISHED`;
- suas páginas podem ser disponibilizadas publicamente;
- metadados e páginas não podem ser modificados;
- o installment não pode ser excluído no MVP.

A disponibilidade pública também depende da visibilidade da obra.

Uma obra `RESTRICTED` pode possuir installments `PUBLISHED`, mas seu conteúdo não é disponibilizado publicamente.

### 7.5. Visão pública e visão do criador

O Metopa distingue duas perspectivas.

**Visão pública:**

- apresenta obras com conteúdo publicado;
- exige visibilidade `PUBLIC`;
- apresenta somente installments `PUBLISHED`;
- não expõe drafts;
- disponibiliza capas e páginas conforme as regras de acesso;
- permite descobrir obras por título e gênero;
- utiliza listagem paginada.

**Visão do criador:**

- exige autenticação;
- valida propriedade;
- apresenta obras ainda sem conteúdo publicado;
- permite consultar installments `DRAFT` e `PUBLISHED`;
- disponibiliza metadados e conteúdo binário para gerenciamento.

A remoção da capa não altera o estado de publicação dos installments.

Uma obra pública com conteúdo publicado permanece disponível após remover sua capa.

### 7.6. Exclusão de obras

O MVP permite excluir uma obra somente quando ela não possui installments.

A restrição vale independentemente do estado:

- sem installments: exclusão permitida;
- com `DRAFT`: exclusão rejeitada;
- com `PUBLISHED`: exclusão rejeitada.

A exclusão é coordenada por `CatalogService.deleteWork()`.

A existência de installments é consultada por `WorkDeletionGuard`.

Se houver installments, a aplicação retorna `409 Conflict`.

Quando a exclusão é permitida:

- o registro da obra é removido;
- os gêneros são removidos;
- referências de biblioteca são removidas;
- registros de progresso são removidos;
- a capa física é excluída após commit, quando existir.

A operação não é idempotente quanto à resposta HTTP.

Após uma exclusão bem-sucedida, uma nova tentativa retorna `404 Not Found`.

### 7.7. Restrição e restauração de obras

A visibilidade possui dois estados:

```text
PUBLIC
RESTRICTED
```

Fluxo:

```text
PUBLIC
   |
   | decisão WORK_RESTRICTED
   v
RESTRICTED
   |
   | ação WORK_RESTORED
   v
PUBLIC
```

A restrição não modifica o estado dos installments.

Uma obra restrita mantém seus registros e arquivos.

Entretanto, seu conteúdo deixa de estar disponível nos endpoints públicos.

A restauração devolve a visibilidade `PUBLIC`.

Se a obra possuir installments publicados, seu conteúdo poderá voltar a ser disponibilizado.

A restauração não modifica retroativamente a decisão administrativa original.

## 8. Concorrência e bloqueio pessimista

O Metopa utiliza bloqueios pessimistas no PostgreSQL para coordenar operações concorrentes sobre o mesmo installment, obra ou denúncia.

A estratégia utiliza `PESSIMISTIC_WRITE` por meio de consultas específicas dos repositories.

### Operações coordenadas

Em `publication`:

- criação de páginas;
- exclusão de páginas;
- reordenação;
- atualização de metadados;
- publicação;
- exclusão de installments.

Em `catalog`:

- atualização de obras;
- definição e substituição de capas;
- remoção de capas;
- exclusão de obras;
- criação de installments;
- restrição de visibilidade;
- restauração de visibilidade.

Em `moderation`:

- início de análise;
- resolução de denúncias;
- restrição de obras;
- restauração administrativa;
- coordenação da persistência de auditoria.

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
Reavalia estado persistido
```

O objetivo é impedir que operações incompatíveis modifiquem simultaneamente a mesma entidade sem observar o estado consolidado.

### 8.1. Reordenação concorrente com publicação

Uma transação reordena páginas enquanto outra tenta publicar o mesmo installment.

O bloqueio coordena as operações.

O resultado esperado é preservar a ordem confirmada das páginas e o estado final correto do installment.

### 8.2. Exclusão concorrente com publicação

A exclusão de installments utiliza bloqueio pessimista.

Quando a publicação confirma primeiro, a exclusão encontra o installment `PUBLISHED` e é rejeitada.

O conteúdo publicado permanece preservado.

### 8.3. Limites da estratégia para installments

O bloqueio coordena operações sobre a mesma linha do installment.

Ele não substitui constraints de unicidade ou integridade referencial.

Conflitos envolvendo registros diferentes continuam exigindo validações apropriadas.

### 8.4. Substituição concorrente de capas

O `WorkRepository` disponibiliza `findByIdForUpdate()` com `PESSIMISTIC_WRITE`.

`CatalogService.setCover()` adquire o bloqueio antes de verificar propriedade, identificar a capa anterior ou armazenar o novo arquivo.

Isso evita que duas substituições utilizem simultaneamente uma referência anterior desatualizada.

O bloqueio permanece associado à transação até commit ou rollback.

### 8.5. Concorrência entre metadados e capas

`CatalogService.updateWork()` também utiliza `findByIdForUpdate()`.

Como atualização de metadados e substituição de capa alteram a mesma entidade, ambas precisam observar o estado consolidado da transação anterior.

Essa coordenação reduz o risco de *lost updates*.

### 8.6. Concorrência entre remoção e substituição de capas

`CatalogService.removeCover()` utiliza o mesmo bloqueio da entidade `Work`.

Um cenário de integração valida:

1. uma transação inicia a remoção;
2. a referência é alterada, mas a transação permanece aberta;
3. outra transação tenta substituir a capa;
4. a substituição aguarda;
5. a remoção confirma e executa sua limpeza;
6. a substituição prossegue com o estado atualizado.

O estado final esperado preserva a referência e o arquivo da nova capa.

Esse comportamento foi validado em:

```text
WorkCoverIntegrationTests.shouldPreserveReplacementWhenCoverRemovalCommitsConcurrently()
```

### 8.7. Exclusão concorrente de obra e criação de installment

A exclusão de obras e a criação de installments envolvem dois módulos:

- `catalog`;
- `publication`.

Ambas utilizam bloqueio pessimista sobre `Work`.

**Exclusão confirma primeiro:**

1. a exclusão bloqueia a obra;
2. verifica que não existem installments;
3. remove a obra;
4. a criação concorrente aguarda;
5. a exclusão confirma;
6. a criação não encontra mais a obra;
7. nenhum installment órfão é persistido.

**Criação confirma primeiro:**

1. a criação bloqueia a obra;
2. persiste o installment;
3. a exclusão aguarda;
4. a criação confirma;
5. a exclusão identifica o installment;
6. a exclusão é rejeitada;
7. obra e installment permanecem preservados.

Esses cenários foram validados em `WorkDeletionIntegrationTests`.

A foreign key da V10 complementa a coordenação.

### 8.8. Concorrência na análise de denúncias

Uma denúncia somente pode iniciar análise enquanto estiver `OPEN`.

A transição utiliza bloqueio pessimista sobre o registro da denúncia.

Duas operações concorrentes não devem concluir validamente a mesma transição utilizando o estado anterior.

Após a primeira transação confirmar, a segunda precisa observar o estado atualizado.

Uma nova tentativa de iniciar análise em uma denúncia já `UNDER_REVIEW` é rejeitada.

A implementação possui testes de concorrência e consistência transacional para esse comportamento.

### 8.9. Resolução e restauração administrativas

A resolução exige uma denúncia `UNDER_REVIEW`.

A operação bloqueia a denúncia antes de aplicar a decisão.

Quando a decisão é `WORK_RESTRICTED`, o catálogo também bloqueia a obra antes de modificar sua visibilidade.

A restauração exige:

- denúncia `RESOLVED`;
- decisão `WORK_RESTRICTED`;
- obra atualmente `RESTRICTED`.

A alteração da visibilidade e o registro da auditoria participam da transação.

Uma restauração duplicada de obra já pública é rejeitada.

O rollback impede que uma operação rejeitada deixe novos registros de auditoria ou alterações relacionais parciais.

### 8.10. Limites da coordenação

Os bloqueios protegem os fluxos que utilizam a mesma estratégia de acesso.

Eles não tornam operações de filesystem transacionais.

Também não eliminam falhas de infraestrutura ou substituem constraints do PostgreSQL.

A coordenação pressupõe que outros fluxos de escrita respeitem os mecanismos definidos.

Testes concorrentes demonstram os cenários exercitados, mas não garantem ausência de todas as condições de corrida possíveis.

## 9. Armazenamento binário

O Metopa mantém os arquivos binários fora do PostgreSQL.

O banco persiste as referências necessárias para localizar o conteúdo.

A interface `BinaryStorage` abstrai o armazenamento físico.

### 9.1. Storage keys

A chave de armazenamento, denominada `storageKey`, é gerada internamente.

Ela não é fornecida diretamente pelo cliente nem exposta nos contratos públicos.

Os endpoints disponibilizam conteúdo através de identificadores de domínio, como `pageId` e `workId`.

### 9.2. Filesystem local

No MVP, os arquivos são armazenados em um diretório local configurado.

Isso permite executar o projeto sem contratar object storage externo.

A abstração `BinaryStorage` reduz o acoplamento entre regras de domínio e armazenamento.

### 9.3. Proteção de caminhos

As operações de leitura e exclusão validam os caminhos para impedir acesso fora da raiz configurada.

A exclusão de arquivos inexistentes é idempotente.

### 9.4. Limite transacional

O PostgreSQL oferece transações relacionais.

O filesystem não participa automaticamente dessas transações.

Portanto, operações que combinam alterações no banco e no armazenamento não possuem atomicidade distribuída.

O Metopa utiliza mecanismos compensatórios para reduzir inconsistências.

### 9.5. Visibilidade e armazenamento físico

A restrição administrativa de uma obra não representa a exclusão de seus arquivos.

Arquivos de capas e páginas permanecem armazenados quando uma obra recebe visibilidade `RESTRICTED`.

O bloqueio ocorre nas regras de acesso público.

Essa separação permite restaurar uma obra sem precisar reconstruir seu conteúdo binário.

Também significa que arquivos pertencentes a obras restritas continuam possuindo referências válidas no banco.

Eles não devem ser considerados órfãos apenas porque não estão publicamente acessíveis.

## 10. Consistência transacional entre PostgreSQL e filesystem

A implementação diferencia cinco situações principais:

1. exclusão de páginas e installments existentes;
2. upload de conteúdo novo;
3. substituição de capa;
4. remoção de capa;
5. exclusão de obra com capa.

Cada situação exige coordenação entre PostgreSQL e `BinaryStorage`.

### 10.1. Exclusão física após commit

Nas operações `deletePage()` e `deleteInstallment()`, os arquivos não são removidos imediatamente após a exclusão dos registros.

O `PublicationService` utiliza um helper compartilhado, `deleteFilesAfterCommit()`.

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
Registrar afterCommit()
    |
    +----------------------------+
    |                            |
    v                            v
 COMMIT                       ROLLBACK
    |                            |
    v                            v
Excluir arquivos             Preservar arquivos
```

A exclusão física acontece somente quando o banco confirma a remoção.

Se houver rollback, a limpeza não é executada.

### 10.2. Exclusão de installments

A exclusão de installments `DRAFT` utiliza o relacionamento `ON DELETE CASCADE`.

Fluxo:

1. localizar e bloquear o installment;
2. validar a propriedade;
3. verificar o estado `DRAFT`;
4. coletar as referências das páginas;
5. excluir o installment;
6. executar `flush()`;
7. registrar limpeza física após commit.

Os registros das páginas são removidos em cascata.

Os arquivos correspondentes são excluídos após a confirmação da transação.

Installments `PUBLISHED` não podem ser excluídos.

### 10.3. Compensação de uploads após rollback

Na criação de páginas, o arquivo é armazenado antes de persistir sua referência.

Se a transação falhar, o banco pode reverter o registro enquanto o arquivo permanece fisicamente armazenado.

Para reduzir esse risco, `createPage()` registra uma compensação após o armazenamento bem-sucedido.

O mecanismo utiliza `TransactionSynchronization.afterCompletion()`.

Quando a transação termina com `STATUS_ROLLED_BACK`, solicita a exclusão do arquivo recém-criado.

Quando ocorre commit, o arquivo permanece.

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
Registrar afterCompletion()
    |
    v
Persistir metadados
    |
    +----------------------------+
    |                            |
    v                            v
 COMMIT                       ROLLBACK
    |                            |
    v                            v
Preservar arquivo             Excluir arquivo novo
```

### 10.4. Consistência transacional de capas

O módulo `catalog` aplica compensações na criação e substituição de capas através de `CatalogService.setCover()`.

Depois de bloquear a obra e validar o proprietário, o método:

1. captura a referência da capa anterior;
2. armazena o novo arquivo;
3. registra callbacks transacionais;
4. atualiza a referência e o `Content-Type`;
5. deixa commit ou rollback sob responsabilidade da transação.

**Commit:**

- preserva o novo arquivo;
- confirma a referência;
- exclui a capa anterior após commit, quando aplicável.

**Rollback:**

- preserva a referência anterior;
- preserva o arquivo antigo;
- solicita a remoção do novo arquivo.

```text
Upload ou substituição de capa
    |
    v
Bloquear Work
    |
    v
Validar proprietário
    |
    v
Armazenar nova capa
    |
    v
Atualizar referência
    |
    +----------------------------+
    |                            |
    v                            v
 COMMIT                       ROLLBACK
    |                            |
    v                            v
Preservar capa nova           Preservar capa antiga
Excluir capa anterior         Excluir capa nova
```

A estratégia foi validada por testes de integração com PostgreSQL e filesystem local.

### 10.5. Remoção transacional de capas

`CatalogService.removeCover()` permite ao proprietário remover a capa.

Endpoint:

```http
DELETE /api/works/{workId}/cover
```

Fluxo:

1. localizar a obra com `findByIdForUpdate()`;
2. validar propriedade;
3. capturar a referência atual;
4. retornar normalmente caso não exista capa;
5. limpar `coverStorageKey` e `coverContentType`;
6. atualizar a entidade;
7. registrar `afterCommit()` para limpeza física;
8. confirmar ou reverter a transação.

O método `Work.clearCover()` modifica os atributos da capa e a data de atualização.

**Commit:**

- remove a referência;
- confirma a alteração;
- executa a limpeza física.

**Rollback:**

- preserva a referência;
- preserva o arquivo original;
- não executa a exclusão agendada.

#### Idempotência

A remoção é idempotente.

Quando a obra existe sem capa, a operação retorna normalmente.

Chamadas repetidas retornam `204 No Content`, desde que a obra exista e o usuário esteja autorizado.

A idempotência não elimina validações de propriedade.

#### Obra inexistente

Quando a obra não existe, o serviço lança `WorkNotFoundException`.

O `CatalogExceptionHandler` traduz a exceção para `404 Not Found`.

#### Limitação da limpeza após commit

Se a exclusão física falhar, a referência já poderá ter sido removida do PostgreSQL.

O arquivo anterior poderá permanecer armazenado sem referência.

Esse cenário exige um mecanismo posterior de reconciliação.

### 10.6. Exclusão transacional de obras

`CatalogService.deleteWork()` permite excluir uma obra sem installments.

Endpoint:

```http
DELETE /api/works/{workId}
```

Fluxo:

1. localizar e bloquear `Work`;
2. validar o proprietário;
3. consultar `WorkDeletionGuard.hasInstallments()`;
4. rejeitar se houver installments;
5. capturar `coverStorageKey`, quando existir;
6. remover a entidade;
7. registrar limpeza física após commit;
8. confirmar ou reverter a transação.

Quando existem installments, a operação lança `WorkHasInstallmentsException`.

O `CatalogExceptionHandler` retorna `409 Conflict`.

A obra, os installments, as páginas e a capa permanecem preservados.

Quando não existem installments, a exclusão pode prosseguir.

As associações e referências abrangidas por `ON DELETE CASCADE` são removidas pelo PostgreSQL.

#### Exclusão sem capa

Não é registrada limpeza física.

A ausência de capa não torna a exclusão idempotente quanto à resposta HTTP.

Uma segunda tentativa retorna `404 Not Found`.

#### Exclusão com capa

A referência é capturada antes da exclusão.

O arquivo não é removido imediatamente.

A limpeza é executada após commit.

**Commit:**

- remove o registro;
- remove referências dependentes conforme as constraints;
- solicita a exclusão física da capa.

**Rollback:**

- preserva a obra;
- preserva suas referências;
- preserva o arquivo.

```text
Exclusão de obra
    |
    v
Bloquear Work
    |
    v
Validar proprietário
    |
    v
Verificar installments
    |
    +----> Existem installments
    |      Rejeitar com 409
    |
    v
Remover Work no PostgreSQL
    |
    v
Registrar afterCommit() se houver capa
    |
    +----------------------------+
    |                            |
    v                            v
 COMMIT                       ROLLBACK
    |                            |
    v                            v
Obra removida                 Obra preservada
Referências removidas         Referências preservadas
Capa excluída após commit     Capa preservada
```

#### Concorrência com criação de installments

A criação e a exclusão utilizam o mesmo bloqueio pessimista da obra.

Isso impede que a exclusão conclua baseada em uma verificação de installments desatualizada.

Os cenários de concorrência foram validados por testes de integração.

#### Limitação da exclusão após commit

A limpeza física não faz parte da transação PostgreSQL.

Se `BinaryStorage.delete()` falhar, o registro poderá já estar excluído.

A exceção também poderá ser propagada ao fluxo da requisição sem desfazer o commit relacional.

O MVP ainda não possui uma fila persistente de limpeza ou mecanismo automático de reconciliação.

### 10.7. Comparação das estratégias

| Operação | Momento da limpeza | Objetivo |
|----------|--------------------|----------|
| Exclusão de página | Após commit | Preservar arquivo durante rollback |
| Exclusão de installment | Após commit | Preservar arquivos de páginas durante rollback |
| Upload de página | Após rollback | Remover arquivo sem registro persistido |
| Primeiro upload de capa | Após rollback | Remover arquivo se o registro falhar |
| Substituição de capa — commit | Após commit | Remover capa anterior |
| Substituição de capa — rollback | Após rollback | Remover capa nova |
| Remoção de capa — commit | Após commit | Excluir arquivo após remover referência |
| Remoção de capa — rollback | Nenhuma | Preservar referência e arquivo |
| Exclusão de obra sem capa | Nenhuma | Não tentar excluir arquivo inexistente |
| Exclusão de obra com capa — commit | Após commit | Remover capa depois da exclusão |
| Exclusão de obra com capa — rollback | Nenhuma | Preservar obra e capa |
| Restrição administrativa | Nenhuma | Bloquear acesso sem excluir conteúdo |
| Restauração administrativa | Nenhuma | Restaurar visibilidade sem reconstruir arquivos |

As estratégias são complementares.

O armazenamento permanece encapsulado por `BinaryStorage`.

A coordenação transacional pertence aos módulos de negócio.

### 10.8. Limitações conhecidas

Os callbacks não transformam PostgreSQL e filesystem em um sistema com transações distribuídas.

A solução atual não oferece garantia durável de limpeza em todos os cenários.

Situações que ainda podem produzir arquivos órfãos incluem:

- encerramento abrupto da JVM antes de um callback;
- falhas de acesso ao filesystem;
- interrupção após commit e antes da limpeza;
- resultado transacional incerto;
- falhas entre armazenamento físico e registro da compensação;
- falhas na exclusão de arquivos depois de remover referências;
- outros fluxos binários sem compensação equivalente.

Se `BinaryStorage.delete()` falhar em `afterCommit()`, a transação relacional já terá sido confirmada.

A limpeza poderá ficar pendente.

Os callbacks atuais não persistem tarefas de limpeza para repetição posterior.

### 10.9. Próxima evolução: reconciliação de arquivos órfãos

A próxima etapa planejada do backend MVP é a reconciliação de arquivos órfãos.

O objetivo é identificar arquivos presentes no armazenamento que não possuem referências válidas no PostgreSQL.

Uma solução deverá considerar:

- identificação segura de arquivos sem referência;
- preservação de arquivos ainda utilizados;
- distinção entre arquivos órfãos e arquivos apenas indisponíveis publicamente;
- prevenção de exclusões durante uploads ou transações em andamento;
- tratamento de falhas de limpeza;
- repetição segura das operações;
- testes com PostgreSQL e filesystem.

Arquivos pertencentes a obras `RESTRICTED` continuam sendo arquivos referenciados.

Eles não devem ser removidos pela reconciliação apenas em razão da visibilidade administrativa.

A implementação ainda precisa ser definida.

Não existe, neste estágio, um mecanismo automático de reconciliação em produção.

## 11. Segurança

O Metopa utiliza Spring Security com autenticação baseada em sessão HTTP.

O identificador da sessão é transmitido por cookie.

### 11.1. Senhas

As senhas são processadas utilizando `PasswordEncoder` e armazenadas como hash bcrypt.

Senhas não são persistidas em texto puro.

### 11.2. Proteção CSRF

A proteção CSRF permanece habilitada.

Operações protegidas que alteram estado exigem token válido.

Isso inclui:

- criação e atualização de obras;
- uploads e exclusões;
- publicação;
- modificações de biblioteca;
- atualização de progresso;
- registro de denúncias;
- início de análise administrativa;
- resolução de denúncias;
- restauração de obras.

Requisições autenticadas sem o token exigido recebem `403 Forbidden`.

### 11.3. Autorização por propriedade

As operações de gerenciamento do criador utilizam a identidade autenticada e a propriedade da obra.

O cliente não informa `ownerId`.

A aplicação verifica a propriedade antes de permitir alterações ou leituras administrativas.

Na remoção de capas, a propriedade é validada antes de modificar a entidade.

Na exclusão de obras, a propriedade é validada antes da consulta aos installments.

Usuários sem autorização não podem excluir ou modificar obras de terceiros.

### 11.4. Autorização por papéis

As contas possuem os papéis:

```text
USER
ADMIN
```

As autoridades correspondentes são utilizadas pelo Spring Security.

Os endpoints administrativos de moderação exigem:

```text
ROLE_ADMIN
```

O prefixo administrativo é:

```text
/api/admin/**
```

Usuários regulares não podem executar operações administrativas de moderação.

A autenticação por sessão fornece a identidade utilizada na auditoria.

O papel administrativo não substitui automaticamente as regras de propriedade aplicáveis aos endpoints do criador.

### 11.5. Ordem das validações

Na exclusão de installments, a propriedade é validada antes de verificar `DRAFT` ou `PUBLISHED`.

Isso evita revelar o estado de publicação a usuários não autorizados.

Na remoção de capas, a propriedade é validada antes de verificar a existência da capa.

Na exclusão de obras, a ordem é:

1. localizar e bloquear;
2. verificar propriedade;
3. consultar installments;
4. excluir somente quando permitido.

Na moderação, a autorização administrativa é aplicada antes da execução das operações de negócio.

Depois da autorização, o serviço valida a existência e o estado da denúncia.

Essa separação impede que usuários regulares executem transições administrativas.

### 11.6. Separação de conteúdo público

Os endpoints públicos apresentam somente conteúdo que atende às regras de publicação e visibilidade.

As condições principais são:

```text
Installment.status = PUBLISHED
Work.visibility = PUBLIC
```

Drafts e obras restritas são tratados como indisponíveis.

A descoberta pública não retorna obras `RESTRICTED`.

O conteúdo público de installments e páginas também respeita a visibilidade da obra.

A restrição não remove fisicamente o conteúdo.

A restauração permite que obras com conteúdo publicado voltem a ser disponibilizadas.

### 11.7. Respostas HTTP

O projeto utiliza códigos apropriados para autenticação, autorização e regras de negócio.

Entre eles:

```text
400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
```

A remoção de capas utiliza:

- `204` para remoção bem-sucedida;
- `204` quando a obra existe sem capa;
- `401` sem autenticação;
- `403` sem autorização ou CSRF;
- `404` quando a obra não existe.

A exclusão de obras utiliza:

- `204` quando a exclusão é permitida;
- `401` sem autenticação;
- `403` sem autorização;
- `404` para obra inexistente;
- `409` quando existem installments.

Os endpoints administrativos de moderação utilizam:

- `200` para consultas;
- `204` para transições concluídas;
- `400` para dados inválidos;
- `401` para ausência de autenticação;
- `403` para ausência de autoridade administrativa ou CSRF;
- `404` para recursos não encontrados;
- `409` para transições inválidas.

O projeto utiliza `ProblemDetail` em tratamentos específicos de exceções.

Para conflitos de transição da moderação, o controller utiliza o título:

```text
Invalid moderation transition
```

Os contratos detalhados estão em [API.md](API.md).

## 12. Testes de arquitetura e integração

A validação completa de **21/11/2026** executou **441 testes com sucesso**.

Comando:

```bash
./mvnw clean verify
```

Resultado:

```text
Tests run: 441
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A execução também concluiu o empacotamento do backend como JAR executável do Spring Boot.

O GitHub Actions confirmou a aprovação dos checks do Pull Request de moderação e da execução posterior ao merge na `main`.

### Cobertura arquitetural

A suíte valida:

- fronteiras entre módulos com Spring Modulith;
- regras de domínio;
- persistência real no PostgreSQL;
- autenticação;
- autorização por propriedade;
- autorização por papéis;
- proteção CSRF;
- contratos Spring MVC;
- armazenamento e leitura binária;
- exclusões transacionais;
- compensação de uploads em rollback;
- substituição e remoção de capas;
- concorrência com transações independentes;
- bloqueios pessimistas;
- integridade referencial;
- descoberta pública;
- busca por título e gênero;
- paginação e ordenação;
- moderação administrativa;
- restrição e restauração de obras;
- auditoria;
- rollback das transições administrativas;
- proteção do conteúdo de obras restritas.

### Testes de capas

As operações de capas possuem cobertura para:

1. remoção após commit;
2. preservação após rollback;
3. remoção idempotente;
4. autorização por propriedade;
5. ausência de autenticação;
6. ausência de CSRF;
7. obra inexistente;
8. concorrência entre remoção e substituição;
9. exclusão física após commit;
10. ausência de limpeza física em rollback.

Os testes utilizam PostgreSQL e filesystem local quando necessário.

### Testes de exclusão de obras

`WorkDeletionIntegrationTests` valida cenários como:

1. exclusão de obra sem installments;
2. rejeição quando existe `DRAFT`;
3. rejeição quando existe `PUBLISHED`;
4. remoção da capa após commit;
5. preservação após rollback;
6. autorização por propriedade;
7. autenticação e CSRF;
8. obra inexistente;
9. concorrência entre exclusão e criação de installment;
10. integridade referencial;
11. remoção de referências dependentes.

Os cenários de concorrência utilizam transações independentes.

### Testes da descoberta pública

A descoberta possui cobertura em diferentes camadas.

**Repository — `InstallmentRepositoryTests`**

Valida:

- paginação de obras distintas;
- filtragem por gênero;
- busca por título;
- correspondência case-insensitive;
- combinação de filtros;
- ordenação;
- contagem;
- caracteres especiais;
- exclusão de obras restritas.

**Service — `PublicationServiceTests`**

Valida:

- seleção da consulta correta;
- normalização de títulos;
- escape de `%`, `_` e `!`;
- conversão para resumos públicos;
- metadados de paginação;
- páginas vazias;
- regras de visibilidade.

**HTTP — `PublishedWorkControllerTests`**

Valida:

- recebimento dos parâmetros;
- encaminhamento dos filtros;
- paginação;
- formato da resposta;
- validação HTTP.

**Integração — `PublishedWorkDiscoveryIntegrationTests`**

Valida:

- publicação e descoberta;
- exclusão de drafts;
- prevenção de duplicados;
- filtros de título e gênero;
- paginação e ordenação;
- contagem;
- caracteres especiais;
- ocultação de obras restritas;
- restauração de acesso público.

A integração também valida o ciclo de acesso público:

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

### Testes da moderação

A implementação possui testes unitários, de persistência, HTTP e integração.

**Domínio — `ModerationReportTests`**

Valida estados, decisões e transições.

**Service — `ModerationServiceTests`**

Valida operações de criação e regras de negócio.

**Análise — `ModerationServiceStartReviewTests`**

Valida início de análise e rejeição de transições inválidas.

**Resolução — `ModerationServiceResolveTests`**

Valida decisões administrativas e regras de resolução.

**Persistência — `ModerationReportRepositoryTests`**

Valida operações relacionadas à persistência das denúncias.

**Auditoria — `ModerationAuditEntryTests` e `ModerationAuditEntryRepositoryTests`**

Valida o modelo e a persistência de eventos administrativos.

**HTTP — `WorkReportControllerTests`**

Valida o contrato de registro de denúncias.

**Administração — testes dos controllers de moderação**

Validam autenticação, autorização, parâmetros, operações administrativas e respostas HTTP.

**Integração — `AdminModerationResolveIntegrationTests`**

Valida operações administrativas utilizando autenticação e persistência reais.

Inclui restrição, restauração, transições inválidas, rejeição de operações duplicadas e preservação da auditoria.

**Transações — `ModerationResolveTransactionIntegrationTests`**

Valida a consistência das operações de resolução.

**Concorrência — `ModerationStartReviewConcurrencyIntegrationTests`**

Valida transições concorrentes de análise.

**Rollback — `ModerationStartReviewTransactionIntegrationTests`**

Valida o comportamento transacional do início de análise.

### Integração contínua do backend

O projeto possui um workflow GitHub Actions.

O ambiente utiliza:

- Java 25;
- PostgreSQL 18.6;
- Maven Wrapper;
- execução da suíte;
- empacotamento da aplicação.

Comando:

```bash
./mvnw -B -ntp clean verify
```

O CI utiliza um serviço PostgreSQL próprio, sem depender do Docker Compose local.

O workflow valida Pull Requests direcionados à `main` e alterações integradas à branch principal.

A execução local continua recomendada antes de abrir Pull Requests.

### Limites da validação

Os testes aumentam a confiança na implementação, mas não garantem ausência de todos os defeitos possíveis.

Em especial:

- testes de concorrência exercitam cenários definidos;
- callbacks transacionais não eliminam todas as falhas de filesystem;
- a cobertura de moderação não substitui políticas futuras de operação;
- o sucesso do CI não significa que mecanismos ainda planejados estejam implementados.

A estratégia completa está documentada em [TESTING.md](TESTING.md).

## 13. Princípios de evolução

A evolução do Metopa segue algumas diretrizes:

- preservar os limites entre módulos;
- manter regras de negócio próximas de seus domínios;
- evitar dependências JPA diretas entre módulos;
- utilizar APIs públicas para comunicação interna;
- encapsular detalhes de armazenamento;
- reforçar integridade com constraints PostgreSQL;
- proteger operações concorrentes quando necessário;
- utilizar transações para alterações relacionadas;
- preservar histórico de auditoria;
- não confundir visibilidade com exclusão de conteúdo;
- introduzir abstrações conforme necessidades concretas;
- desenvolver incrementos pequenos;
- validar alterações com testes automatizados;
- registrar decisões e limitações arquiteturais;
- priorizar paginação no banco;
- documentar acoplamentos SQL;
- avaliar otimizações com medições;
- utilizar CI na integração das alterações.

### Estado atual do backend MVP

As seguintes etapas do roadmap foram concluídas:

- [x] Paginação da descoberta pública.
- [x] Busca parcial por título.
- [x] Combinação dos filtros de título e gênero.
- [x] Moderação mínima.
- [x] Autorização administrativa por papéis.
- [x] Restrição e restauração de obras.
- [x] Auditoria administrativa.

A próxima etapa planejada é:

- [ ] Reconciliação de arquivos órfãos.

### Evoluções futuras possíveis

Outras necessidades poderão ser avaliadas posteriormente:

- armazenamento binário fora do filesystem local;
- mecanismos duráveis de limpeza;
- observabilidade adicional;
- revisão das estratégias de consistência;
- otimização da busca por título com `pg_trgm`;
- revisão do acoplamento SQL entre módulos;
- redução de consultas individuais na recuperação de resumos;
- estratégias adicionais de pesquisa;
- evolução dos recursos de moderação;
- implementação da interface do MVP.

Essas possibilidades não constituem funcionalidades já implementadas.

A adoção de novas tecnologias deverá ser orientada por necessidades concretas, medições e evidências.

## Documentação relacionada

- [README — Visão geral do Metopa](../README.md)
- [API HTTP](API.md)
- [Estratégia de testes](TESTING.md)
