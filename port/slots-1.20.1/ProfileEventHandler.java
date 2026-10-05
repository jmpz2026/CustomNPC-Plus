package noppes.npcs.controllers.data.profile;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.controllers.ProfileController;

/**
 * Wires player join/leave to the slot system. Register on the Forge bus:
 *     MinecraftForge.EVENT_BUS.register(new ProfileEventHandler());
 */
public class ProfileEventHandler {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() != null && ProfileController.Instance != null)
            ProfileController.Instance.login(event.getEntity());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null && ProfileController.Instance != null)
            ProfileController.Instance.logout(event.getEntity());
    }
}
