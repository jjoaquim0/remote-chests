package dev.joaquim.remotechests.storage;

import dev.joaquim.remotechests.model.RemoteChest;

import java.util.Collection;
import java.util.List;

/**
 * Persistencia dos registros. A implementacao atual grava um YAML simples;
 * trocar por SQLite/Postgres (util quando a interface web chegar) e implementar
 * esta interface e mudar uma linha no RemoteChestsPlugin.
 */
public interface ChestStorage {

    List<RemoteChest> loadAll();

    /**
     * Serializa no thread chamador (a colecao vem do estado do servidor) e
     * grava em disco de forma assincrona e atomica.
     */
    void saveAll(Collection<RemoteChest> chests);

    /** Mesma coisa, mas bloqueando — usado no onDisable. */
    void saveAllBlocking(Collection<RemoteChest> chests);
}
