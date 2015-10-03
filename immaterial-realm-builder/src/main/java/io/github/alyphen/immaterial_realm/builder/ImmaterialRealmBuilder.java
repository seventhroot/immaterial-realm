package io.github.alyphen.immaterial_realm.builder;

import com.alee.laf.WebLookAndFeel;
import io.github.alyphen.immaterial_realm.builder.panel.*;
import io.github.alyphen.immaterial_realm.common.ImmaterialRealm;
import io.github.alyphen.immaterial_realm.common.object.WorldObject;
import io.github.alyphen.immaterial_realm.common.object.WorldObjectFactory;
import io.github.alyphen.immaterial_realm.common.object.WorldObjectInitializer;
import io.github.alyphen.immaterial_realm.common.sprite.Sprite;
import io.github.alyphen.immaterial_realm.common.tile.Tile;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import static io.github.alyphen.immaterial_realm.common.util.FileUtils.loadMetadata;
import static java.lang.Thread.sleep;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;

public class ImmaterialRealmBuilder extends JPanel implements Runnable {

    private static final long DELAY = 100L;

    private JFrame frame;

    private Logger logger;

    private MenuPanel menuPanel;
    private ChatDesignerPanel chatDesignerPanel;
    private LogViewerPanel logViewerPanel;
    private MapBuilderPanel mapBuilderPanel;
    private ObjectScripterPanel objectScripterPanel;
    private PluginsPanel pluginsPanel;
    private SettingsPanel settingsPanel;
    private TilesPanel tilesPanel;
    private SpritesPanel spritesPanel;

    public static void main(String[] args) {
        new ImmaterialRealmBuilder();
    }

    public ImmaterialRealmBuilder() {
        logger = Logger.getLogger(getClass().getName());
        ImmaterialRealm.getInstance().setLogger(logger);
        try {
            UIManager.setLookAndFeel(new WebLookAndFeel());
        } catch (UnsupportedLookAndFeelException exception) {
            getLogger().log(WARNING, "Failed to set look and feel", exception);
        }
        frame = new JFrame();
        setLayout(new CardLayout());
        loadData();
        menuPanel = new MenuPanel(this);
        add(menuPanel, "menu");
        chatDesignerPanel = new ChatDesignerPanel(this);
        add(chatDesignerPanel, "chat designer");
        logViewerPanel = new LogViewerPanel(this);
        add(logViewerPanel, "log viewer");
        mapBuilderPanel = new MapBuilderPanel(this);
        add(mapBuilderPanel, "map builder");
        objectScripterPanel = new ObjectScripterPanel(this);
        add(objectScripterPanel, "object scripter");
        pluginsPanel = new PluginsPanel(this);
        add(pluginsPanel, "plugins");
        settingsPanel = new SettingsPanel(this);
        add(settingsPanel, "settings");
        tilesPanel = new TilesPanel(this);
        add(tilesPanel, "tiles");
        spritesPanel = new SpritesPanel(this);
        add(spritesPanel, "sprites");
        new Thread(this).start();

        frame.setTitle("ImmaterialRealm Builder");
        frame.setFocusable(true);
        frame.add(this);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        EventQueue.invokeLater(() -> {
            frame.setVisible(true);
            frame.requestFocus();
        });
    }

    public Logger getLogger() {
        return logger;
    }

    public void showPanel(String panel) {
        CardLayout layout = (CardLayout) getLayout();
        layout.show(this, panel);
        frame.pack();
    }

    public void loadData() {
        try {
            Tile.loadTiles();
        } catch (IOException exception) {
            getLogger().log(SEVERE, "Failed to load tiles", exception);
        }
        try {
            Sprite.loadSprites();
        } catch (IOException exception) {
            getLogger().log(SEVERE, "Failed to load sprites", exception);
        }
        loadObjectTypes();
    }

    private void loadObjectTypes() {
        File objectsDirectory = new File("./objects");
        for (File objectDirectory : objectsDirectory.listFiles(File::isDirectory)) {
            try {
                File propertiesFile = new File(objectDirectory, "object.json");
                Map<String, Object> properties = loadMetadata(propertiesFile);
                WorldObjectFactory.registerObjectInitializer((String) properties.get("name"), new WorldObjectInitializer() {

                    {
                        setObjectName((String) properties.get("name"));
                        String spriteName = (String) properties.get("sprite");
                        Sprite sprite = spriteName.equals("none") ? null : Sprite.getSprite(spriteName);
                        setObjectSprite(sprite);
                        setObjectBounds(new Rectangle((int) ((double) properties.get("bounds_offset_x")), (int) ((double) properties.get("bounds_offset_y")), (int) ((double) properties.get("bounds_width")), (int) ((double) properties.get("bounds_height"))));
                    }

                    @Override
                    public WorldObject initialize(long id) {
                        return new WorldObject(id, getObjectName(), getObjectSprite(), getObjectBounds());
                    }

                });
            } catch (IOException exception) {
                getLogger().log(SEVERE, "Failed to load object type: " + objectDirectory.getName(), exception);
            }

        }
    }

    public void doTick() {
        if (mapBuilderPanel.isVisible()) mapBuilderPanel.onTick();
    }

    @Override
    public void run() {
        long beforeTime, timeDiff, sleep;
        beforeTime = System.currentTimeMillis();
        while (true) {
            doTick();
            repaint();
            timeDiff = System.currentTimeMillis() - beforeTime;
            sleep = DELAY - timeDiff;
            if (sleep < 0) {
                sleep = 2;
            }
            try {
                sleep(sleep);
            } catch (InterruptedException exception) {
                getLogger().log(SEVERE, "Thread interrupted", exception);
            }
            beforeTime = System.currentTimeMillis();
        }
    }

    public JFrame getFrame() {
        return frame;
    }

    public MenuPanel getMenuPanel() {
        return menuPanel;
    }

    public ChatDesignerPanel getChatDesignerPanel() {
        return chatDesignerPanel;
    }

    public LogViewerPanel getLogViewerPanel() {
        return logViewerPanel;
    }

    public MapBuilderPanel getMapBuilderPanel() {
        return mapBuilderPanel;
    }

    public ObjectScripterPanel getObjectScripterPanel() {
        return objectScripterPanel;
    }

    public PluginsPanel getPluginsPanel() {
        return pluginsPanel;
    }

    public SettingsPanel getSettingsPanel() {
        return settingsPanel;
    }

    public TilesPanel getTilesPanel() {
        return tilesPanel;
    }

    public SpritesPanel getSpritesPanel() {
        return spritesPanel;
    }

}
