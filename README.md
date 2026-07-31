# hubsaude-cliente-cli

[![Version](https://img.shields.io/badge/Version-0.3.26-yellow)](https://github.com/FabricaDeSoftwareINF/server-hubsaude)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-orange)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-SES--GO%2FUFG-green)](#licença)

Ferramenta de conveniência para verificar que a obtenção de token 
de acesso ao HubSaúde via SMART Backend Services pode ser realizada 
de forma satisfatória. 

Não se destina ao uso operacional para obter tokens. O
sucesso da verificação apenas evidencia que o credenciamento 
foi concluído corretamente para o certificado digital empregado
na verificação. 

> Usa [`hubsaude-cliente-java`](../hubsaude-cliente-java) para obtenção 
> do token de acesso.

> **Alternativa sem Java:** a mesma verificação pode ser feita com o 
> `hubsaude auth verify` do [`hubsaude-cli`](../hubsaude-cli) — binário 
> único (Go), **sem exigir Java instalado** e com início instantâneo.

## Quando usar

Após o credenciamento ser aprovado (o pedido é feito pelo Sistema Ganesha), 
use esta ferramenta para confirmar de forma rápida, sem escrever código, 
que o acesso ao HubSaúde está funcionando com a sua chave privada e 
certificado em arquivos PEM.

## Build

A partir do diretório do projeto (`projetos/hubsaude-cliente-cli`):

```bash
mvn clean package
```

Gera o fat-jar executável em:

```
target/hubsaude-cliente-cli-<versão>-SNAPSHOT.jar
```

> Os exemplos da seção **Uso** referenciam o nome genérico
> `hubsaude-cliente-cli-<versão>.jar` — substitua `<versão>` pela versão do
> jar que você possui (release publicada, ex.: `0.3.16`, ou build local com
> sufixo `-SNAPSHOT`).


## Uso

> A CLI recebe a chave privada e o certificado como arquivos **PEM** (chave em
> claro ou cifrada com `--password`).

### Verificação básica

Descoberta automática do endpoint via `.well-known/smart-configuration` a partir
da base FHIR padrão (`https://hub.saude.go.gov.br`):

```bash
java -jar hubsaude-cliente-cli-<versão>.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --ig=hemograma \
    --ig-versao=0.0.1
```

Por padrão a saída inclui os detalhes do token (header/payload decodificados
e prévia truncada de 50 caracteres — o token completo exige `--show-token`).
Use `--quiet` para ocultá-los:

```bash
java -jar hubsaude-cliente-cli-<versão>.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --ig=hemograma \
    --ig-versao=0.0.1 \
    --quiet
```

### Endpoint explícito e *scope* específico

Aponta diretamente ao servidor de autorização (desabilitando a descoberta) e
solicita um *scope* diferente do padrão `system/Patient.rs` — útil, por exemplo,
contra um ambiente de *staging*:

```bash
java -jar hubsaude-cliente-cli-<versão>.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --ig=hemograma \
    --ig-versao=0.0.1 \
    --endpoint=https://auth.staging.saude.go.gov.br/realms/hub/protocol/openid-connect/token \
    --scope=system/Observation.rs
```

### Chave PEM cifrada

Informe a senha com `--password`. Se a chave estiver cifrada e `--password` for
omitido, a senha é solicitada interativamente no terminal:

```bash
java -jar hubsaude-cliente-cli-<versão>.jar \
    --client-id=hs-12345678 \
    --key=chave-encrypted.pem \
    --cert=meu-certificado.pem \
    --password=senha-da-chave
```

### Homologação/simulador com CA interna

Forneça o *trust anchor* do servidor com `--trust` (e, se necessário, fixe o
protocolo TLS). Em produção a CA do HubSaúde já está no *trust store* da JVM e
esta opção é desnecessária:

```bash
java -jar hubsaude-cliente-cli-<versão>.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --ig=hemograma \
    --ig-versao=0.0.1 \
    --trust=ca-homologacao.pem \
    --tls=TLSv1.2
```

### Execução em desenvolvimento (Maven)

Sem empacotar o fat-jar, a partir do diretório do projeto
(`projetos/hubsaude-cliente-cli`):

```bash
mvn exec:java \
    -Dexec.mainClass="br.gov.go.saude.hubsaude.client.cli.VerificarAcesso" \
    -Dexec.args="--client-id=hs-12345678 --key=minha-chave.pem --cert=meu-certificado.pem --ig=hemograma --ig-versao=0.0.1"
```

**Saída esperada (modo `--quiet`):**

```
╔═══════════════════════════════════════════════════════════╗
║     HubSaúde - Verificador de Acesso (Pós-Credenciamento) ║
╚═══════════════════════════════════════════════════════════╝

[i] Configuração:
  Client ID:  hs-12345678
  Chave:      minha-chave.pem
  Certificado:meu-certificado.pem
  Scope:      system/Patient.rs
  IG:         hemograma (versão 0.0.1)
  TLS:        TLSv1.3
  Algoritmo:  RS384
  FHIR Base:  https://hub.saude.go.gov.br
  Endpoint:   https://hub.saude.go.gov.br/auth/token (descoberto via .well-known)

[i] Obtendo token de acesso...
[OK] Token obtido com sucesso!

  Tempo de resposta: 245ms
  Token (primeiros 50 chars): eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOi...
[i] Omita --quiet para ver detalhes do token.

[OK] Credenciamento verificado. Acesso ao HubSaúde está funcionando.
```

Sem `--quiet` (padrão), além do *preview* de 50 caracteres são exibidos a
resposta do servidor de autorização (com os segredos redigidos) e as tabelas
do *header*/*payload* do JWT. O access token **completo** só é exibido com a
flag explícita `--show-token` (issue #813) — evite-a em CI/CD, pois o token
ficaria retido nos logs do pipeline.

---

## Diagnóstico de falhas

Em caso de erro, a ferramenta imprime uma mensagem prefixada por `[X]` em
`stderr`, seguida do **veredito da classificação**, e encerra com o código de
saída correspondente (ver [Códigos de saída](#códigos-de-saída)):

- **Erro de uso/configuração (exit `3`)** — arquivo ausente/ilegível, argumento
  desconhecido ou parâmetro obrigatório faltando:

  ```
  [X] Erro de configuração: Chave privada não encontrado: minha-chave.pem
  [i] Erro de uso: verifique os argumentos e arquivos informados (exit 3).
  ```

- **Credencial NÃO apta (exit `6`)** — rejeição determinística do servidor de
  autorização: HTTP 400/401 (`invalid_client`) ou certificado de cliente
  rejeitado no mTLS:

  ```
  [X] Falha ao obter token: Falha ao obter token: HTTP 401 — {"error":"invalid_client"}
  [X] Credencial NÃO apta: rejeição determinística do servidor de autorização (HTTP 400/401 invalid_client ou handshake mTLS) — exit 6.
  ```

- **Falha de infraestrutura de rede (exit `4`)** — conectividade, timeout,
  descoberta `.well-known`, rate limit 429 ou 5xx do token endpoint. O teste é
  **inconclusivo**: nada se pode afirmar sobre a credencial:

  ```
  [X] Falha ao obter token: <detalhe da causa>
  [i] Teste INCONCLUSIVO: falha de infraestrutura de rede — repita mais tarde (exit 4).
  ```

Por padrão apenas a mensagem curta é exibida; com `--debug`, os detalhes
técnicos (*stack trace*) são exibidos para facilitar o diagnóstico.

### Códigos de saída

A ferramenta adota a mesma taxonomia do comando `hubsaude auth verify` do
[`hubsaude-cli`](../hubsaude-cli) (spec §12.1), permitindo que automação
(pipelines de credenciamento, smoke-tests agendados) distinga credencial
rejeitada de falha de rede:

| Código | Significado                                                     | Ação recomendada para automação                       |
| ------ | --------------------------------------------------------------- | ----------------------------------------------------- |
| `0`    | Sucesso — token obtido, credencial apta                         | Nenhuma                                                |
| `3`    | Erro de uso — argumentos, arquivos ou configuração inválidos    | Corrigir a invocação/configuração                      |
| `4`    | Infraestrutura de rede — teste **inconclusivo** (discovery, timeout, 429, 5xx) | Reexecutar mais tarde (retry/backoff)   |
| `6`    | Credencial **não apta** — rejeição determinística (HTTP 400/401 `invalid_client` ou handshake mTLS) | Acionar o processo de credenciamento |

> **Mudança de contrato (pré-1.0, issue #817):** até a release `0.3.22` toda
> falha encerrava com exit `1`. A partir da release seguinte os códigos
> `3`/`4`/`6` substituem o `1` — ajuste scripts que testavam `$? -eq 1`.
>
> **Classificação fail-safe:** falhas que não podem ser atribuídas com certeza
> determinística à credencial (ex.: mensagens ambíguas da biblioteca) são
> classificadas como `4` (inconclusivo) — a ferramenta jamais declara uma
> credencial inapta sem evidência determinística.

---

## Parâmetros

| Parâmetro      | Descrição                                                                  | Obrigatório |
|----------------|----------------------------------------------------------------------------|-------------|
| `--client-id`  | ID do cliente (fornecido no credenciamento)                                | Sim         |
| `--key`        | Caminho para a chave privada PEM                                           | Sim         |
| `--cert`       | Caminho para o certificado PEM                                             | Sim         |
| `--ig`         | Alias do Guia de Implementação pretendido (claim `hub_ctx.ig`)             | Sim         |
| `--ig-versao`  | Versão SemVer completa do IG (claim `hub_ctx.versao`, ex.: `0.0.1`)        | Sim         |
| `--endpoint`   | URL do token endpoint (desabilita descoberta automática)                   | Não         |
| `--fhir-base`  | URL base FHIR (padrão: `https://hub.saude.go.gov.br`)                      | Não         |
| `--scope`      | Scope a solicitar (padrão: `system/Patient.rs`)                            | Não         |
| `--password`   | Senha da chave privada (se criptografada)                                  | Não         |
| `--tls`        | Protocolo TLS: `TLSv1.3` ou `TLSv1.2` (padrão: `TLSv1.3`)                  | Não         |
| `--alg`        | Algoritmo JWT aceito pelo HubSaúde: `RS384` ou `ES384` (padrão: `RS384`)   | Não         |
| `--kid`        | Identificador da chave registrada no SA (header `kid`, rotação de chaves)  | Não         |
| `--trust`      | Certificado PEM do servidor (trust anchor, para simulador/homologação)     | Não         |
| `--quiet`      | Oculta os detalhes do token (exibidos por padrão)                          | Não         |
| `--show-token` | Exibe o access token completo (sensível; oculto por padrão)                | Não         |
| `--debug`      | Exibe detalhes técnicos (*stack trace*) em caso de erro                    | Não         |

---

## Preparação de chave e certificado

Para detalhes de geração e conversão de chaves/certificados (PEM, PKCS#12, ICP-Brasil), consulte o README da biblioteca: [`hubsaude-cliente-java/README.md`](../hubsaude-cliente-java/README.md).

---

## Publicação de nova versão (release)

A publicação é automatizada pelo workflow [`.github/workflows/hubsaude-cliente-cli-release.yml`](../../../.github/workflows/hubsaude-cliente-cli-release.yml) e é **disparada por tag** com o padrão `cliente-cli-v<MAJOR>.<MINOR>.<PATCH>` (convenção ADR-33). A versão publicada é derivada da tag — o `<version>` do `pom.xml` é sobrescrito apenas durante a execução do workflow (sem commit no repositório).

**Pré-requisitos:**

1. `main` (ou branch alvo) verde no workflow [`hubsaude-cliente-cli-tests.yml`](../../../.github/workflows/hubsaude-cliente-cli-tests.yml).
2. Versão da dependência `hubsaude-cliente-java` no [`pom.xml`](pom.xml) apontando para uma release já publicada no GitHub Packages.
3. Permissão de `packages:write` no repositório (já concedida ao `GITHUB_TOKEN` do workflow).

**Passos para publicar uma nova versão (ex.: `0.3.1`):**

```bash
# 1. Garanta que está na branch alvo, sincronizada com o remoto
git checkout main
git pull --ff-only

# 2. Crie a tag semver com o prefixo cliente-cli-v
git tag -a cliente-cli-v0.3.1 -m "Release hubsaude-cliente-cli 0.3.1"

# 3. Empurre a tag para disparar o workflow de release
git push origin cliente-cli-v0.3.1
```

Acompanhe a execução em **Actions → hubsaude-cliente-cli-release** no GitHub. Ao final, o artefato estará disponível em:

```
https://maven.pkg.github.com/FabricaDeSoftwareINF/server-hubsaude
   br.gov.go.saude.hubsaude:hubsaude-cliente-cli:0.3.1
```

**Disparo manual (excepcional, para testes):** use *Run workflow* na aba Actions informando a versão (ex.: `0.3.1-SNAPSHOT`).

**Reverter uma tag publicada por engano:**

```bash
git push --delete origin cliente-cli-v0.3.1   # remove a tag remota
git tag -d cliente-cli-v0.3.1                 # remove a tag local
```

> O pacote já publicado no GitHub Packages permanece — remova-o manualmente em *Packages → hubsaude-cliente-cli* se necessário.

---

## Licença

Copyright (c) 2026 SES-GO / UFG. Todos os direitos reservados.
