package io.anuke.mindustry.game.griefprevention;

import io.anuke.arc.Core;
import io.anuke.arc.math.geom.Vector2;
import io.anuke.arc.scene.Element;
import io.anuke.arc.scene.event.Touchable;
import io.anuke.arc.scene.ui.layout.Table;
import io.anuke.mindustry.gen.Tex;
import io.anuke.mindustry.world.Tile;

import static io.anuke.mindustry.Vars.*;

public class TileInfoHud extends Table {
    private Tile lastTile = null;
    private String lastOutput = "No data";

    public TileInfoHud() {
        touchable(Touchable.disabled);
        background(Tex.pane);
        label(this::hudInfo);
    }

    public String hudInfo() {
        Vector2 vec = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY());
        Tile tile = world.tile(world.toTile(vec.x), world.toTile(vec.y));
        if (tile == lastTile) return lastOutput;
        if (tile == null) return lastOutput = "No data";
        return lastOutput = String.join("\n", griefWarnings.commandHandler.tileInfo(tile));
    }
}
