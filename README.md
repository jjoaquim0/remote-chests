# RemoteChests

Plugin de Paper que deixa você registrar baús pelo chat e abrir qualquer um
deles de onde estiver no servidor.

```
/bau set estoque       # mirando no baú
/bau abrir estoque     # de qualquer lugar, em qualquer mundo
```

## Como funciona

**Não existe inventário virtual.** Quando você usa `/bau abrir`, o plugin
carrega o chunk do bloco real, pega o inventário daquele bloco e entrega esse
mesmo objeto para você. Ou seja, é literalmente o baú que está lá no mundo:

- funis, comparadores e outros plugins continuam funcionando normalmente;
- dois jogadores olhando o mesmo baú veem o mesmo conteúdo, ao vivo;
- baú duplo abre com 54 slots, sem gambiarra;
- nada precisa ser "sincronizado" depois — não há cópia para dessincronizar.

Enquanto alguém está com um baú remoto aberto, o plugin segura um
*chunk ticket* para o chunk não descarregar debaixo do inventário; o ticket é
liberado no `InventoryCloseEvent` (com contagem, caso várias pessoas estejam no
mesmo chunk).

## Comandos

| Comando | O que faz |
| --- | --- |
| `/bau set <nome>` | Registra o baú/barril/shulker que você está mirando |
| `/bau abrir <nome>` | Abre o baú remotamente (com tab-complete dos seus nomes) |
| `/bau lista` | Lista seus baús; clicar no nome abre |
| `/bau info <nome>` | Local, tipo, ocupação em slots, datas |
| `/bau renomear <nome> <novo>` | Troca o nome |
| `/bau remover <nome>` | Apaga o registro (o baú continua no mundo) |
| `/bau admin lista <jogador>` | Lista os baús de outra pessoa |
| `/bau admin recarregar` | Recarrega o `config.yml` |

Aliases: `/baus`, `/rchest`.

Nomes aceitam `A-Z a-z 0-9 _ -`, até 32 caracteres. A restrição a ASCII é de
propósito: o parser de argumentos do Brigadier exige aspas para qualquer outra
coisa, e `/bau abrir "meu baú"` é pior de digitar do que `/bau abrir meu-bau`.

## Permissões

| Permissão | Padrão | Efeito |
| --- | --- | --- |
| `remotechests.use` | todos | Registrar e abrir os próprios baús |
| `remotechests.unlimited` | op | Ignora o limite por jogador |
| `remotechests.admin` | op | Ver baús de outros, quebrar baús alheios, recarregar, sem cooldown |

## Configuração

O `config.yml` cobre limite por jogador, distância de mira, cooldown, se pode
abrir entre mundos, proteção contra quebra e explosão, e todas as mensagens
(em [MiniMessage](https://docs.advntr.dev/minimessage/format.html)).

Destaques:

- `protect-registered-blocks` — impede que outros quebrem um baú registrado. Se
  o próprio dono quebrar, o registro sai junto automaticamente.
- `auto-remove-missing` — se o bloco sumiu (worldedit, outro plugin), apaga o
  registro em vez de só avisar.
- `animate-real-block` — abre a tampa e toca o som no baú de verdade, para quem
  estiver por perto perceber o acesso.

## Compilando

Requer **JDK 25** — Minecraft 26.1+ exige. Se você não tiver, o Gradle baixa
sozinho (o `settings.gradle.kts` já traz o resolvedor de toolchain).

```bash
./gradlew build
# gera build/libs/RemoteChests-1.0.0.jar
```

No Windows use `gradlew.bat build`.

> **Confira a versão do Paper antes do primeiro build.** O Paper 26.x abandonou
> o formato `-R0.1-SNAPSHOT`; agora o artefato é `26.2.build.<n>-stable`. O
> `build.gradle.kts` está fixado em `26.2.build.123-stable`. Se der erro de
> dependência não encontrada, veja o build mais recente em
> <https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/>
> e troque a linha, ou use `"[26.2.build,)"` para pegar sempre o último.

## Instalando no servidor

1. Copie `build/libs/RemoteChests-1.0.0.jar` para a pasta `plugins/` do servidor.
2. Reinicie o servidor (reload não registra comandos Brigadier direito).
3. Confira no console que o plugin carregou e teste:

```
/bau set teste     # mirando num baú
/bau abrir teste   # ande pra longe e abra
```

O `config.yml` e o `chests.yml` aparecem em `plugins/RemoteChests/` no primeiro
start. Depois de mexer no `config.yml`, use `/bau admin recarregar`.

## Dados

Os registros ficam em `plugins/RemoteChests/chests.yml` — só coordenadas e
metadados, nunca o conteúdo dos baús:

```yaml
chests:
  3f2b...:
    name: estoque
    owner: 8c9d...
    owner-name: jjoaquim0
    world: world
    x: 128
    y: 63
    z: -412
    type: CHEST
    created-at: 1757880000000
    last-opened-at: 1757881234000
```

A gravação é atômica (escreve `.tmp` e move) e acontece fora da main thread.
Mudanças estruturais gravam na hora; "último acesso" é acumulado e gravado em
lote a cada minuto.

## Próximo passo: interface web

A arquitetura já foi pensada para isso, e o caminho mais curto é:

1. `ChestService.all()` é a fonte de verdade em memória, e `ChestAccessService.peek()`
   lê o conteúdo de um baú (carregando o chunk) sem abrir nada. Juntos eles já
   dão todo o conteúdo que uma API precisa expor.
2. Suba um `com.sun.net.httpserver.HttpServer` no `onEnable` (vem no JDK, zero
   dependência nova) servindo `/api/chests` e `/api/chests/{id}`. Como o Bukkit
   não é thread-safe, o handler HTTP precisa saltar para a main thread —
   `getScheduler().callSyncMethod(...)` e esperar o `Future` — antes de tocar em
   qualquer bloco ou inventário.
3. Autenticação: um token por jogador, gerado por comando e guardado junto do
   registro.
4. Se a consulta ficar pesada, `ChestStorage` é uma interface de propósito:
   trocar `YamlChestStorage` por SQLite/Postgres é uma linha no
   `RemoteChestsPlugin` e o front consulta o banco direto.
