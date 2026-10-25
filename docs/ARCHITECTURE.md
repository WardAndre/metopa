
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
- definição, substituição e leitura de capas;
- exposição de informações da obra para outros módulos através de APIs internas.

A propriedade de uma obra pertence ao domínio `catalog`.

Outros módulos não devem modificar diretamente suas entidades ou acessar livremente seus repositories.

Quando o módulo `publication` precisa validar a propriedade de uma obra, utiliza uma API disponibilizada por `catalog`.

Um exemplo dessa integração é a utilização de `CatalogService.findWork()`, que fornece uma referência da obra contendo as informações necessárias à operação.

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

As operações de exclusão de installments, reordenação de páginas e compensação transacional de uploads foram implementadas sem necessidade de novas migrations.

### Política de evolução do schema

Migrations já aplicadas não devem ser editadas.

Mudanças posteriores devem utilizar novas migrations versionadas, preservando o histórico de evolução do banco.

## 7. Modelo de publicação

### 7.1. Installments

Um installment pertence a uma obra e possui tipo, número, título opcional e estado de publicação.

A combinação de tipo e número deve ser única dentro da mesma obra.

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

Essa separação faz parte das regras de acesso do domínio e não depende apenas da interface utilizada pelo cliente.

## 8. Concorrência e bloqueio pessimista

O Metopa utiliza bloqueios pessimistas no PostgreSQL para coordenar operações que modificam o mesmo installment.

A estratégia utiliza `PESSIMISTIC_WRITE`.

O repository disponibiliza a consulta `findByIdForUpdate()`, utilizada nas operações que precisam bloquear a linha correspondente.

### Operações coordenadas

A estratégia se aplica às operações de escrita sobre o mesmo installment, incluindo:

- criação de páginas;
- exclusão de páginas;
- reordenação de páginas;
- atualização de metadados;
- publicação;
- exclusão do installment.

### Funcionamento

```text
Transação A
    |
    v
Obtém bloqueio do installment
    |
    v
Valida e modifica os dados
    |
    | Transação B tenta modificar
    | o mesmo installment
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

A coordenação evita que duas operações incompatíveis alterem simultaneamente o mesmo installment sem observar o estado consolidado da transação anterior.

### 8.1. Reordenação concorrente com publicação

A estratégia foi validada com duas transações independentes no PostgreSQL.

Uma transação reordena as páginas enquanto outra tenta publicar o mesmo installment.

O bloqueio mantém a coordenação entre as operações.

O resultado esperado é preservar a ordem confirmada das páginas e o estado final correto do installment.

### 8.2. Exclusão concorrente com publicação

A exclusão de installments também utiliza o bloqueio pessimista.

Quando a publicação é confirmada antes de a exclusão prosseguir, a operação de exclusão encontra o installment em `PUBLISHED` e é rejeitada.

O conteúdo publicado e seus arquivos permanecem preservados.

### 8.3. Limites da estratégia

O bloqueio pessimista coordena operações sobre a mesma linha do installment.

Ele não substitui restrições de unicidade e integridade do PostgreSQL.

Conflitos envolvendo registros diferentes continuam exigindo validações apropriadas e restrições no banco.

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

A implementação atual diferencia duas situações:

1. exclusão de conteúdo que já existe;
2. upload de conteúdo que está sendo criado.

Cada situação possui uma estratégia própria.

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

### 10.4. Comparação das estratégias

| Operação | Momento da limpeza | Objetivo |
|----------|--------------------|----------|
| Exclusão de página | Após commit | Não remover arquivo de registro que pode ser restaurado |
| Exclusão de installment | Após commit | Não remover arquivos de páginas cuja exclusão pode ser revertida |
| Upload de página | Após rollback | Não manter arquivo sem registro persistido |

As estratégias são complementares.

O armazenamento físico permanece encapsulado por `BinaryStorage`.

A coordenação transacional pertence ao fluxo de negócio implementado em `publication`.

### 10.5. Limitações conhecidas

Os callbacks transacionais não transformam PostgreSQL e filesystem em um sistema com transações distribuídas.

A solução atual não oferece garantia durável de limpeza em todos os cenários.

Situações que ainda podem produzir arquivos órfãos incluem:

- encerramento abrupto da JVM antes da execução de um callback;
- falhas de acesso ao filesystem durante a exclusão;
- interrupção da aplicação após o commit e antes de completar a limpeza física;
- resultado transacional incerto;
- falhas entre o armazenamento físico e o registro da compensação;
- outros fluxos de armazenamento binário que ainda não utilizem a mesma compensação.

O callback de rollback implementado em `createPage()` atua especificamente sobre uploads de páginas.

Não se deve assumir que outros fluxos, como substituição de capas, possuem automaticamente a mesma proteção.

### 10.6. Possíveis evoluções

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

### 11.3. Autorização por propriedade

A autorização administrativa utiliza a identidade do usuário autenticado e a propriedade da obra.

O cliente não informa diretamente o `ownerId` nas operações de gerenciamento.

A propriedade é verificada pela aplicação antes de permitir alterações ou leituras administrativas de conteúdo.

### 11.4. Ordem das validações

Na exclusão de installments, a validação de propriedade ocorre antes da verificação do estado `DRAFT` ou `PUBLISHED`.

Isso evita revelar o estado de publicação para um usuário que não possui autorização para gerenciar a obra.

### 11.5. Separação de conteúdo público

Os endpoints públicos apresentam somente conteúdo que atende às regras de publicação.

Drafts são tratados como indisponíveis nessas operações.

As consultas administrativas possuem contratos específicos e exigem autorização.

### 11.6. Respostas HTTP

O projeto utiliza respostas HTTP adequadas para representar falhas de autenticação, autorização e regras de negócio.

Entre os códigos utilizados estão:

- `401 Unauthorized`;
- `403 Forbidden`;
- `404 Not Found`;
- `409 Conflict`.

Os contratos específicos de cada endpoint são descritos em [API.md](API.md).

## 12. Testes de arquitetura e integração

A suíte automatizada possui **242 testes aprovados** na referência atual.

Entre os aspectos relacionados à arquitetura estão:

- validação das fronteiras entre módulos com Spring Modulith;
- regras de domínio;
- persistência real no PostgreSQL;
- autenticação e autorização;
- proteção CSRF;
- integração com Spring Web MVC;
- armazenamento e leitura de arquivos;
- exclusões transacionais;
- compensação de upload em rollback;
- concorrência com transações independentes;
- bloqueios pessimistas;
- preservação de registros e arquivos após rollback.

O teste de modularidade ajuda a identificar dependências indevidas entre módulos.

Os testes de integração exercitam o comportamento real da aplicação com PostgreSQL e, quando aplicável, filesystem local.

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
