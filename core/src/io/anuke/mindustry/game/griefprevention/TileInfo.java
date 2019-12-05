package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.collection.ObjectSet;
import io.anuke.mindustry.entities.type.Player;
import io.anuke.mindustry.world.Block;

public class TileInfo implements Cloneable {
    public Player constructedBy;
    // deconstructedBy ambiguously holds possibly either someone whp attempted to deconstruct
    // the current block or the person who deconstructed the previous block
    // TODO: implement full block history
    public Player deconstructedBy;
    public boolean constructSeen = false;
    public boolean deconstructSeen = false;
    public Block previousBlock;
    public Block currentBlock;
    public int configureCount = 0;
    public ObjectSet<Player> interactedPlayers = new ObjectSet<>();
    public Player lastRotatedBy;
    public TileInfo link;

    public TileInfo clone() {
        try {
            return (TileInfo)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("literally not possible");
        }
    }

    public void reset() {
        currentBlock = null;
        constructSeen = false;
        deconstructSeen = false;
        configureCount = 0;
        interactedPlayers.clear();
        lastRotatedBy = null;
        link = null;
    }

    public void doLink(TileInfo primary) {
        reset();
        constructedBy = null;
        link = primary;
    }

    public void unlink() {
        if (link == null) return;
        // this is called after previousBlock is set on primary
        previousBlock = link.previousBlock;
        constructedBy = link.constructedBy;
        deconstructedBy = link.deconstructedBy;
        reset();
    }
}
