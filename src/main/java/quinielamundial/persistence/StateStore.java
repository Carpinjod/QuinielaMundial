package quinielamundial.persistence;

import quinielamundial.domain.Group;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StateStore {
    private final Path file;
    public StateStore(Path file) { this.file = file; }
    public void save(java.util.Collection<Group> groups, String champion) { try { Files.createDirectories(file.getParent()); try (var out = new ObjectOutputStream(Files.newOutputStream(file))) { out.writeObject(new Snapshot(new ArrayList<>(groups), champion)); } } catch (IOException e) { throw new IllegalStateException("No se pudo guardar el estado", e); } }
    public Snapshot load() { if (!Files.exists(file)) return new Snapshot(List.of(), "Argentina"); try (var in = new ObjectInputStream(Files.newInputStream(file))) { return (Snapshot) in.readObject(); } catch (IOException | ClassNotFoundException e) { throw new IllegalStateException("No se pudo cargar el estado", e); } }
    public record Snapshot(List<Group> groups, String champion) implements java.io.Serializable { private static final long serialVersionUID = 1L; }
}
