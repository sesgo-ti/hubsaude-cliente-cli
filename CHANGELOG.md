# Changelog

Todas as mudanças notáveis neste projeto serão documentadas neste arquivo.

O formato é baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.0.0/),
e este projeto adere ao [Versionamento Semântico](https://semver.org/lang/pt-BR/).

## [Unreleased]

### Alterado
- Dependência `hubsaude-cliente-java` atualizada de `0.3.29` para `0.4.0`.
  O CLI já utilizava exclusivamente `SmartTokenClient.builder()` e permanece
  compatível com a consolidação da API da biblioteca.
- **Higiene de credenciais no stdout:** o modo detalhado (sem
  `--quiet`) deixa de imprimir o access token completo por padrão —
  passa a exibir header/payload decodificados e a prévia truncada de
  50 caracteres, coerente com a redação `<REDACTED>` já aplicada à
  resposta do servidor de autorização. A nova flag `--show-token`
  restaura a exibição integral, mediante solicitação explícita.
  Mantém a exposição de credenciais como opção explícita.
- **Mudança de contrato (pré-1.0):** os códigos de saída de falha deixam
  de colapsar em `1` e passam a adotar uma taxonomia operacional:
  `0` sucesso (credencial
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

## Projeto

- [Repositório](https://github.com/sesgo-ti/hubsaude-cliente-cli)
- [SDK Java](https://github.com/sesgo-ti/hubsaude-cliente-java)
