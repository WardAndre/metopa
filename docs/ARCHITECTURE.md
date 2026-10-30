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

Microsserviços acrescentariam preocupações como comunicação distribuída, observabilidade entre serviços, deployment individual, consistência eventual e infraestrutura adicional.

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
- coordenação de substituições, remoções de capas e atualizações de metadados por bloqueio pessimista;
- exposição de informações da obra para outros módulos através de APIs internas.

A propriedade de uma obra pertence ao domínio `catalog`.

Outros módulos não devem modificar diretamente suas entidades ou acessar livremente seus repositories.

Quando o módulo `publication` precisa validar a propriedade de uma obra, utiliza uma API disponibilizada por `catalog`.

Um exemplo dessa integração é a utilização de `CatalogService.findWork()`, que fornece uma referência da obra contendo as informações necessárias à operação.

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
- coordenação das operações transacionais com arquivos binários.

O módulo é responsável pelas regras que determinam quando uma página ou um installment pode ser modificado.

Operações sobre conteúdos publicados não podem alterar seus metadados ou páginas no MVP.

### 3.4. Library

Responsável pela biblioteca pessoal dos usuários.

Principais funcionalidades:

- inclusão de obras na biblioteca;
- prevenção de entradas duplicadas;
- listagem das obras adicionadas;
- isolamento por usuário;
- remoção idempotente de obras;
- restrição das operações a obras com conteúdo publicado.

A biblioteca não é responsável pela publicação do conteúdo.

Sua responsabilidade é registrar a relação entre um usuário e as obras que ele adicionou à biblioteca.

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

Essas regras pertencem aos módulos de domínio.

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

### Benefícios dessa separação

- regras de domínio permanecem próximas de seus responsáveis;
- detalhes de armazenamento não contaminam os contratos HTTP;
- alterações no filesystem não exigem modificar as regras de publicação;
- relações entre módulos não dependem de associações JPA;
- as fronteiras arquiteturais podem ser verificadas automaticamente.

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

### 5.3. Integridade dos dados

O PostgreSQL é utilizado para reforçar regras de integridade.

Entre as restrições relevantes estão:

- unicidade de combinações de tipo e número dos installments dentro da mesma obra;
- unicidade do número de página dentro de um installment;
- relacionamentos necessários para manter a consistência dos registros;
- exclusão em cascata dos registros das páginas quando um installment é removido.

A aplicação também valida regras de domínio antes de persistir alterações.

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
```

A migration `V9__create_work_genre.sql` introduziu a persistência de múltiplos gêneros associados a uma obra.

As operações de exclusão de installments, reordenação de páginas, compensação transacional de uploads, substituição e remoção de capas, bem como o bloqueio pessimista das escritas sobre obras, foram implementadas sem necessidade de novas migrations.

A remoção de capas reutiliza os campos existentes `cover_storage_key` e `cover_content_type`, definidos como opcionais no modelo de persistência.

### Política de evolução do schema

Migrations já aplicadas não devem ser editadas.

Mudanças posteriores devem utilizar novas migrations versionadas, preservando o histórico de evolução do banco.

## 7. Modelo de publicação

### 7.1. Installments

Um installment pertence a uma obra e possui tipo, número, título opcional e estado de publicação.

A combinação de tipo e número deve ser única dentro da obra.

Ao ser criado, um installment começa no estado `DRAFT`.

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
- remoção de capas.

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

### 8.7. Limites da coordenação

O bloqueio protege os fluxos que utilizam a mesma estratégia de acesso à linha.

Ele não torna operações de filesystem transacionais, não elimina falhas de infraestrutura e não substitui as restrições relacionais do banco.

A coordenação também pressupõe que outros fluxos que modifiquem a mesma entidade respeitem os mecanismos de bloqueio definidos.

Testes concorrentes baseados em sincronização de threads e janelas de tempo oferecem evidência de comportamento sob os cenários exercitados, mas não demonstram ausência de todas as condições de corrida possíveis.

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

A implementação atual diferencia quatro situações:

1. exclusão de páginas e installments existentes;
2. upload de conteúdo que está sendo criado;
3. substituição de uma capa existente por um novo arquivo;
4. remoção explícita de uma capa.

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

### 10.6. Comparação das estratégias

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
| Remoção de obra sem capa | Nenhuma limpeza física | Manter a operação idempotente, sem arquivos a remover |

As estratégias são complementares.

O armazenamento físico permanece encapsulado por `BinaryStorage`.

A coordenação transacional pertence aos fluxos de negócio implementados nos módulos `publication` e `catalog`.

### 10.7. Limitações conhecidas

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

Se `binaryStorage.delete()` falhar durante `afterCommit()`, a transação relacional já terá sido confirmada. A limpeza poderá ficar pendente e a exceção poderá ser propagada, sem desfazer o commit.

Os callbacks atuais não persistem tarefas de limpeza para repetição após falha ou reinicialização da aplicação.

### 10.8. Possíveis evoluções

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

Uma requisição autenticada sem o token exigido recebe `403 Forbidden`.

### 11.3. Autorização por propriedade

A autorização administrativa utiliza a identidade do usuário autenticado e a propriedade da obra.

O cliente não informa diretamente o `ownerId` nas operações de gerenciamento.

A propriedade é verificada pela aplicação antes de permitir alterações ou leituras administrativas de conteúdo.

Na remoção de capas, a validação de propriedade acontece antes de modificar a entidade ou registrar a limpeza física.

Tentativas de remoção por usuários que não são proprietários recebem `403 Forbidden`, preservando a referência e o arquivo da capa.

### 11.4. Ordem das validações

Na exclusão de installments, a validação de propriedade ocorre antes da verificação do estado `DRAFT` ou `PUBLISHED`.

Isso evita revelar o estado de publicação para um usuário que não possui autorização para gerenciar a obra.

Na remoção de capas, a propriedade também é validada antes de verificar se existe uma capa a remover.

Assim, a idempotência da operação não permite que um usuário sem autorização modifique ou consulte indevidamente o estado administrativo da obra.

### 11.5. Separação de conteúdo público

Os endpoints públicos apresentam somente conteúdo que atende às regras de publicação.

Drafts são tratados como indisponíveis nessas operações.

As consultas administrativas possuem contratos específicos e exigem autorização.

A remoção da capa não modifica os estados `DRAFT` ou `PUBLISHED` dos installments.

Uma obra publicada continua disponível publicamente após a remoção de sua capa, mas o endpoint público da capa deixa de disponibilizar conteúdo até que uma nova seja definida.

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

O tratamento de `WorkNotFoundException` é realizado em `CatalogExceptionHandler`, com resposta estruturada através de `ProblemDetail`.

Os contratos específicos de cada endpoint são descritos em [API.md](API.md).

## 12. Testes de arquitetura e integração

Na validação completa de 29/10/2026, a suíte automatizada executou **258 testes com sucesso**, sem falhas, erros ou testes ignorados.

Comando utilizado:

```bash
./mvnw clean test
```

Resultado:

```text
Tests run: 258
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A referência anterior era de 248 testes aprovados após a implementação da consistência transacional de capas.

A feature de remoção de capas acrescentou dez testes à suíte.

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
- idempotência na remoção de obras sem capa;
- tratamento de obras inexistentes;
- concorrência com transações independentes;
- bloqueios pessimistas para installments e obras;
- substituições concorrentes de capas;
- concorrência entre atualização de metadados e substituição de capa;
- concorrência entre remoção e substituição de capas;
- preservação de registros e arquivos após rollback;
- validação unitária dos callbacks transacionais.

### Testes específicos da remoção de capas

A feature `creator-cover-removal` introduziu testes de integração e testes unitários para validar:

1. remoção de capa existente após commit;
2. preservação da capa quando a remoção sofre rollback;
3. remoção idempotente de obra sem capa;
4. rejeição de remoção por usuário que não é proprietário;
5. rejeição de remoção sem autenticação;
6. rejeição de remoção sem token CSRF;
7. resposta `404 Not Found` para obra inexistente;
8. concorrência entre remoção e substituição de capas;
9. execução da exclusão física após commit em teste unitário;
10. ausência de exclusão física após rollback em teste unitário.

Os testes de integração exercitam transações reais, persistência no PostgreSQL e armazenamento no filesystem local.

Os testes unitários complementam essa validação, verificando as interações com `WorkRepository`, `BinaryStorage` e os callbacks transacionais.

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
