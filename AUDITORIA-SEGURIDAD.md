# Auditoría de seguridad — CustomNPC-Plus

Fecha: 2026-11-05 · Commit: `a3429a4` (rama `fork-1.10.4` del remoto `fork` (jmpz2026). El PC está en `ac87279`, **2 commits por detrás**, limpio: `git pull fork fork-1.10.4`) · Alcance: los 38 commits propios del fork sobre el upstream de KAMKEEL (idioma por jugador, diálogos, perfiles, música, rendimiento). El resto del mod (1 369 clases) es upstream y no se revisó línea a línea.
Herramientas: revisión manual (skill `dbr-seguridad`)

## Resumen

Sin hallazgos en el código propio. El cambio de seguridad que importa ya está hecho: los paquetes de perfil (crear, borrar y cambiar de slot) dejaron de fiarse del cliente y ahora exigen la opción `Player Slot Management` o el permiso de admin. El riesgo de verdad del mod está en su configuración: quien tenga la herramienta de scripts ejecuta código Java en el servidor.

| Severidad | Cantidad |
|---|---|
| Crítica | 0 |
| Alta | 0 |
| Media | 0 |
| Baja | 0 |

## Hallazgos

Ninguno.

## Variantes revisadas

- `ProfileCreatePacket`/`ProfileRemovePacket`/`ProfileChangePacket`: permiso en el servidor (`CustomNpcsPermissions.PROFILE_*`) y la clave `Player Slot Management` en `false`.
- Las traducciones por jugador solo cambian texto que el servidor manda.

## Lo que está bien

- El arreglo de perfiles está documentado en el commit `5ee395d` con el porqué.

## Pendiente de verificar

- **Scripts = código en el servidor.** Un script de NPC corre JavaScript con acceso a Java. Comprobar en LuckPerms que los permisos de CustomNPC de edición (`customnpcs.npc.*`, `customnpcs.global.*`, la herramienta de scripts) los tenga solo el dueño: con el staff no fiado (decisión F0), un staff con eso controla el servidor.
- El resto de paquetes cliente → servidor del upstream (≈200) no se revisó.
