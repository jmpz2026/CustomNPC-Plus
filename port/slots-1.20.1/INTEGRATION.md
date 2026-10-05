# Integración — parches en código de BetaZavr (rama `1.20.1`)

Solo **3 archivos** de BetaZavr se tocan. Todo lo demás son archivos nuevos
(este folder). Anclas verificadas contra el source real.

---

## 1. `noppes/npcs/controllers/data/PlayerData.java`  ← ÚNICO cambio obligatorio

Añade el campo que recuerda el slot activo + persistirlo.

**a) Campo** (junto a `companionID`, ~línea 69):
```java
public int profileSlot = 0;   // slot system
```

**b) En `getNBT()`** — después de:
```java
compound.putInt("PlayerCompanionId", companionID);
```
añade:
```java
compound.putInt("ProfileSlot", profileSlot);
```

**c) En `setNBT(CompoundTag data)`** — después de:
```java
companionID = data.getInt("PlayerCompanionId");
```
añade:
```java
profileSlot = data.getInt("ProfileSlot");
```

> Nota: el swap de slot guarda este valor dentro de cada slot, pero
> `ProfileController.loadSlotData()` lo sobrescribe al `slotId` correcto justo
> después de cargar, así que siempre queda consistente (igual que el original).

---

## 2. `noppes/npcs/CustomNpcs.java`  ← registro

**a) Crear el controller** — en el `@SubscribeEvent` donde se instancian los
controllers (junto a `new PlayerDataController();`, ~línea 334):
```java
new ProfileController();
```

**b) Registrar el comando** — en `registerCommand(RegisterCommandsEvent e)`
(~línea 428):
```java
e.getDispatcher().register(CmdProfile.register());
```

**c) Registrar el handler de login/logout** — donde se hacen los
`MinecraftForge.EVENT_BUS.register(...)` (~línea 301):
```java
MinecraftForge.EVENT_BUS.register(new ProfileEventHandler());
```

**Imports** a añadir en `CustomNpcs.java`:
```java
import noppes.npcs.command.CmdProfile;
import noppes.npcs.controllers.ProfileController;
import noppes.npcs.controllers.data.profile.ProfileEventHandler;
```

---

## 3. (opcional) `noppes/npcs/command/CmdNoppes.java`

Si prefieres `/noppes profile ...` (admin) en vez del top-level `/profile`,
añade en `register(...)`:
```java
.then(CmdProfile.register())
```
en vez del registro directo del paso 2b.

---

## Archivos nuevos a copiar al árbol de BetaZavr

| Origen (este folder) | Destino en su repo |
|---|---|
| `Profile/Slot/IProfileData/CNPCData/ProfileOperation/EnumProfileOperation/ProfileInfoEntry/ProfileConfig/ProfileEvents/ProfileQuestSync/ProfileEventHandler.java` | `src/main/java/noppes/npcs/controllers/data/profile/` |
| `ProfileController.java` | `src/main/java/noppes/npcs/controllers/` |
| `CmdProfile.java` | `src/main/java/noppes/npcs/command/` |

## Config
`ProfileConfig` es standalone. Para exponerlo en su config, cablea sus campos
en su `ConfigLoader`/`ConfigMain` (o déjalo con defaults).
