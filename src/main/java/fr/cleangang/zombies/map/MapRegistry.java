package fr.cleangang.zombies.map;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Toutes les cartes de plugins/CleanZombies/maps, triées par "order". */
public final class MapRegistry {

    private final Map<String, MapDefinition> maps;

    private MapRegistry(Map<String, MapDefinition> maps) {
        this.maps = maps;
    }

    public static MapRegistry load(File folder, List<String> errors) {
        List<MapDefinition> list = new ArrayList<>();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) list.add(MapDefinition.load(f, errors));
        }
        list.sort(Comparator.comparingInt((MapDefinition m) -> m.order).thenComparing(m -> m.id));
        Map<String, MapDefinition> out = new LinkedHashMap<>();
        for (MapDefinition m : list) {
            if (out.putIfAbsent(m.id.toLowerCase(Locale.ROOT), m) != null) {
                errors.add("maps/ → deux cartes utilisent l'id « " + m.id + " »");
            }
        }
        if (out.isEmpty()) errors.add("maps/ → aucune carte trouvée");
        return new MapRegistry(out);
    }

    public MapDefinition get(String id) {
        return id == null ? null : maps.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<MapDefinition> all() {
        return maps.values();
    }

    public List<String> ids() {
        return new ArrayList<>(maps.keySet());
    }
}
