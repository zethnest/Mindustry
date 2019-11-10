package io.anuke.mindustry.game;

import io.anuke.arc.Events;
import io.anuke.arc.collection.Array;
import io.anuke.arc.math.Mathf;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.entities.type.Unit;
import io.anuke.mindustry.game.EventType.DepositEvent;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.power.ItemLiquidGenerator;
import io.anuke.mindustry.world.blocks.power.NuclearReactor;

import static io.anuke.mindustry.Vars.*;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.WeakHashMap;

public class GriefWarnings {
    public class PlayerStats {
        // don't prevent garbage collection
        public WeakReference<Player> player;

        public PlayerStats(Player player) {
            this.player = new WeakReference<>(player);
        }
    }
    private Instant nextWarningTime = Instant.now();
    public WeakHashMap<Player, PlayerStats> playerStats = new WeakHashMap<>();

    public GriefWarnings() {
        Events.on(DepositEvent.class, this::handleDeposit);
        // Events.on(PlayerJoin.class, this::handlePlayerJoin);
        // Events.on(StateChangeEvent.class, this::handleStateChange);
    }

    public boolean sendMessage(String message) {
        // if (!net.active()) return false;
        if (message.length() > maxTextLength) {
            ui.chatfrag.addMessage(
                    "[scarlet]WARNING: the following grief warning exceeded maximum allowed chat length and was not sent",
                    null);
            ui.chatfrag.addMessage(message, null);
            ui.chatfrag.addMessage("Message length was [accent]" + message.length(), null);
            return false;
        }
        if (!Instant.now().isAfter(nextWarningTime))
            return false;
        nextWarningTime = Instant.now().plusSeconds(1);
        Call.sendChatMessage(message);
        return true;
    }

    public float getDistanceToCore(Unit unit, float x, float y) {
        Tile nearestCore = unit.getClosestCore().getTile();
        return Mathf.dst(x, y, nearestCore.x, nearestCore.y);
    }

    public float getDistanceToCore(Unit unit, Tile tile) {
        return getDistanceToCore(unit, tile.x, tile.y);
    }

    public float getDistanceToCore(Unit unit) {
        return getDistanceToCore(unit, unit.x, unit.y);
    }

    public void handleBlockConstruction(Player builder, Tile tile, Block cblock, float progress) {
        float coreDistance = getDistanceToCore(builder, tile);
        // i don't see any better way to do this
        // using instanceof to match an entire class of blocks
        if (coreDistance < 50 && cblock instanceof NuclearReactor) {
            String message = "[scarlet]WARNING[] " + builder.name + "[white] ([stat]#" + builder.id
                    + "[]) is building a reactor [stat]" + Math.round(coreDistance) + "[] blocks from core. [stat]"
                    + Math.round(progress * 100) + "%";
            sendMessage(message);
            return;
        } else if (coreDistance < 10 && cblock instanceof ItemLiquidGenerator) {
            String message = "[scarlet]WARNING[] " + builder.name + "[white] ([stat]#" + builder.id
                    + "[]) is building a generator [stat]" + Math.round(coreDistance) + "[] blocks from core. [stat]"
                    + Math.round(progress * 100) + "%";
            sendMessage(message);
            return;
        }
    }

    public Player getNearestPlayerByLocation(float x, float y) {
        // grab every player in a 10x10 area and then find closest
        Array<Player> candidates = playerGroup.intersect(x - 5, y - 5, 10, 10);
        if (candidates.size == 0) return null;
        if (candidates.size == 1) return candidates.first();
        if (candidates.size > 1) {
            float nearestDistance = Float.MAX_VALUE;
            Player nearestPlayer = null;
            for (Player player : candidates) {
                float distance = Mathf.dst(x, y, player.x, player.y);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
            return nearestPlayer;
        }
        return null; // this should be impossible
    }

    public void handleDeposit(DepositEvent event) {
        Player targetPlayer = event.player;
        Tile tile = event.tile;
        Item item = event.item;
        int amount = event.amount;
        if (targetPlayer == null) return;
        // sendMessage("[cyan]Debug[] " + targetPlayer.name + "[white] transfers " + amount + " " + item.name + " to " + tile.block().name + " (" + tile.x + ", " + tile.y + ")");
        if (item.equals(Items.thorium) && tile.block() instanceof NuclearReactor) {
            String message = "[scarlet]WARNING[] " + targetPlayer.name + "[white] ([stat]#" +
                targetPlayer.id + "[]) transfers [accent]" + amount + "[] thorium to reactor. (" + tile.x + ", " + tile.y + ")";
            sendMessage(message);
            return;
        } else if (item.explosiveness > 0.5f && tile.block() instanceof ItemLiquidGenerator) {
            String message = "[scarlet]WARNING[] " + targetPlayer.name + "[white] ([stat]#" +
                targetPlayer.id + "[]) transfers [accent]" + amount + "[] blast to generator. (" + tile.x + ", " + tile.y + ")";
            sendMessage(message);
            return;
        }
    }

    /* this does not work
    public void createStatsForPlayer(Player player) {
        System.out.println("creating stats for " + player);
        playerStats.put(player, new PlayerStats(player));
    }

    public void handleStateChange(StateChangeEvent event) {
        if (event.from == State.menu && event.to == State.playing) {
            playerStats = new WeakHashMap<>();
            for (Player player : playerGroup.all()) createStatsForPlayer(player);
        }
    }

    public void handlePlayerJoin(PlayerJoin event) {
        createStatsForPlayer(event.player);
    }
    */
}
