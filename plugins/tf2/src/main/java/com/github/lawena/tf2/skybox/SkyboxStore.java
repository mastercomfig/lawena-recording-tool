package com.github.lawena.tf2.skybox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.swing.*;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

public class SkyboxStore implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SkyboxStore.class);

    private Map<String, ImageIcon> store = new LinkedHashMap<>();
    private transient ListProperty<Skybox> itemsProperty;

    private static BufferedImage toBufferedImage(java.awt.Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }
        BufferedImage bimage =
                new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();
        return bimage;
    }

    private static ImageIcon toImageIcon(Image src) {
        return new ImageIcon(SwingFXUtils.fromFXImage(src, null));
    }

    private static void gzWrite(Serializable s, File dest) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(dest);
             GZIPOutputStream gz = new GZIPOutputStream(fos);
             ObjectOutputStream oos = new ObjectOutputStream(gz)) {
            oos.writeObject(s);
        }
    }

    private static <T> T gzRead(Class<T> cls, File src) throws IOException, ClassNotFoundException {
        try (FileInputStream fis = new FileInputStream(src);
             GZIPInputStream gs = new GZIPInputStream(fis);
             ObjectInputStream ois = new ObjectInputStream(gs)) {
            Object o = ois.readObject();
            return cls.cast(o);
        }
    }

    public final ListProperty<Skybox> itemsProperty() {
        if (itemsProperty == null) {
            itemsProperty = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList()); //$NON-NLS-1$
        }
        return itemsProperty;
    }

    public final ObservableList<Skybox> getItems() {
        return this.itemsProperty().get();
    }

    public final void setItems(final ObservableList<Skybox> itemsProperty) {
        this.itemsProperty().set(itemsProperty);
    }

    public void add(Skybox skybox) {
        if (!getSkybox(skybox.getName()).isPresent()) {
            itemsProperty().get().add(skybox);
        } else {
            log.debug("{} not added because it already exists", skybox); //$NON-NLS-1$
        }
    }

    public Optional<Skybox> getSkybox(String byName) {
        return itemsProperty().get().stream().filter(s -> s.getName().equals(byName)).findFirst();
    }

    public void load(Path path) {
        if (Files.exists(path)) {
            try {
                SkyboxStore ss = gzRead(SkyboxStore.class, path.toFile());
                ss.store.forEach((name, icon) -> itemsProperty().get().add(
                        new Skybox(name, SwingFXUtils.toFXImage(toBufferedImage(icon.getImage()), null))));
                log.info("Loaded skybox cache from {}", path); //$NON-NLS-1$
            } catch (ClassNotFoundException | IOException e) {
                log.warn("Could not load skybox cache", e); //$NON-NLS-1$
            }
        } else {
            log.debug("No skybox cache found at {}", path); //$NON-NLS-1$
        }
    }

    public void save(Path path) {
        store.putAll(itemsProperty().get().stream()
                .filter(s -> s.getName() != null && s.getPreview() != null)
                .collect(Collectors.toMap(s -> s.getName(), s -> toImageIcon(s.getPreview()))));
        try {
            gzWrite(this, path.toFile());
            log.info("Skybox cache saved to {}", path); //$NON-NLS-1$
        } catch (IOException e) {
            log.warn("Could not save skybox cache", e); //$NON-NLS-1$
        }
    }
}