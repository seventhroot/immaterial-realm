package io.github.alyphen.immaterial_realm.client.panel;

import io.github.alyphen.immaterial_realm.client.ImmaterialRealmClient;
import io.github.alyphen.immaterial_realm.client.chat.ChatBox;
import io.github.alyphen.immaterial_realm.client.menu.MenuBox;
import io.github.alyphen.immaterial_realm.common.entity.EntityCharacter;
import io.github.alyphen.immaterial_realm.common.hud.HUD;
import io.github.alyphen.immaterial_realm.common.tile.Tile;
import io.github.alyphen.immaterial_realm.common.world.World;
import io.github.alyphen.immaterial_realm.common.world.WorldArea;

import javax.swing.*;
import java.awt.*;

import static java.awt.Color.BLACK;

public class WorldPanel extends JPanel {

    private ImmaterialRealmClient client;

    private World world;
    private WorldArea area;
    private EntityCharacter playerCharacter;
    private ChatBox chatBox;
    private MenuBox menuBox;
    private HUD hud;

    public WorldPanel(ImmaterialRealmClient client) {
        this.client = client;
        chatBox = new ChatBox(client, this);
        menuBox = new MenuBox(client, this);
        hud = new HUD();
        client.getFrame().addKeyListener(chatBox);
    }

    public void onTick() {
        if (getWorld() != null) getWorld().onTick();
        if (getChatBox() != null) getChatBox().onTick();
        repaint();
    }

    @Override
    public void paintComponent(Graphics graphics) {
        graphics.setColor(BLACK);
        graphics.fillRect(0, 0, getWidth(), getHeight());
        if (getArea() != null && getPlayerCharacter() != null) {
            Graphics2D graphics2D = (Graphics2D) graphics;
            graphics2D.translate(-getCameraX(), -getCameraY());
            for (int row = 0; row < area.getRows(); row++) {
                for (int col = 0; col < area.getColumns(); col++) {
                    Tile tile = getArea().getTileAt(row, col);
                    int x = col * tile.getWidth();
                    int y = row * tile.getHeight();
                    if (x + tile.getWidth() >= getCameraX()
                            && x <= getCameraX() + getWidth()
                            && y + tile.getHeight() >= getCameraY()
                            && y <= getCameraY() + getHeight())
                        tile.paint(graphics, x, y);
                }
            }
            getArea().getObjects().stream().filter(object -> object.getX() + object.getBounds().getWidth() >= getCameraX()
                    && object.getX() <= getCameraX() + getWidth()
                    && object.getY() + object.getBounds().getHeight() >= getCameraY()
                    && object.getY() <= getCameraY() + getHeight()).forEach(object -> {
                graphics2D.translate(object.getX(), object.getY());
                object.paint(graphics);
                graphics2D.translate(-object.getX(), -object.getY());
            });
            getArea().getEntities().stream().filter(entity -> entity.getX() + entity.getBounds().getWidth() >= getCameraX()
                    && entity.getX() <= getCameraX() + getWidth()
                    && entity.getY() + entity.getBounds().getHeight() >= getCameraY()
                    && entity.getY() <= getCameraY() + getHeight()).forEach(entity -> {
                graphics2D.translate(entity.getX(), entity.getY());
                entity.paint(graphics);
                graphics2D.translate(-entity.getX(), -entity.getY());
            });
            graphics2D.translate(getCameraX(), getCameraY());
        }
        chatBox.paint(graphics);
        menuBox.paint(graphics);
        hud.paint(client.getScriptEngineManager(), graphics, getWidth(), getHeight());
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public WorldArea getArea() {
        return area;
    }

    public void setArea(WorldArea area) {
        world.addArea(area);
        if (this.area != null) world.removeArea(this.area);
        this.area = area;
    }

    public EntityCharacter getPlayerCharacter() {
        return playerCharacter;
    }

    public void setPlayerCharacter(EntityCharacter player) {
        this.playerCharacter = player;
    }

    private int getCameraX() {
        return getPlayerCharacter().getX() + ((getPlayerCharacter().getCharacter() != null && getPlayerCharacter().getCharacter().getWalkDownSprite() != null) ? getPlayerCharacter().getCharacter().getWalkDownSprite().getWidth() / 2 : 0) - (getWidth() / 2);
    }

    private int getCameraY() {
        return getPlayerCharacter().getY() + ((getPlayerCharacter().getCharacter() != null && getPlayerCharacter().getCharacter().getWalkDownSprite() != null) ? getPlayerCharacter().getCharacter().getWalkDownSprite().getHeight() / 2 : 0) - (getHeight() / 2);
    }

    public ChatBox getChatBox() {
        return chatBox;
    }

    public HUD getHUD() {
        return hud;
    }
}
