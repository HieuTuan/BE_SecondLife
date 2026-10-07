package com.secondlife.secondlife.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogSeedService {
    private final JdbcTemplate jdbc;
    private final CatalogWriteLock writeLock;

    private record Room(String key, String name, String description, List<String> items) { }
    private static final List<Room> ROOMS = List.of(
            new Room("living-room", "Living Room", "Furniture and appliances for the living room",
                    List.of("Sofa", "Coffee Table", "TV Stand", "Armchair", "Bookshelf", "Floor Lamp")),
            new Room("kitchen", "Kitchen", "Kitchen appliances and dining furniture",
                    List.of("Refrigerator", "Microwave Oven", "Rice Cooker", "Induction Cooker", "Blender", "Dining Table")),
            new Room("bedroom", "Bedroom", "Bedroom furniture and accessories",
                    List.of("Bed Frame", "Mattress", "Wardrobe", "Bedside Table", "Dressing Table", "Desk")),
            new Room("bathroom", "Bathroom & Sanitation", "Bathroom fixtures and sanitation equipment",
                    List.of("Water Heater", "Bathroom Cabinet", "Washbasin", "Mirror", "Shower Set", "Toilet")),
            new Room("balcony-laundry", "Balcony & Laundry", "Laundry appliances and balcony furniture",
                    List.of("Washing Machine", "Clothes Dryer", "Drying Rack", "Laundry Basket", "Iron", "Balcony Table")),
            new Room("other", "Other", "Other household goods and equipment",
                    List.of("Vacuum Cleaner", "Fan", "Air Purifier", "Storage Cabinet", "Shoe Rack", "Tool Box"))
    );

    /** Adds missing defaults without changing existing IDs, edited labels or assignments. */
    @Transactional
    public void seedDefaults() {
        writeLock.lock();
        List<Object> categoryArgs = new ArrayList<>();
        List<Object> itemArgs = new ArrayList<>();
        for (Room room : ROOMS) {
            Collections.addAll(categoryArgs, room.key(), room.name(), room.description());
            for (String item : room.items()) {
                // These keys identify original defaults independently of editable database names.
                String key = room.key() + "/" + item.toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
                Collections.addAll(itemArgs, key, room.key(), item);
            }
        }
        jdbc.update("""
                WITH seeds(seed_key, name, description) AS (VALUES %s),
                candidates AS (
                    SELECT DISTINCT ON (s.seed_key) s.seed_key, c.id
                    FROM seeds s JOIN categories c ON lower(btrim(c.name)) = lower(s.name)
                    WHERE c.seed_key IS NULL
                      AND NOT EXISTS (SELECT 1 FROM categories x WHERE x.seed_key = s.seed_key)
                    ORDER BY s.seed_key, c.id
                ), adopted AS (
                    UPDATE categories c SET seed_key = x.seed_key FROM candidates x WHERE c.id = x.id
                    RETURNING c.seed_key
                )
                INSERT INTO categories(id, seed_key, name, description)
                SELECT gen_random_uuid(), s.seed_key, s.name, s.description FROM seeds s
                WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.seed_key = s.seed_key)
                  AND NOT EXISTS (SELECT 1 FROM adopted a WHERE a.seed_key = s.seed_key)
                ON CONFLICT (seed_key) DO NOTHING
                """.formatted(values(ROOMS.size())), categoryArgs.toArray());
        jdbc.update("""
                WITH seeds(seed_key, category_key, name) AS (VALUES %s),
                candidates AS (
                    SELECT DISTINCT ON (s.seed_key) s.seed_key, i.id
                    FROM seeds s JOIN categories c ON c.seed_key = s.category_key
                    JOIN items i ON i.category_id = c.id AND lower(btrim(i.name)) = lower(s.name)
                    WHERE i.seed_key IS NULL
                      AND NOT EXISTS (SELECT 1 FROM items x WHERE x.seed_key = s.seed_key)
                    ORDER BY s.seed_key, i.id
                ), adopted AS (
                    UPDATE items i SET seed_key = x.seed_key FROM candidates x WHERE i.id = x.id
                    RETURNING i.seed_key
                )
                INSERT INTO items(id, seed_key, category_id, name)
                SELECT gen_random_uuid(), s.seed_key, c.id, s.name FROM seeds s
                JOIN categories c ON c.seed_key = s.category_key
                WHERE NOT EXISTS (SELECT 1 FROM items i WHERE i.seed_key = s.seed_key)
                  AND NOT EXISTS (SELECT 1 FROM adopted a WHERE a.seed_key = s.seed_key)
                ON CONFLICT (seed_key) DO NOTHING
                """.formatted(values(itemArgs.size() / 3)), itemArgs.toArray());
    }

    private static String values(int count) { return String.join(",", Collections.nCopies(count, "(?, ?, ?)")); }
}
