# Changelog

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Alterado
- **Higiene de credenciais no stdout (#813):** o modo detalhado (sem
  `--quiet`) deixa de imprimir o access token completo por padrão —
  passa a exibir header/payload decodificados e a prévia truncada de
  50 caracteres, coerente com a redação `<REDACTED>` já aplicada à
  resposta do servidor de autorização. A nova flag `--show-token`
  restaura a exibição integral, mediante solicitação explícita.
  Alinha o contrato ao `auth verify` do `hubsaude-cli` (US-14).
- **Mudança de contrato (pré-1.0):** os códigos de saída de falha deixam
  de colapsar em `1` e passam a adotar a taxonomia do `hubsaude-cli`
  para o caso de uso `auth verify` (#817): `0` sucesso (credencial
  apta); `3` erro de uso/configuração (argumentos ou arquivos
  inválidos); `4` falha de infraestrutura de rede — teste
  **inconclusivo** (conexão, timeout, descoberta `.well-known`, rate
  limit 429 e 5xx do token endpoint); `6` credencial **não apta** —
  rejeição determinística (HTTP 400/401 `invalid_client` do servidor
  de autorização ou certificado de cliente rejeitado no handshake
  mTLS). A classificação é fail-safe: falha não distinguível com
  certeza determinística classifica como `4`, nunca como `6`.
  Automação que testava `$? -eq 1` deve passar a tratar `3`/`4`/`6`.
  A classificação vive no novo colaborador `ClassificadorFalhas`; a
  tabela de códigos consta no README e no `--help`.

## Links

- [Repositório](https://github.com/FabricaDeSoftwareINF/server-hubsaude)
- [Issue #813 — access token completo no stdout](https://github.com/FabricaDeSoftwareINF/server-hubsaude/issues/813)
- [Issue #817 — taxonomia de códigos de saída](https://github.com/FabricaDeSoftwareINF/server-hubsaude/issues/817)
