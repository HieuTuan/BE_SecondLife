package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.CategoryRepository.CategorySeed;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.ItemRepository.ItemSeed;
import com.secondlife.secondlife.service.CatalogSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service("catalogSeedService")
@RequiredArgsConstructor
public class CatalogSeedServiceImpl implements CatalogSeedService {
    private final CategoryRepository categories;
    private final ItemRepository items;

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
        categories.lockCatalog();
        List<CategorySeed> categorySeeds = new ArrayList<>();
        List<ItemSeed> itemSeeds = new ArrayList<>();
        for (Room room : ROOMS) {
            categorySeeds.add(new CategorySeed(room.key(), room.name(), room.description()));
            for (String item : room.items()) {
                // These keys identify original defaults independently of editable database names.
                String key = room.key() + "/" + item.toLowerCase(Locale.ROOT).replace(' ', '-');
                itemSeeds.add(new ItemSeed(key, room.key(), item));
            }
        }
        categories.seedCategories(categorySeeds);
        items.seedItems(itemSeeds);
    }
}
