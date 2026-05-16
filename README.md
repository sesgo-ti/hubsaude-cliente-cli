# hubsaude-cliente-cli

[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9%2B-orange)](https://maven.apache.org/)
[![Version](https://img.shields.io/badge/Version-0.0.0--SNAPSHOT-yellow)](https://github.com/FabricaDeSoftwareINF/server-hubsaude)
[![License](https://img.shields.io/badge/License-SES--GO%2FUFG-green)](#licença)

Ferramenta de conveniência para **verificação de acesso ao HubSaúde** após o credenciamento. Internamente usa a biblioteca [`hubsaude-cliente-java`](../hubsaude-cliente-java) para obter um token via SMART Backend Services e confirmar que o credenciamento está operacional.

> Para uso programático (chamada da biblioteca em código Java), consulte [`hubsaude-cliente-java`](../hubsaude-cliente-java).

---

## Quando usar

Após o credenciamento ser aprovado (o pedido é feito pelo Sistema Ganesha), use esta ferramenta para confirmar de forma rápida, sem escrever código, que o acesso ao HubSaúde está funcionando com a sua chave privada e certificado.

---

## Build

```bash
mvn -pl projetos/hubsaude-cliente-cli -am package
```

Gera o fat-jar executável em:

```
projetos/hubsaude-cliente-cli/target/hubsaude-cliente-cli-0.0.0-SNAPSHOT.jar
```

**Requisitos:** Java 21+, Maven 3.9+.

---

## Uso

```bash
# Verificação básica
java -jar hubsaude-cliente-cli-0.0.0-SNAPSHOT.jar \
    --client-id=hs-12345678 \
    --key=minha-chave.pem \
    --cert=meu-certificado.pem

# Com detalhes do token
java -jar hubsaude-cliente-cli-0.0.0-SNAPSHOT.jar \
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

## Licença

Copyright (c) 2026 SES-GO / UFG. Todos os direitos reservados.
