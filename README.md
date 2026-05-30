# hubsaude-cliente-cli

[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-orange)](https://maven.apache.org/)
[![Version](https://img.shields.io/badge/Version-0.1.5-yellow)](https://github.com/FabricaDeSoftwareINF/server-hubsaude)
[![License](https://img.shields.io/badge/License-SES--GO%2FUFG-green)](#licença)

Ferramenta de conveniência para verificar que a obtenção de token de acesso ao HubSaúde via SMART Backend Services pode ser realizada de forma satisfatória. Não se destina ao uso operacional para obter tokens — o sucesso da verificação evidencia que o credenciamento foi concluído corretamente e que a requisição e emissão do token funcionam como esperado.

> Usa [`hubsaude-cliente-java`](../hubsaude-cliente-java) para obtenção do token de acesso.

> **Caminho recomendado:** para o caso comum — chave privada de **software** (PEM, PEM cifrado ou PKCS#12) — prefira o `hubsaude auth verify` do [`hubsaude-cli`](../hubsaude-cli), que faz a mesma verificação **sem exigir Java instalado** (binário único Go, início instantâneo). Esta ferramenta (`hubsaude-cliente-cli`) permanece como verificador de **nicho** para fontes de chave **não-exportáveis** — HSM/PKCS#11 e OpenBao — que dependem do SDK Java e ficam fora do escopo do CLI nativo (ver ADR-CLI-19 da spec do hubsaude-cli).

## Quando usar

Após o credenciamento ser aprovado (o pedido é feito pelo Sistema Ganesha), use esta ferramenta para confirmar de forma rápida, sem escrever código, que o acesso ao HubSaúde está funcionando com a sua chave privada e certificado — em especial quando a chave residir em **HSM/PKCS#11** ou em **cofre (OpenBao)**. Para chaves de software (PEM/PKCS#12), o caminho mais simples é `hubsaude auth verify`.



## Build

A partir do diretório do projeto (`projetos/hubsaude-cliente-cli`):

```bash
mvn clean package
```

Gera o fat-jar executável em:

```
target/hubsaude-cliente-cli-0.1.5.jar
```


## Uso

```bash
# Verificação básica
java -jar hubsaude-cliente-cli-0.1.5.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem

# Com detalhes do token
java -jar hubsaude-cliente-cli-0.1.5.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem \
    --verbose
```

**Saída esperada (sucesso):**

```
╔═══════════════════════════════════════════════════════════╗
║     HubSaúde - Verificador de Acesso (Pós-Credenciamento) ║
╚═══════════════════════════════════════════════════════════╝

ℹ Configuração:
  Client ID:  hs-12345678
  Chave:      minha-chave.pem
  Certificado:meu-certificado.pem
  Scope:      system/Patient.rs
  TLS:        TLSv1.3
  Algoritmo:  RS256
  FHIR Base:  https://fhir.saude.go.gov.br
  Endpoint:   https://fhir.saude.go.gov.br/auth/token (descoberto via .well-known)

ℹ Obtendo token de acesso...
✓ Token obtido com sucesso!

  Tempo de resposta: 245ms
  Token (primeiros 50 chars): eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOi...

✓ Credenciamento verificado. Acesso ao HubSaúde está funcionando.
```

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
| `--verbose`    | Mostra detalhes do token obtido                                            | Não         |

---

## Preparação de chave e certificado

Para detalhes de geração e conversão de chaves/certificados (PEM, PKCS#12, PKCS#11/HSM, ICP-Brasil), consulte o README da biblioteca: [`hubsaude-cliente-java/README.md`](../hubsaude-cliente-java/README.md).

---

## Release (publicação no GitHub Packages)

A publicação é automatizada pelo workflow [`.github/workflows/hubsaude-cliente-cli-release.yml`](../../../.github/workflows/hubsaude-cliente-cli-release.yml) e é **disparada por tag** com o padrão `cliente-cli-v<MAJOR>.<MINOR>.<PATCH>` (convenção ADR-33). A versão publicada é derivada da tag — o `<version>` do `pom.xml` é sobrescrito apenas durante a execução do workflow (sem commit no repositório).

**Pré-requisitos:**

1. `main` (ou branch alvo) verde no workflow [`hubsaude-cliente-cli-tests.yml`](../../../.github/workflows/hubsaude-cliente-cli-tests.yml).
2. Versão da dependência `hubsaude-cliente-java` no [`pom.xml`](pom.xml) apontando para uma release já publicada no GitHub Packages.
3. Permissão de `packages:write` no repositório (já concedida ao `GITHUB_TOKEN` do workflow).

**Passos para publicar uma nova versão (ex.: `0.1.5`):**

```bash
# 1. Garanta que está na branch alvo, sincronizada com o remoto
git checkout main
git pull --ff-only

# 2. Crie a tag semver com o prefixo cliente-cli-v
git tag -a cliente-cli-v0.1.5 -m "Release hubsaude-cliente-cli 0.1.5"

# 3. Empurre a tag para disparar o workflow de release
git push origin cliente-cli-v0.1.5
```

Acompanhe a execução em **Actions → hubsaude-cliente-cli-release** no GitHub. Ao final, o artefato estará disponível em:

```
https://maven.pkg.github.com/FabricaDeSoftwareINF/server-hubsaude
   br.gov.go.saude.hubsaude:hubsaude-cliente-cli:0.1.5
```

**Disparo manual (excepcional, para testes):** use *Run workflow* na aba Actions informando a versão (ex.: `0.1.5-SNAPSHOT`).

**Reverter uma tag publicada por engano:**

```bash
git push --delete origin cliente-cli-v0.1.5   # remove a tag remota
git tag -d cliente-cli-v0.1.5                 # remove a tag local
```

> O pacote já publicado no GitHub Packages permanece — remova-o manualmente em *Packages → hubsaude-cliente-cli* se necessário.

---

## Licença

Copyright (c) 2026 SES-GO / UFG. Todos os direitos reservados.
