package io.anuke.mindustry.game.griefprevention;

import java.lang.ref.WeakReference;

import io.anuke.mindustry.entities.type.Player;

public class PlayerStats {
    // don't prevent garbage collection
    public WeakReference<Player> player;

    public PlayerStats(Player player) {
        this.player = new WeakReference<>(player);
    }
}
