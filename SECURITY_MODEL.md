# Modelo de seguridad — CustomNPC-Plus

Hereda: `dbr-seguridad/references/modelo-amenazas.md` (INV-G1..G9).
Última revisión: 2026-11-05

## Qué hace y qué protege

Fork de CustomNPC-Plus 1.7.10: NPCs, misiones, diálogos, perfiles de personaje.

## Entradas

| Entrada | Quién la dispara | Guarda | Archivo |
|---|---|---|---|
| Paquetes de perfil | cliente | permiso + opción de servidor | `network/packets/player/profile/` |
| Paquetes de NPC y scripts (upstream) | cliente | permisos de CustomNPC | `kamkeel/npcs/network/` |

## Invariantes propios

- **INV-1** — Crear, borrar o cambiar de slot no se decide en el cliente.
- **INV-2** — Editar scripts es solo del dueño.

## Datos y secretos

- Perfiles de personaje (`customnpcs/profiles/*.dat`), misiones, scripts.
- Secretos: ninguno.

## Dependencias de seguridad

- Forge 1.7.10, submódulo de API fijado a un commit.

## Hallazgos que importan acá

| Clase | Severidad base |
|---|---|
| Código arbitrario en el servidor | Crítica |
| Personajes perdidos o mezclados | Alta |

## Fuera de alcance

- Balance del juego.

## Riesgos aceptados

| Fecha | Riesgo | Por qué se acepta | Revisar |
|---|---|---|---|
| — | — | — | — |
