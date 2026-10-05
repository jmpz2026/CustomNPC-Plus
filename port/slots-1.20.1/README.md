# Slot system — port a BetaZavr CustomNPCs-Unofficial 1.20.1

Sistema de Profile/Slots portado a la rama `1.20.1` de BetaZavr. Conserva los
puntos **1–10**; solo se dejaron de lado **Party**, **AttributeController** y
`ProfileSlotRequirement` (acoplados a Attributes).

> ⚠️ No probado/compilado contra la base de BetaZavr (no la tengo local).
> Escrito contra su API real verificada (PlayerData, SyncController,
> PlayerDataController, CustomNpcsPermissions, EventHooks, QuestController).
> Paquete `noppes.npcs.*` para que caiga directo en su árbol.

## Archivos

| Archivo | Estado |
|---|---|
| `Profile`, `Slot`, `IProfileData`, `ProfileOperation`, `EnumProfileOperation`, `ProfileInfoEntry` | port limpio |
| `CNPCData` | adapter — port casi 1:1 (`PlayerData.get/getNBT/setNBT/updateClient`) |
| `ProfileController` | port completo de puntos 1–10 |
| `ProfileConfig` | config standalone (cablear a su ConfigLoader) |
| `ProfileEvents` | **hook punto 5** (no-op + TODO) |
| `ProfileQuestSync` | **hook punto 6** (no-op + TODO) |

## Estado por punto

| # | Punto | Estado en este port |
|---|---|---|
| 1 | Regiones de switch | ✅ portado (dim como `ResourceLocation` string; bypass = op `hasPermissions(2)`) |
| 2 | Límite de slots | ✅ plano `DefaultProfileSlots`. ⚠️ los tiers `max.<n>` por permiso NO se portan (1.20.1 usa `PermissionNode` registrados, no nodos string). TODO: 1 nodo bypass |
| 3 | Backups + rollback | ✅ portado (JSON, rotación) |
| 4 | Ops offline | ✅ portado vía `PlayerDataController.hasPlayer()` (sin HTTP Mojang) |
| 5 | EventHooks script | ⚠️ scaffold `ProfileEvents` (no-op). BetaZavr no tiene eventos de profile; hay que añadirlos a su API |
| 6 | Quest compartida entre slots | ⚠️ scaffold `ProfileQuestSync` (no-op). No existe `sharedQuests` y `finishedQuests` es `protected` |
| 7 | Clone + slots temporales | ✅ portado (ids negativos, no persisten) |
| 8 | Info GUI | ✅ `ProfileInfoEntry` + `getProfileInfo`. ⚠️ solo `activeQuests` (público); finished/dialogs necesitan getters en sus data classes |
| 9 | Save async | ✅ vía `CustomNPCsScheduler.runTack` (su propio scheduler) |
| 10 | Comandos | ❌ aparte — reescribir en **Brigadier** (`/profile change|create|remove`) |

## Diferencias de API aplicadas (1.7.10 → 1.20.1)

`EntityPlayer`→`Player` · `EntityPlayerMP`→`ServerPlayer` ·
`NBTTagCompound`→`CompoundTag` · `func_150296_c()`→`getAllKeys()` ·
`hasNoTags()`→`isEmpty()` · `setTag/getCompoundTag`→`put/getCompound` ·
`CompressedStreamTools(.dat)`→`NBTJsonUtil.SaveFile/LoadFile(.json)` ·
`getWorldSaveDirectory()`→`getLevelSaveDirectory("profiles")` ·
`save()`→`save(false)` · `SyncController.syncPlayerData`→`SyncController.syncPlayer` ·
op: `func_152596_g`→`hasPermissions(2)` · server: `ServerLifecycleHooks.getCurrentServer()`.

## Pendiente de integración (orden sugerido)

1. **`PlayerData.profileSlot`** — añadir el campo `int profileSlot` a la
   `PlayerData` de BetaZavr (+ persistirlo en get/setNBT). El slot usa esto para
   recordar el slot activo. **Es el único cambio obligatorio en su clase.**
2. **Eventos login/logout** — enganchar `ProfileController.Instance.login/logout`
   a `PlayerEvent.PlayerLoggedIn/OutEvent` (Forge 1.20.1).
3. **Registrar** `new ProfileController()` al arrancar el mod (como sus otros controllers).
4. **Comando** Brigadier `/profile` (punto 10).
5. (Opcional) cablear puntos 5 y 6 si quieres eventos de script + quest compartida.
6. (Opcional) getters públicos en `PlayerQuestData`/`PlayerDialogData` para el info del punto 8.
