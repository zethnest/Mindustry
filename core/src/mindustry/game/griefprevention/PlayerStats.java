package mindustry.game.griefprevention;

import java.lang.ref.WeakReference;

import mindustry.entities.type.Player;

public class PlayerStats {
    // don't prevent garbage collection
    public WeakReference<Player> player;
    public Ratelimit rotateRatelimit = new Ratelimit(50, 1000);
    public Ratelimit configureRatelimit = new Ratelimit(50, 1000);

    public PlayerStats(Player player) {
        this.player = new WeakReference<>(player);
    }
}
