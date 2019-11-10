package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.Events;
import io.anuke.arc.collection.Array;
import io.anuke.arc.math.Mathf;
import io.anuke.mindustry.content.Items;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.entities.type.Unit;
import io.anuke.mindustry.game.EventType.DepositEvent;
import io.anuke.mindustry.game.EventType.TileChangeEvent;
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

    public class TileInfo {
        public WeakReference<Player> constructedBy;
    }

    private Instant nextWarningTime = Instant.now();
    public WeakHashMap<Player, PlayerStats> playerStats = new WeakHashMap<>();
    // whether or not to be very noisy about everything
    public boolean verbose = false;
    public WeakHashMap<Tile, TileInfo> tileInfo = new WeakHashMap<>();

    public CommandHandler commandHandler = new CommandHandler();
    public FixGrief fixer = new FixGrief();

    public GriefWarnings() {
        Events.on(DepositEvent.class, this::handleDeposit);
        // Events.on(PlayerJoin.class, this::handlePlayerJoin);
        // Events.on(StateChangeEvent.class, this::handleStateChange);
        Events.on(TileChangeEvent.class, this::handleTileChange);
    }

    public boolean sendMessage(String message, boolean throttled) {
        // if (!net.active()) return false;
        if (message.length() > maxTextLength) {
            ui.chatfrag.addMessage(
                    "[scarlet]WARNING: the following grief warning exceeded maximum allowed chat length and was not sent",
                    null);
            ui.chatfrag.addMessage(message, null);
            ui.chatfrag.addMessage("Message length was [accent]" + message.length(), null);
            return false;
        }
        if (!Instant.now().isAfter(nextWarningTime) && throttled) return false;
        nextWarningTime = Instant.now().plusSeconds(1);
        Call.sendChatMessage(message);
        return true;
    }

    public boolean sendMessage(String message) {
        return sendMessage(message, true);
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

    public void handleTileChange(TileChangeEvent event) {
        tileInfo.remove(event.tile);
    }

    public void handleBlockConstructProgress(Player builder, Tile tile, Block cblock, float progress, Block previous) {
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

    public void handleBlockConstructFinish(Tile tile, Block block, int builderId) {
        TileInfo info = new TileInfo();
        Player targetPlayer = playerGroup.getByID(builderId);
        info.constructedBy = new WeakReference<Player>(targetPlayer);
        tileInfo.put(tile, info);

        if (verbose && targetPlayer != null) {
            sendMessage("[green]Verbose[] " + targetPlayer.name + "[white] ([stat]#" + builderId +
                "[]) builds [accent]" + tile.block().name + "[] at (" + tile.x + ", " + tile.y + ")", false);
        }
    }

    public void handleBlockDeconstructProgress(Player builder, Tile tile, Block cblock, float progress, Block previous) {
        // TODO: things will be here in the future
    }

    public void handleBlockDeconstructFinish(Tile tile, Block block, int builderId) {
        Player targetPlayer = playerGroup.getByID(builderId);
        if (verbose && targetPlayer != null) {
            sendMessage("[green]Verbose[] " + targetPlayer.name + "[white] ([stat]#" + builderId +
                "[]) deconstructs [accent]" + tile.block().name + "[] at (" + tile.x + ", " + tile.y + ")", false);
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
        if (verbose) {
            sendMessage("[green]Verbose[] " + targetPlayer.name + "[white] ([stat]#" + targetPlayer.id +
                "[]) transfers " + amount + " " + item.name + " to " + tile.block().name + " (" + tile.x + ", " + tile.y + ")", false);
        }
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
