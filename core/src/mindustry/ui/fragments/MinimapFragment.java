package mindustry.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.layout.*;
import arc.util.Log;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.ui.*;

import static mindustry.Vars.*;

public class MinimapFragment extends Fragment{
    private boolean shown;
    private float panx, pany, zoom = 1f, lastZoom = -1;
    private float baseSize = Scl.scl(5f);
    private Element elem;

    @Override
    public void build(Group parent){
        elem = parent.fill((x, y, w, h) -> {
            w = Core.graphics.getWidth();
            h = Core.graphics.getHeight();
            float size = baseSize * zoom * world.width();

            Draw.color(Color.black);
            Fill.crect(x, y, w, h);

            if(renderer.minimap.getTexture() != null){
                Draw.color();
                float ratio = (float)renderer.minimap.getTexture().getHeight() / renderer.minimap.getTexture().getWidth();
                TextureRegion reg = Draw.wrap(renderer.minimap.getTexture());
                Draw.rect(reg, w/2f + panx*zoom, h/2f + pany*zoom, size, size * ratio);
                renderer.minimap.drawEntities(w/2f + panx*zoom - size/2f, h/2f + pany*zoom - size/2f * ratio, size, size * ratio, zoom, true);
				Log.info("panx,pany: " + ((Float) panx).toString() + "," + ((Float) pany).toString());
				Log.info("world.toTile(player.{x,y}): " + ((Integer) world.toTile(player.x)).toString() + " , " + ((Integer) world.toTile(player.y)).toString());
				Log.info("size: " + ((Float) size).toString());
				Log.info("ratio: " + ((Float) ratio).toString());
				Log.info("size*ratio: " + ((Float) (size * ratio)).toString());
				Log.info("world.width(),world.height()" + ((Integer) world.width()).toString() + " , " + ((Integer) world.height()));
            }

            Draw.reset();
        });

        elem.visible(() -> shown);
        elem.update(() -> {
            elem.requestKeyboard();
            elem.requestScroll();
            elem.setFillParent(true);
            elem.setBounds(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());

            if(Core.input.keyTap(Binding.menu)){
                shown = false;
            }
        });
        elem.touchable(Touchable.enabled);

        elem.addListener(new ElementGestureListener(){

            @Override
            public void zoom(InputEvent event, float initialDistance, float distance){
                if(lastZoom < 0){
                    lastZoom = zoom;
                }

                zoom = Mathf.clamp(distance / initialDistance * lastZoom, 0.25f, 10f);
            }

            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY){
                panx += deltaX / zoom;
                pany += deltaY / zoom;
            }

            @Override
            public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                lastZoom = zoom;
            }
        });

        elem.addListener(new InputListener(){

            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                zoom = Mathf.clamp(zoom - amountY / 10f * zoom, 0.25f, 10f);
                return true;
            }
        });

        parent.fill(t -> {
            t.setFillParent(true);
            t.visible(() -> shown);
            t.update(() -> t.setBounds(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight()));

            t.add("$minimap").style(Styles.outlineLabel).pad(10f);
            t.row();
            t.add().growY();
            t.row();
            t.addImageTextButton("$back", Icon.leftOpen, () -> shown = false).size(220f, 60f).pad(10f);
        });
    }

    public boolean shown(){
        return shown;
    }

    public void toggle(){
		panx = -((float) world.toTile(player.x) - (world.width()/2)) * 3.5f;
		pany = -((float) world.toTile(player.y) - (world.height()/2)) * 3.5f;
        shown = !shown;
    }
}
