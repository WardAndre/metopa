# Metopa

Metopa é uma plataforma para publicação, descoberta e leitura de quadrinhos, mangás, graphic novels, webtoons e outros formatos de narrativa visual.

O projeto busca oferecer um ambiente onde criadores possam publicar e gerenciar suas obras, enquanto leitores podem descobrir conteúdos, acompanhar suas leituras e organizar uma biblioteca pessoal.

## Status

**Em desenvolvimento — MVP**

A primeira fase do Metopa utiliza infraestrutura local, sem dependência de serviços cloud pagos.

O backend possui atualmente **441 testes automatizados aprovados na última validação local**, realizada em 21 de novembro de 2026 com `./mvnw clean verify`.

A suíte cobre regras de domínio, persistência, autenticação, autorização, APIs HTTP, armazenamento de arquivos, transações, concorrência, descoberta pública e moderação administrativa.

O projeto possui integração contínua com GitHub Actions para validar Pull Requests e alterações integradas à branch `main`.

## Tecnologias

### Backend

- Java 25
- Spring Boot 4.1.1
- Spring Modulith
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security
- Bean Validation

### Persistência e infraestrutura

- PostgreSQL 18
- Flyway
- Docker Compose
- Armazenamento de arquivos em filesystem local

### Desenvolvimento e testes

- Maven Wrapper
- JUnit
- Mockito
- Spring Test
- Testes unitários e de integração
- GitHub Actions (CI)

## Arquitetura

O Metopa utiliza uma arquitetura de **monólito modular**, implementada com Spring Modulith.

Essa abordagem permite desenvolver o MVP como uma única aplicação, mantendo responsabilidades, dependências e limites explícitos entre os módulos.

Os módulos atuais são:

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

| Módulo | Responsabilidade |
|--------|------------------|
| `identity` | Contas de usuário, autenticação, sessões e papéis de acesso |
| `catalog` | Obras, metadados, gêneros, capas e controle de visibilidade |
| `publication` | Installments, páginas, publicação, leitura de conteúdo e descoberta pública |
| `library` | Biblioteca pessoal dos leitores |
| `reading` | Registro e consulta do progresso de leitura |
| `media` | Abstração para armazenamento de arquivos binários |
| `moderation` | Denúncias, análise administrativa, decisões, restrições, restaurações e auditoria |

O Spring Modulith é utilizado para verificar os limites e as dependências entre os módulos.

Os dados estruturados são persistidos no PostgreSQL. Arquivos binários, como páginas e capas, são armazenados separadamente através da abstração `BinaryStorage`.

O identificador interno de armazenamento (`storageKey`) não é exposto pelos endpoints públicos.

A descoberta pública utiliza consultas paginadas no PostgreSQL, com filtros aplicados antes da paginação. Os resultados são ordenados pela atualização mais recente de um installment publicado.

O controle de visibilidade das obras é responsabilidade do módulo `catalog`. Os módulos que disponibilizam conteúdo público consultam essa informação para impedir a exposição de obras restritas.

A moderação coordena operações administrativas entre os módulos, preservando suas responsabilidades e utilizando transações para manter a consistência dos dados.

Para informações detalhadas, consulte [Arquitetura](docs/ARCHITECTURE.md).

## Funcionalidades implementadas

### Contas e autenticação

- Criação de contas com validação e normalização dos dados.
- Armazenamento seguro de senhas com bcrypt.
- Autenticação baseada em sessão HTTP.
- Proteção CSRF para operações que alteram estado.
- Login, logout e consulta do usuário autenticado.
- Papéis de acesso `USER` e `ADMIN`.
- Autorização administrativa baseada em `ROLE_ADMIN`.

### Catálogo de obras

- Criação e atualização de obras pelo proprietário.
- Diferentes formatos de publicação.
- Configuração da direção de leitura e modo de apresentação.
- Associação de múltiplos gêneros.
- Upload, substituição, remoção e leitura de capas.
- Consulta e gerenciamento das obras do criador.
- Exclusão de obras sem installments.
- Descoberta pública de obras com conteúdo publicado.
- Filtragem da descoberta pública por gênero.
- Busca parcial de obras por título, sem diferenciar maiúsculas e minúsculas.
- Combinação dos filtros de título e gênero.
- Paginação da descoberta pública com tamanho configurável.
- Ordenação por atualização mais recente de installment publicado.
- Exclusão de obras sem conteúdo publicado e prevenção de resultados duplicados.
- Controle de visibilidade com os estados `PUBLIC` e `RESTRICTED`.
- Restrição e restauração de visibilidade através de operações administrativas.
- Exclusão de obras restritas dos resultados de descoberta pública.

### Publicação de conteúdo

- Criação e gerenciamento de installments.
- Tipos: `CHAPTER`, `ISSUE`, `EPISODE` e `STRIP`.
- Estados `DRAFT` e `PUBLISHED`.
- Atualização e exclusão de installments em `DRAFT`.
- Upload e gerenciamento de páginas.
- Reordenação de páginas em installments não publicados.
- Exclusão de páginas em installments `DRAFT`.
- Publicação de installments que possuam pelo menos uma página.
- Leitura pública de installments publicados.
- Acesso administrativo a drafts pelo proprietário.
- Bloqueio de alterações em installments publicados.
- Bloqueio de acesso público a obras, capas, installments e páginas de obras `RESTRICTED`.
- Restauração do acesso público quando a visibilidade da obra retorna para `PUBLIC`.

### Biblioteca e leitura

- Adição e remoção de obras da biblioteca pessoal.
- Prevenção de entradas duplicadas.
- Listagem da biblioteca do usuário autenticado.
- Registro e atualização do progresso de leitura.
- Consulta da última posição registrada por obra.
- Restrição do progresso a páginas publicadas.
- Bloqueio de criação e atualização do progresso de leitura para obras `RESTRICTED`.
- Preservação do progresso de leitura existente quando uma obra é restringida.

### Moderação administrativa

- Registro de denúncias relacionadas a obras.
- Consulta administrativa de denúncias com paginação e filtragem por status.
- Fluxo de análise: `OPEN` → `UNDER_REVIEW` → `RESOLVED`.
- Resolução de denúncias com as decisões `NO_VIOLATION` e `WORK_RESTRICTED`.
- Restrição administrativa de obras com violações confirmadas.
- Bloqueio da descoberta e leitura pública de obras restritas.
- Restauração administrativa da visibilidade de obras restritas.
- Preservação da decisão original após a restauração.
- Registro de auditoria com identificação do administrador, justificativa e data da operação.
- Eventos de auditoria `REVIEW_STARTED`, `REPORT_RESOLVED` e `WORK_RESTORED`.
- Autorização exclusiva de administradores para análise, resolução e restauração.
- Validação das transições de estado e rejeição de operações inválidas.
- Bloqueio pessimista e transações para coordenar alterações concorrentes.
- Rollback de operações que não possam ser concluídas integralmente.

A restauração administrativa não elimina o histórico da denúncia. Uma denúncia resolvida com `WORK_RESTRICTED` permanece com essa decisão, enquanto a restauração é registrada como uma nova ação de auditoria.

### Persistência e consistência

- Migrations versionadas com Flyway.
- Restrições de integridade no PostgreSQL.
- Bloqueio pessimista para coordenar operações concorrentes.
- Exclusão física de arquivos somente após commit nas operações de remoção.
- Compensação de uploads de páginas em caso de rollback.
- Preservação de registros e arquivos em transações revertidas.
- Consultas SQL paginadas para descoberta pública, com filtros e contagem de resultados no banco de dados.
- Persistência transacional das decisões de moderação e alterações de visibilidade.
- Auditoria persistente das ações administrativas.
- Rejeição de transições inválidas sem persistir alterações parciais.

## Requisitos

Para executar o projeto localmente:

- JDK 25
- Docker
- Docker Compose

O Maven Wrapper está incluído no repositório, portanto não é necessário instalar o Maven globalmente.

## Executando localmente

### 1. Iniciar o PostgreSQL

Na raiz do projeto:

```bash
docker compose up -d
```

Verifique se o container está em execução:

```bash
docker compose ps
```

Os dados do PostgreSQL são mantidos em um volume Docker.

### 2. Iniciar a aplicação

Com o PostgreSQL disponível e a configuração local da aplicação ajustada:

```bash
./mvnw spring-boot:run
```

Por padrão, a aplicação fica disponível em:

```text
http://localhost:8080
```

As migrations pendentes são aplicadas automaticamente pelo Flyway durante a inicialização.

### 3. Encerrar a infraestrutura

```bash
docker compose down
```

Esse comando encerra os containers sem remover o volume persistente do PostgreSQL.

## API HTTP

O Metopa expõe uma API REST para gerenciamento de contas, obras, publicações, bibliotecas, progresso de leitura e moderação administrativa.

### Endpoints públicos

```http
POST /api/users
GET  /api/works
GET  /api/works/{workId}
GET  /api/installments/{installmentId}
GET  /api/pages/{pageId}/content
```

O acesso público ao conteúdo depende do estado de publicação e da visibilidade da obra.

Obras `RESTRICTED` não são disponibilizadas pelos endpoints públicos de leitura.

### Descoberta pública de obras

O endpoint `GET /api/works` permite descobrir obras públicas com pelo menos um installment publicado.

A descoberta oferece busca por título, filtragem por gênero e paginação. Todos os parâmetros são opcionais e podem ser combinados.

| Parâmetro | Descrição | Padrão |
|-----------|-----------|--------|
| `title` | Busca parcial pelo título, sem diferenciar maiúsculas e minúsculas | Sem filtro |
| `genre` | Filtra obras pelo gênero informado | Sem filtro |
| `page` | Índice da página, começando em zero | `0` |
| `size` | Quantidade de obras por página, entre 1 e 50 | `20` |

Exemplos:

```http
GET /api/works
GET /api/works?page=0&size=10
GET /api/works?genre=SCI_FI
GET /api/works?title=dragon
GET /api/works?title=dragon&genre=SCI_FI
GET /api/works?title=dragon&genre=SCI_FI&page=0&size=10
```

Regras da descoberta pública:

- Somente obras com pelo menos um installment `PUBLISHED` são retornadas.
- Somente obras com visibilidade `PUBLIC` são incluídas.
- Uma obra aparece apenas uma vez, mesmo que possua vários installments publicados.
- A busca por título corresponde a qualquer trecho do texto informado.
- Espaços no início e no final do título pesquisado são ignorados.
- Títulos de busca ausentes ou em branco não aplicam filtro textual.
- Os caracteres `%` e `_` são tratados literalmente, não como curingas SQL.
- Os filtros são aplicados no PostgreSQL antes da paginação.
- Os resultados são ordenados pela data de atualização mais recente de um installment publicado, em ordem decrescente, com desempate pelo identificador da obra.
- Parâmetros de paginação inválidos retornam HTTP `400 Bad Request`.

A resposta mantém os seguintes campos de paginação:

| Campo | Descrição |
|-------|-----------|
| `content` | Lista de obras da página solicitada |
| `page` | Índice da página retornada |
| `size` | Tamanho configurado da página |
| `totalElements` | Quantidade total de obras que correspondem aos filtros |
| `totalPages` | Quantidade total de páginas |

A consulta utiliza PostgreSQL `ILIKE` para busca textual, sem dependência de mecanismos externos de busca.

### Endpoints autenticados

Exemplos de endpoints disponíveis para usuários autenticados:

```http
GET    /api/me/works
POST   /api/works
PUT    /api/works/{workId}
POST   /api/works/{workId}/installments
DELETE /api/installments/{installmentId}
POST   /api/installments/{installmentId}/pages
PUT    /api/installments/{installmentId}/pages/order
POST   /api/installments/{installmentId}/publish
GET    /api/library
PUT    /api/reading/progress/pages/{pageId}
```

As operações protegidas exigem sessão autenticada e, quando alteram estado, token CSRF válido.

A autorização também considera a propriedade dos recursos quando aplicável.

O módulo de moderação disponibiliza o registro de denúncias de obras por usuários autenticados.

### Endpoints administrativos de moderação

As operações administrativas de moderação são disponibilizadas em:

```http
GET  /api/admin/moderation/reports

POST /api/admin/moderation/reports/{reportId}/start-review

POST /api/admin/moderation/reports/{reportId}/resolve

POST /api/admin/moderation/reports/{reportId}/restore
```

Esses endpoints exigem autenticação com `ROLE_ADMIN`.

A listagem administrativa permite filtrar denúncias por status e configurar a paginação.

As operações de alteração de estado recebem uma justificativa administrativa.

Exemplo de resolução sem violação:

```json
{
  "decision": "NO_VIOLATION",
  "note": "No violation was found."
}
```

Exemplo de resolução com restrição:

```json
{
  "decision": "WORK_RESTRICTED",
  "note": "Confirmed content policy violation."
}
```

Exemplo de restauração:

```json
{
  "note": "Restriction reversed after administrative review."
}
```

As operações administrativas bem-sucedidas de início de análise, resolução e restauração retornam HTTP `204 No Content`.

Transições inválidas, como uma resolução duplicada ou a tentativa de restaurar uma obra já pública, retornam HTTP `409 Conflict`.

A restauração preserva o estado `RESOLVED` e a decisão original da denúncia, registrando um novo evento `WORK_RESTORED`.

Contratos completos, payloads, autenticação e respostas HTTP estão documentados em [API](docs/API.md).

## Testes

O Metopa utiliza testes unitários e de integração para validar regras de negócio, persistência, contratos HTTP e comportamento da aplicação.

### Executando os testes

Durante o desenvolvimento, para executar a suíte de testes:

```bash
./mvnw clean test
```

Para executar uma classe específica:

```bash
./mvnw -Dtest=PublicationServiceTests test
```

Para executar um único método de teste:

```bash
./mvnw -Dtest=AdminModerationResolveIntegrationTests#shouldRestoreRestrictedWorkUsingRealAdminSession test
```

Antes de abrir um Pull Request, a validação completa recomendada é:

```bash
./mvnw clean verify
```

Esse comando executa as fases do Maven até `verify`, incluindo testes e empacotamento da aplicação.

### Última validação local

Resultado de `./mvnw clean verify` em **21 de novembro de 2026**:

```text
Tests run: 441
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

A execução confirmou a compilação do projeto, a aprovação dos testes e a geração do JAR executável do Spring Boot.

A suíte cobre:

- Regras de domínio e validações.
- Persistência real no PostgreSQL.
- Endpoints HTTP e integração com Spring MVC.
- Autenticação, autorização, sessão e CSRF.
- Autorização baseada em propriedade e papéis `USER` e `ADMIN`.
- Armazenamento e leitura de arquivos binários.
- Operações de upload, exclusão e reordenação.
- Rollback e compensação de operações com arquivos.
- Concorrência utilizando transações independentes e bloqueios pessimistas.
- Descoberta pública de obras, filtros combinados, busca textual e paginação.
- Moderação administrativa, registro de denúncias e transições de estado.
- Resolução de denúncias e restrição de obras.
- Restauração administrativa e preservação do histórico de moderação.
- Auditoria de ações administrativas.
- Rejeição de operações duplicadas e transições inválidas.
- Rollback de operações administrativas.
- Bloqueio e restauração do acesso público a obras publicadas.
- Verificação da modularidade com Spring Modulith.

Os testes de integração que dependem do banco exigem o PostgreSQL disponível.

O número de testes é utilizado como referência de validação, mas não substitui a análise dos cenários cobertos e das regras de negócio verificadas.

### Integração contínua

O backend possui um workflow de CI configurado no GitHub Actions.

A validação automática é executada em Pull Requests direcionados à `main` e em alterações enviadas à branch principal, além de permitir execução manual.

O workflow utiliza:

- Java 25.
- PostgreSQL 18.6 como serviço de teste.
- Maven Wrapper.
- Execução de `./mvnw -B -ntp clean verify`.

O workflow valida os testes automatizados e o empacotamento do backend em um ambiente de integração contínua.

A branch `main` possui proteção por regras do GitHub, exigindo Pull Request e aprovação da verificação automatizada do backend antes da integração das alterações.

Para informações detalhadas, consulte [Testes](docs/TESTING.md).

## Banco de dados

O schema do PostgreSQL é gerenciado pelo Flyway.

As migrations estão localizadas em:

```text
src/main/resources/db/migration/
```

O projeto utiliza migrations versionadas. Arquivos de migrations já executadas não devem ser modificados; alterações posteriores são introduzidas por novas versões.

### Evolução do schema

A implementação da moderação mínima introduziu as migrations V11–V18:

| Migration | Responsabilidade |
|-----------|------------------|
| V11 | Criação da tabela de denúncias |
| V12 | Inclusão de papéis nas contas de usuários |
| V13 | Criação da auditoria de moderação |
| V14 | Inclusão das decisões de moderação |
| V15 | Validação das decisões e ações de auditoria |
| V16 | Inclusão da visibilidade das obras |
| V17 | Suporte à decisão `WORK_RESTRICTED` |
| V18 | Suporte à auditoria `WORK_RESTORED` |

As constraints do PostgreSQL complementam as validações realizadas pelas entidades e serviços da aplicação.

As decisões de persistência e integridade estão descritas em [Arquitetura](docs/ARCHITECTURE.md).

## Segurança

A autenticação do MVP utiliza sessões HTTP gerenciadas pelo Spring Security.

As principais medidas implementadas incluem:

- Hash de senhas com bcrypt.
- Proteção CSRF habilitada.
- Validação de autenticação para operações privadas.
- Autorização baseada na propriedade das obras.
- Separação entre conteúdo público e conteúdo administrativo.
- Papéis de acesso `USER` e `ADMIN`.
- Restrição de endpoints administrativos de moderação a `ROLE_ADMIN`.
- Bloqueio de alterações em installments publicados.
- Bloqueio de leitura pública de obras `RESTRICTED`.
- Proteção contra criação e atualização de progresso de leitura em obras restritas.
- Validação de transições administrativas de estado.
- Registro persistente de auditoria das ações de moderação.
- Respostas HTTP apropriadas para falhas de autenticação, autorização e regras de negócio.

A restrição de visibilidade não exclui a obra ou seu conteúdo armazenado.

Quando uma obra é restaurada, seu conteúdo publicado pode voltar a ser disponibilizado pelos endpoints públicos, preservando o histórico de moderação.

## Roadmap do MVP

O desenvolvimento prioriza a conclusão do backend antes da implementação da interface do MVP.

### Backend

- [x] Paginação da descoberta pública de obras.
- [x] Busca parcial de obras por título.
- [x] Combinação dos filtros de título e gênero.
- [x] Moderação mínima.
- [x] Restrição e restauração administrativa de obras.
- [ ] Reconciliação de arquivos órfãos após falhas de compensação.

### Frontend

- [ ] Definição da arquitetura da interface.
- [ ] Fluxos de autenticação e navegação.
- [ ] Interface de publicação e gerenciamento de obras.
- [ ] Interface de descoberta e leitura.
- [ ] Biblioteca pessoal e acompanhamento de leitura.
- [ ] Integração com a API do backend.

As etapas futuras serão desenvolvidas incrementalmente, priorizando consistência, testes automatizados e a manutenção dos limites entre módulos.

## Documentação

A documentação técnica detalhada é organizada nos seguintes arquivos:

| Documento | Conteúdo |
|-----------|----------|
| [API](docs/API.md) | Endpoints, payloads, autenticação, filtros, paginação, respostas HTTP e regras de acesso |
| [Arquitetura](docs/ARCHITECTURE.md) | Módulos, decisões arquiteturais, persistência, segurança, armazenamento, transações e consistência |
| [Testes](docs/TESTING.md) | Estratégia de testes, cobertura funcional, integração, rollback, concorrência, moderação e CI |

O README apresenta a visão geral do projeto. Os documentos técnicos detalham os contratos, as decisões arquiteturais e os cenários de validação.

## Desenvolvimento

O desenvolvimento é organizado em branches de funcionalidades, correções, refatorações e documentação.

Fluxo habitual:

```text
main
  ↓
feature/*, fix/*, refactor/* ou docs/*
  ↓
commit
  ↓
push
  ↓
pull request
  ↓
CI
  ↓
merge
```

As alterações são integradas à `main` por meio de Pull Requests, após a validação dos checks obrigatórios no GitHub Actions.

O desenvolvimento prioriza incrementos pequenos, testes automatizados, clareza das responsabilidades e preservação dos limites entre módulos.
