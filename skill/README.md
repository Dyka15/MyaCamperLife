# La skill

`android-offline-first/` è una skill di Claude Code: le regole imparate
costruendo questa app e Cicala, in una forma che un prossimo progetto può
caricare invece di riscoprire.

## Installarla

Per tutti i progetti, copiala nel profilo:

```bash
cp -r skill/android-offline-first ~/.claude/skills/
```

Per un solo progetto, nel repository di quel progetto:

```bash
mkdir -p .claude/skills && cp -r /percorso/skill/android-offline-first .claude/skills/
```

Si attiva da sé quando il lavoro riguarda un'app Android; si può richiamare a
mano con `/android-offline-first`.

## Cosa contiene

| | |
|---|---|
| `SKILL.md` | i quattro principi, il ciclo di lavoro, e le regole che sono costate un difetto ciascuna |
| `references/formato-file.md` | il contratto dei file: append-only, `id`/`ts`/`cancellato`, lapidi, fusione, specchio SAF |
| `references/verifica-senza-sdk.md` | come compilare e provare senza SDK Android, e come si segue la CI |
| `references/interfaccia-compose.md` | stato, dialoghi, layout, cosa spegnere invece di nascondere |
| `references/rete-e-guasti.md` | esiti tipizzati, servizi di cortesia, specchi, chiavi API |
| `references/sistema-android.md` | sveglie, HyperOS, versioni allineate, release firmata e R8 |
| `references/diagnosi-a-distanza.md` | diagnosticare un guasto che accade sul telefono di un altro |
| `scripts/controlli.py` | controlli statici: import, stringhe, XML |
| `scripts/armatura.sh` | crea e sincronizza il progetto JVM di verifica |

Entrambi gli script sono stati provati su questo repository: `controlli.py`
riporta 0 problemi, e `armatura.sh crea && sync && prova` costruisce l'armatura
da zero ed esegue le 618 prove del dominio e dell'archivio.
