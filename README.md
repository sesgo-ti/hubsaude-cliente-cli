# hubsaude-cliente-cli

[![Version](https://img.shields.io/badge/Version-0.3.8-yellow)](https://github.com/FabricaDeSoftwareINF/server-hubsaude)
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
target/hubsaude-cliente-cli-0.3.0-SNAPSHOT.jar
```

> Os exemplos da seção **Uso** referenciam o nome da release publicada
> (`hubsaude-cliente-cli-0.3.0.jar`); o build local produz o artefato com
> sufixo `-SNAPSHOT`. Ajuste o nome conforme o jar que você possui.


## Uso

> A CLI recebe a chave privada e o certificado como arquivos **PEM** (chave em
> claro ou cifrada com `--password`).

### Verificação básica

Descoberta automática do endpoint via `.well-known/smart-configuration` a partir
da base FHIR padrão (`https://hub.saude.go.gov.br`):

```bash
java -jar hubsaude-cliente-cli-0.3.0.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem
```

Por padrão a saída inclui os detalhes do token. Use `--quiet` para ocultá-los:

```bash
java -jar hubsaude-cliente-cli-0.3.0.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --quiet
```

### Endpoint explícito e *scope* específico

Aponta diretamente ao servidor de autorização (desabilitando a descoberta) e
solicita um *scope* diferente do padrão `system/Patient.rs` — útil, por exemplo,
contra um ambiente de *staging*:

```bash
java -jar hubsaude-cliente-cli-0.3.0.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --endpoint=https://auth.staging.saude.go.gov.br/realms/hub/protocol/openid-connect/token \
    --scope=system/Observation.rs
```

### Chave PEM cifrada

Informe a senha com `--password`. Se a chave estiver cifrada e `--password` for
omitido, a senha é solicitada interativamente no terminal:

```bash
java -jar hubsaude-cliente-cli-0.3.0.jar \
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
java -jar hubsaude-cliente-cli-0.3.0.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --trust=ca-homologacao.pem \
    --tls=TLSv1.2
```

### Execução em desenvolvimento (Maven)

Sem empacotar o fat-jar, a partir do diretório do projeto
(`projetos/hubsaude-cliente-cli`):

```bash
mvn exec:java \
    -Dexec.mainClass="br.gov.go.saude.hubsaude.client.cli.VerificarAcesso" \
    -Dexec.args="--client-id=hs-12345678 --key=minha-chave.pem --cert=meu-certificado.pem"
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
  TLS:        TLSv1.3
  Algoritmo:  RS256
  FHIR Base:  https://hub.saude.go.gov.br
  Endpoint:   https://hub.saude.go.gov.br/auth/token (descoberto via .well-known)

[i] Obtendo token de acesso...
[OK] Token obtido com sucesso!

  Tempo de resposta: 245ms
  Token (primeiros 50 chars): eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOi...
[i] Omita --quiet para ver detalhes do token.

[OK] Credenciamento verificado. Acesso ao HubSaúde está funcionando.
```

Sem `--quiet` (padrão), no lugar do *preview* de 50 caracteres são exibidos a
resposta do servidor de autorização e as tabelas do *header*/*payload* do JWT.

---

## Diagnóstico de falhas

Em caso de erro, a ferramenta imprime uma mensagem prefixada por `[X]` em
`stderr` e encerra com **código de saída 1**. Há duas categorias:

- **Erro de configuração** — arquivo ausente/ilegível, argumento desconhecido ou
  parâmetro obrigatório faltando:

  ```
  [X] Erro de configuração: Chave privada não encontrado: minha-chave.pem
  ```

- **Falha ao obter token** — credenciamento não aprovado, `client-id` inválido,
  problema de TLS/CA ou conectividade. Sem `--quiet`, o *stack trace* é exibido
  para facilitar o diagnóstico:

  ```
  [X] Falha ao obter token: <detalhe da causa>
  ```

  (o texto após os dois-pontos varia conforme a causa)

### Códigos de saída

| Código | Significado                                      |
| ------ | ------------------------------------------------ |
| `0`    | Sucesso — token obtido, credenciamento OK        |
| `1`    | Falha — erro de configuração ou acesso negado    |

---

## Parâmetros

| Parâmetro      | Descrição                                                                  | Obrigatório |
|----------------|----------------------------------------------------------------------------|-------------|
| `--client-id`  | ID do cliente (fornecido no credenciamento)                                | Sim         |
| `--key`        | Caminho para a chave privada PEM                                           | Sim         |
| `--cert`       | Caminho para o certificado PEM                                             | Sim         |
| `--endpoint`   | URL do token endpoint (desabilita descoberta automática)                   | Não         |
| `--fhir-base`  | URL base FHIR (padrão: `https://hub.saude.go.gov.br`)                      | Não         |
| `--scope`      | Scope a solicitar (padrão: `system/Patient.rs`)                            | Não         |
| `--password`   | Senha da chave privada (se criptografada)                                  | Não         |
| `--tls`        | Protocolo TLS: `TLSv1.3` ou `TLSv1.2` (padrão: `TLSv1.3`)                  | Não         |
| `--alg`        | Algoritmo JWT: `RS256`, `RS384`, `RS512`, `PS256`, etc. (padrão: `RS256`)  | Não         |
| `--trust`      | Certificado PEM do servidor (trust anchor, para simulador/homologação)     | Não         |
| `--quiet`      | Oculta os detalhes do token (exibidos por padrão)                          | Não         |

---

## Preparação de chave e certificado

Para detalhes de geração e conversão de chaves/certificados (PEM, PKCS#12, ICP-Brasil), consulte o README da biblioteca: [`hubsaude-cliente-java/README.md`](../hubsaude-cliente-java/README.md).

---

## Release (publicação no GitHub Packages)

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
