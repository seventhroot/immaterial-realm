package io.github.alyphen.immaterial_realm.builder.panel;

import io.github.alyphen.immaterial_realm.builder.ImmaterialRealmBuilder;
import io.github.alyphen.immaterial_realm.common.tile.Tile;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.util.logging.Level.SEVERE;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

public class TilesPanel extends JPanel {

    public TilesPanel(ImmaterialRealmBuilder application) {
        setLayout(new BorderLayout());
        JTable tilesTable = new JTable(new DefaultTableModel(0, 5) {
            @Override
            public String getColumnName(int column) {
                switch (column) {
                    case 0: return "Name";
                    case 1: return "Image location";
                    case 2: return "Frame width";
                    case 3: return "Frame height";
                    case 4: return "Frame duration";
                    default: return super.getColumnName(column);
                }
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                switch (columnIndex) {
                    case 0: return String.class;
                    case 1: return File.class;
                    case 2: return Integer.class;
                    case 3: return Integer.class;
                    case 4: return Integer.class;
                    default: return super.getColumnClass(columnIndex);
                }
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return true;
            }
        });
        add(new JScrollPane(tilesTable), CENTER);
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new FlowLayout());
        JButton btnAddTile = new JButton("+");
        btnAddTile.addActionListener(event -> ((DefaultTableModel) tilesTable.getModel()).addRow(new Object[] {"new_tile", new File(""), 32, 32, 25}));
        buttonsPanel.add(btnAddTile);
        JButton btnExportAll = new JButton("Export all...");
        btnExportAll.addActionListener(event -> {
            if (tilesTable.getRowCount() > 0) {
                for (int i = tilesTable.getRowCount() - 1; i >= 0; i--) {
                    String name = (String) tilesTable.getValueAt(i, 0);
                    File imageLocation = (File) tilesTable.getValueAt(i, 1);
                    BufferedImage image = null;
                    try {
                        image = ImageIO.read(imageLocation);
                    } catch (IOException exception) {
                        showMessageDialog(null, "Failed to load image: " + exception.getMessage(), "Failed to load image", ERROR_MESSAGE);
                        application.getLogger().log(SEVERE, "Failed to load image", exception);
                    }
                    int frameWidth = (int) tilesTable.getValueAt(i, 2);
                    int frameHeight = (int) tilesTable.getValueAt(i, 3);
                    int frameDuration = (int) tilesTable.getValueAt(i, 4);
                    if (image != null) {
                        Tile tile = new Tile(name, frameWidth, frameHeight, image, frameDuration);
                        try {
                            tile.save(new File("./tiles/" + name));
                            ((DefaultTableModel) tilesTable.getModel()).removeRow(i);
                        } catch (IOException exception) {
                            showMessageDialog(null, "Failed to save tile: " + exception.getMessage(), "Failed to save tile", ERROR_MESSAGE);
                            application.getLogger().log(SEVERE, "Failed to save tile", exception);
                        }
                    }
                }
            }
        });
        buttonsPanel.add(btnExportAll);
        JButton btnBack = new JButton("Back");
        btnBack.addActionListener(event -> application.showPanel("menu"));
        buttonsPanel.add(btnBack);
        add(buttonsPanel, SOUTH);
    }

}
