package io.github.alyphen.immaterial_realm.server;

import io.github.alyphen.immaterial_realm.common.ImmaterialRealm;
import io.github.alyphen.immaterial_realm.common.encrypt.EncryptionManager;
import io.github.alyphen.immaterial_realm.common.entity.Entity;
import io.github.alyphen.immaterial_realm.common.log.FileWriterHandler;
import io.github.alyphen.immaterial_realm.common.object.WorldObject;
import io.github.alyphen.immaterial_realm.common.object.WorldObjectFactory;
import io.github.alyphen.immaterial_realm.common.object.WorldObjectInitializer;
import io.github.alyphen.immaterial_realm.common.packet.clientbound.entity.PacketEntityMove;
import io.github.alyphen.immaterial_realm.common.sprite.Sprite;
import io.github.alyphen.immaterial_realm.common.tile.Tile;
import io.github.alyphen.immaterial_realm.common.world.World;
import io.github.alyphen.immaterial_realm.server.admin.tpsmonitor.TPSMonitorFrame;
import io.github.alyphen.immaterial_realm.server.character.CharacterComponentManager;
import io.github.alyphen.immaterial_realm.server.character.CharacterManager;
import io.github.alyphen.immaterial_realm.server.chat.ChatManager;
import io.github.alyphen.immaterial_realm.server.database.DatabaseManager;
import io.github.alyphen.immaterial_realm.server.event.EventManager;
import io.github.alyphen.immaterial_realm.server.event.entity.EntityMoveEvent;
import io.github.alyphen.immaterial_realm.server.hud.HUDManager;
import io.github.alyphen.immaterial_realm.server.network.NetworkManager;
import io.github.alyphen.immaterial_realm.server.plugin.PluginManager;

import javax.script.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

import static io.github.alyphen.immaterial_realm.common.util.FileUtils.loadMetadata;
import static io.github.alyphen.immaterial_realm.common.util.FileUtils.read;
import static java.nio.file.Files.copy;
import static java.nio.file.Paths.get;
import static java.util.logging.Level.SEVERE;

public class ImmaterialRealmServer {

    private CharacterComponentManager characterComponentManager;
    private CharacterManager characterManager;
    private ChatManager chatManager;
    private DatabaseManager databaseManager;
    private EncryptionManager encryptionManager;
    private EventManager eventManager;
    private HUDManager hudManager;
    private NetworkManager networkManager;
    private PluginManager pluginManager;
    private ScriptEngineManager scriptEngineManager;
    private Map<String, Object> configuration;
    private Logger logger;
    private boolean running;
    private static final long DELAY = 25L;
    private int tps;
    private Deque<Integer> previousTPSValues;
    private TPSMonitorFrame tpsMonitorFrame;

    public static void main(String[] args) {
        new ImmaterialRealmServer(39752);
    }

    public ImmaterialRealmServer(int port) {
        logger = Logger.getLogger(getClass().getName());
        logger.addHandler(new FileWriterHandler());
        ImmaterialRealm.getInstance().setLogger(logger);
        scriptEngineManager = new ScriptEngineManager();
        try {
            databaseManager = new DatabaseManager();
        } catch (SQLException exception) {
            logger.log(SEVERE, "Failed to connect to database", exception);
        }
        characterComponentManager = new CharacterComponentManager(this);
        characterManager = new CharacterManager(this);
        chatManager = new ChatManager(this);
        encryptionManager = new EncryptionManager();
        networkManager = new NetworkManager(this, port);
        hudManager = new HUDManager(this);
        eventManager = new EventManager(this);
        pluginManager = new PluginManager(this);
        try {
            saveDefaults();
        } catch (IOException exception) {
            getLogger().log(SEVERE, "Failed to save defaults", exception);
        }
        loadConfiguration();
        try {
            Tile.loadTiles();
        } catch (IOException exception) {
            getLogger().log(SEVERE, "Failed to load tiles", exception);
        }
        File objectsDirectory = new File("./objects");
        for (File objectDirectory : objectsDirectory.listFiles(File::isDirectory)) {
            try {
                File propertiesFile = new File(objectDirectory, "object.json");
                Map<String, Object> properties = loadMetadata(propertiesFile);
                WorldObjectFactory.registerObjectInitializer((String) properties.get("name"), new WorldObjectInitializer() {

                    private CompiledScript script;

                    {
                        setObjectName((String) properties.get("name"));
                        String spriteName = (String) properties.get("sprite");
                        setObjectSprite(spriteName.equals("none") ? null : Sprite.getSprite(spriteName));
                        setObjectBounds(new Rectangle((int) ((double) properties.get("bounds_offset_x")), (int) ((double) properties.get("bounds_offset_y")), (int) ((double) properties.get("bounds_width")), (int) ((double) properties.get("bounds_height"))));
                        File jsFile = new File(objectDirectory, "object.js");
                        File rbFile = new File(objectDirectory, "object.rb");
                        File pyFile = new File(objectDirectory, "object.py");
                        if (jsFile.exists()) {
                            try {
                                ScriptEngine engine = getScriptEngineManager().getEngineByExtension("js");
                                script = ((Compilable) engine).compile(read(jsFile));
                                script.eval();
                            } catch (ScriptException | FileNotFoundException exception) {
                                getLogger().log(SEVERE, "Failed to compile script", exception);
                            }
                        } else if (rbFile.exists()) {
                            try {
                                ScriptEngine engine = getScriptEngineManager().getEngineByExtension("rb");
                                script = ((Compilable) engine).compile(read(rbFile));
                                script.eval();
                            } catch (ScriptException | FileNotFoundException exception) {
                                getLogger().log(SEVERE, "Failed to compile script", exception);
                            }
                        } else if (pyFile.exists()) {
                            try {
                                ScriptEngine engine = getScriptEngineManager().getEngineByExtension("py");
                                script = ((Compilable) engine).compile(read(pyFile));
                                script.eval();
                            } catch (ScriptException | FileNotFoundException exception) {
                                getLogger().log(SEVERE, "Failed to compile script", exception);
                            }
                        }
                    }

                    @Override
                    public WorldObject initialize(long id) {
                        return new WorldObject(id, getObjectName(), getObjectSprite(), getObjectBounds()) {

                            {
                                File jsFile = new File(objectDirectory, "object.js");
                                File rbFile = new File(objectDirectory, "object.rb");
                                File pyFile = new File(objectDirectory, "object.py");
                                if (jsFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("js");
                                        ((Invocable) engine).invokeFunction("create");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object creation function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                } else if (rbFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("rb");
                                        ((Invocable) engine).invokeFunction("create");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object creation function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                } else if (pyFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("py");
                                        ((Invocable) engine).invokeFunction("create");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object creation function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                }
                            }

                            @Override
                            public void onInteract() {
                                File jsFile = new File(objectDirectory, "object.js");
                                File rbFile = new File(objectDirectory, "object.rb");
                                File pyFile = new File(objectDirectory, "object.py");
                                if (jsFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("js");
                                        ((Invocable) engine).invokeFunction("interact");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object interaction function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                } else if (rbFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("rb");
                                        ((Invocable) engine).invokeFunction("interact");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object interaction function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                } else if (pyFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("py");
                                        ((Invocable) engine).invokeFunction("interact");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object interaction function");
                                    } catch (NoSuchMethodException ignored) {}
                                }
                            }

                            @Override
                            public void onTick() {
                                super.onTick();
                                File jsFile = new File(objectDirectory, "object.js");
                                File rbFile = new File(objectDirectory, "object.rb");
                                File pyFile = new File(objectDirectory, "object.py");
                                if (jsFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("js");
                                        ((Invocable) engine).invokeFunction("tick");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object tick function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                } else if (rbFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("rb");
                                        ((Invocable) engine).invokeFunction("tick");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object tick function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                } else if (pyFile.exists()) {
                                    try {
                                        ScriptEngine engine = getScriptEngineManager().getEngineByExtension("py");
                                        ((Invocable) engine).invokeFunction("tick");
                                    } catch (ScriptException exception) {
                                        getLogger().log(SEVERE, "Failed to invoke object tick function", exception);
                                    } catch (NoSuchMethodException ignored) {}
                                }
                            }

                        };
                    }

                });
            } catch (IOException exception) {
                getLogger().log(SEVERE, "Failed to load object metadata", exception);
            }

        }
        File worldDirectory = new File("./worlds");
        for (File file : worldDirectory.listFiles(File::isDirectory)) {
            try {
                World.load(file);
            } catch (IOException | ClassNotFoundException exception) {
                getLogger().log(SEVERE, "Failed to load world", exception);
            }
        }
        new Thread(networkManager::start).start();
        setupAdminTools();
    }

    private void setupAdminTools() {
        previousTPSValues = new ConcurrentLinkedDeque<>();
        if (!GraphicsEnvironment.isHeadless()) {
            setupTPSMonitor();
        }
    }

    private void setupTPSMonitor() {
        tpsMonitorFrame = new TPSMonitorFrame(this);
        EventQueue.invokeLater(() -> tpsMonitorFrame.setVisible(true));
    }

    public CharacterComponentManager getCharacterComponentManager() {
        return characterComponentManager;
    }

    public CharacterManager getCharacterManager() {
        return characterManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EncryptionManager getEncryptionManager() {
        return encryptionManager;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public HUDManager getHUDManager() {
        return hudManager;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }

    public ScriptEngineManager getScriptEngineManager() {
        return scriptEngineManager;
    }

    private void saveDefaults() throws IOException {
        saveDefaultConfiguration();
        saveDefaultTiles();
        saveDefaultObjectTypes();
        try {
            saveDefaultWorlds();
        } catch (URISyntaxException exception) {
            getLogger().log(SEVERE, "Failed to save default worlds", exception);
        }
    }

    private void saveDefaultConfiguration() throws IOException {
        File configDir = new File("./config");
        if (!configDir.exists()) configDir.mkdirs();
        File configFile = new File(configDir, "server.json");
        if (!configFile.exists()) {
            copy(getClass().getResourceAsStream("/server.json"), get(configFile.getPath()));
        }
    }

    private void loadConfiguration() {
        try {
            saveDefaultConfiguration();
        } catch (IOException exception) {
            getLogger().log(SEVERE, "Failed to save default configuration", exception);
        }
        File configDir = new File("./config");
        File configFile = new File(configDir, "server.json");
        if (configFile.exists()) {
            try {
                configuration = loadMetadata(configFile);
            } catch (IOException exception) {
                getLogger().log(SEVERE, "Failed to load configuration", exception);
                configuration = new HashMap<>();
            }
        } else {
            configuration = new HashMap<>();
        }
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    private void saveDefaultTiles() throws IOException {
        File tilesDirectory = new File("./tiles");
        String[] defaultTiles = new String[] {
                "grass1",
                "grass2",
                "grass3",
                "grass4",
                "water"
        };
        for (String tileName : defaultTiles) {
            File tileDirectory = new File(tilesDirectory, tileName);
            if (!tileDirectory.isDirectory()) {
                tileDirectory.delete();
            }
            if (!tileDirectory.exists()) {
                tileDirectory.mkdirs();
                copy(getClass().getResourceAsStream("/tiles/" + tileName + "/tile.png"), get(new File(tileDirectory, "tile.png").getPath()));
                copy(getClass().getResourceAsStream("/tiles/" + tileName + "/tile.json"), get(new File(tileDirectory, "tile.json").getPath()));
            }
        }
    }

    private void saveDefaultObjectTypes() throws IOException {
        File objectsDirectory = new File("./objects");
        if (!objectsDirectory.isDirectory()) {
            objectsDirectory.delete();
        }
        if (!objectsDirectory.exists()) {
            objectsDirectory.mkdirs();
        }
    }

    private void saveDefaultWorlds() throws IOException, URISyntaxException {
        File worldDirectory = new File("./worlds");
        File defaultWorldDirectory = new File(worldDirectory, "default");
        if (!worldDirectory.isDirectory()) {
            worldDirectory.delete();
        }
        if (!worldDirectory.exists()) {
            defaultWorldDirectory.mkdirs();
            copy(getClass().getResourceAsStream("/worlds/default/world.json"), get(new File(defaultWorldDirectory, "world.json").getPath()));
            File defaultWorldAreasDirectory = new File(defaultWorldDirectory, "areas");
            File defaultWorldDefaultAreaDirectory = new File(defaultWorldAreasDirectory, "default");
            defaultWorldDefaultAreaDirectory.mkdirs();
            copy(getClass().getResourceAsStream("/worlds/default/areas/default/area.json"), get(new File(defaultWorldDefaultAreaDirectory, "area.json").getPath()));
        }
    }

    public void run() {
        setRunning(true);
        long beforeTime, timeDiff, sleep;
        beforeTime = System.currentTimeMillis();
        while (isRunning()) {
            doTick();
            timeDiff = System.currentTimeMillis() - beforeTime;
            sleep = DELAY - timeDiff;
            if (sleep < 0) {
                sleep = 2;
            }
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException exception) {
                getLogger().log(SEVERE, "Thread interrupted", exception);
            }
            tps = (int) (1000 / (System.currentTimeMillis() - beforeTime));
            if (previousTPSValues.size() > 640) previousTPSValues.removeFirst();
            previousTPSValues.addLast(tps);
            beforeTime = System.currentTimeMillis();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void doTick() {
        World.getWorlds().stream().forEach(world -> {
            world.getAreas().stream().forEach(area -> {
                Set<Entity> entitiesToRemove = new HashSet<>();
                area.getEntities().stream().forEach(entity -> {
                    EntityMoveEvent event = new EntityMoveEvent(entity, area, entity.getX() + entity.getHorizontalSpeed(), entity.getY() + entity.getVerticalSpeed());
                    getEventManager().onEvent(event);
                    if (event.isCancelled()) {
                        entity.setMovementCancelled(true);
                    } else {
                        if (area != event.getNewArea()) {
                            entitiesToRemove.add(entity);
                            event.getNewArea().addEntity(entity);
                            entity.setForceUpdate(true);
                        }
                        if (entity.getX() + entity.getHorizontalSpeed() != event.getNewX()) {
                            entity.setX(event.getNewX() - entity.getHorizontalSpeed());
                            entity.setForceUpdate(true);
                        }
                        if (entity.getY() + entity.getVerticalSpeed() != event.getNewY()) {
                            entity.setY(event.getNewY() - entity.getVerticalSpeed());
                            entity.setForceUpdate(true);
                        }
                    }
                });
                entitiesToRemove.forEach(area::removeEntity);
            });
            world.onTick();
            world.getAreas().stream().forEach(area -> area.getEntities().stream().filter(entity -> entity.isSpeedChanged() || entity.isMovementCancelled() || entity.isForceUpdate()).forEach(entity -> getNetworkManager().broadcastPacket(new PacketEntityMove(entity.getId(), entity.getDirectionFacing(), area.getName(), entity.getX(), entity.getY(), entity.getHorizontalSpeed(), entity.getVerticalSpeed()))));
        });
        if (tpsMonitorFrame != null) {
            tpsMonitorFrame.repaint();
        }
    }

    public Logger getLogger() {
        return logger;
    }

    public int getTPS() {
        return tps;
    }

    public Deque<Integer> getPreviousTPSValues() {
        return previousTPSValues;
    }
}
