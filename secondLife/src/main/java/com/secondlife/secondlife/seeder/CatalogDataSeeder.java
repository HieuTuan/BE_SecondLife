package com.secondlife.secondlife.seeder;

import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class CatalogDataSeeder implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking and seeding Catalog Data...");

        List<Category> categories = categoryRepository.findAll();

        // 1. Dịch các Category tiếng Anh cũ sang tiếng Việt (giữ nguyên ID để không bị lỗi khóa ngoại Post)
        boolean updated = false;
        for (Category c : categories) {
            String originalName = c.getName();
            if ("Kitchen".equalsIgnoreCase(originalName)) {
                c.setName("Bếp & Phòng ăn");
                c.setDescription("Thiết bị và đồ dùng nhà bếp");
                updated = true;
            } else if ("Bedroom".equalsIgnoreCase(originalName)) {
                c.setName("Phòng ngủ");
                c.setDescription("Nội thất và phụ kiện phòng ngủ");
                updated = true;
            } else if ("Balcony & Laundry".equalsIgnoreCase(originalName)) {
                c.setName("Ban công & Giặt giũ");
                c.setDescription("Thiết bị giặt ủi và đồ dùng ban công");
                updated = true;
            } else if ("Bathroom & Sanitation".equalsIgnoreCase(originalName)) {
                c.setName("Phòng tắm & Vệ sinh");
                c.setDescription("Thiết bị vệ sinh và phụ kiện phòng tắm");
                updated = true;
            } else if ("Other".equalsIgnoreCase(originalName)) {
                c.setName("Khác");
                c.setDescription("Các đồ dùng gia đình khác");
                updated = true;
            } else if ("Living Room".equalsIgnoreCase(originalName)) {
                c.setName("Phòng khách");
                c.setDescription("Nội thất và thiết bị phòng khách");
                updated = true;
            }
        }
        
        if (updated) {
            categoryRepository.saveAll(categories);
            log.info("Đã dịch các Category cũ từ tiếng Anh sang tiếng Việt.");
        }

        // Dịch các Item tiếng Anh cũ sang tiếng Việt
        List<Item> items = itemRepository.findAll();
        boolean itemUpdated = false;
        for (Item i : items) {
            String orig = i.getName();
            if (orig == null) continue;
            if (orig.equalsIgnoreCase("Desk")) { i.setName("Bàn làm việc"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Tool Box")) { i.setName("Hộp đồ nghề"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Washbasin")) { i.setName("Bồn rửa mặt"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Floor Lamp")) { i.setName("Đèn đứng"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Storage Cabinet")) { i.setName("Tủ đựng đồ"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Air Purifier")) { i.setName("Máy lọc không khí"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Laundry Basket")) { i.setName("Giỏ đựng đồ giặt"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Dressing Table")) { i.setName("Bàn trang điểm"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Dining Table")) { i.setName("Bàn ăn"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Balcony Table")) { i.setName("Bàn ban công"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Toilet")) { i.setName("Bồn cầu"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Iron")) { i.setName("Bàn ủi / Bàn là"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Drying Rack")) { i.setName("Giá phơi đồ"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Bookshelf")) { i.setName("Giá sách / Kệ sách"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Blender")) { i.setName("Máy xay sinh tố"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Mirror")) { i.setName("Gương"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Shower Set")) { i.setName("Vòi hoa sen"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Coffee Table")) { i.setName("Bàn trà / Bàn sofa"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Armchair")) { i.setName("Ghế bành"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Shoe Rack")) { i.setName("Kệ để giày"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Vacuum Cleaner")) { i.setName("Máy hút bụi"); itemUpdated = true; }
            
            // Các item cũ khác
            else if (orig.equalsIgnoreCase("Sofa") || orig.equalsIgnoreCase("Couch")) { i.setName("Sofa / Ghế dài"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Dryer")) { i.setName("Máy sấy"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Heater") || orig.equalsIgnoreCase("Water Heater")) { i.setName("Máy nước nóng"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Fan")) { i.setName("Quạt máy"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("TV") || orig.equalsIgnoreCase("Television")) { i.setName("Tivi"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Microwave") || orig.equalsIgnoreCase("Oven")) { i.setName("Lò vi sóng / Lò nướng"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Wardrobe") || orig.equalsIgnoreCase("Closet")) { i.setName("Tủ quần áo"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Washing Machine") || orig.equalsIgnoreCase("Washer")) { i.setName("Máy giặt"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Bathroom Accessories")) { i.setName("Phụ kiện phòng tắm"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Stove") || orig.equalsIgnoreCase("Cooker") || orig.equalsIgnoreCase("Rice Cooker")) { i.setName("Bếp / Nồi cơm điện"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Bed") || orig.equalsIgnoreCase("Mattress")) { i.setName("Giường / Nệm"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Refrigerator") || orig.equalsIgnoreCase("Fridge")) { i.setName("Tủ lạnh"); itemUpdated = true; }
            else if (orig.equalsIgnoreCase("Air Conditioner") || orig.equalsIgnoreCase("AC")) { i.setName("Máy lạnh / Điều hòa"); itemUpdated = true; }
        }
        if (itemUpdated) {
            itemRepository.saveAll(items);
            log.info("Đã dịch các Item cũ từ tiếng Anh sang tiếng Việt.");
        }

        // 2. Nếu DB trống (chưa có Category nào), thì tạo bộ mặc định
        if (categories.isEmpty()) {
            log.info("Không tìm thấy Category nào. Đang tạo bộ dữ liệu chuẩn bằng tiếng Việt...");
            
            seedCategoryWithItems("Bếp & Phòng ăn", "Thiết bị và đồ dùng nhà bếp",
                    List.of("Lò vi sóng / Lò nướng", "Bếp từ / Bếp hồng ngoại", "Nồi cơm điện", "Tủ lạnh", "Bàn ăn", "Máy xay sinh tố"));

            seedCategoryWithItems("Phòng ngủ", "Nội thất và phụ kiện phòng ngủ",
                    List.of("Tủ quần áo", "Khung giường", "Nệm / Đệm", "Tủ đầu giường", "Bàn làm việc", "Bàn trang điểm"));

            seedCategoryWithItems("Ban công & Giặt giũ", "Thiết bị giặt ủi và đồ dùng ban công",
                    List.of("Máy sấy", "Máy giặt", "Giỏ đựng đồ giặt", "Bàn ban công", "Bàn ủi / Bàn là", "Giá phơi đồ"));

            seedCategoryWithItems("Phòng tắm & Vệ sinh", "Thiết bị vệ sinh và phụ kiện phòng tắm",
                    List.of("Máy nước nóng", "Tủ / Kệ phòng tắm", "Bồn rửa mặt", "Bồn cầu", "Gương", "Vòi hoa sen"));

            seedCategoryWithItems("Phòng khách", "Nội thất và thiết bị phòng khách",
                    List.of("Sofa / Ghế dài", "Kệ Tivi", "Đèn đứng", "Giá sách / Kệ sách", "Bàn trà / Bàn sofa", "Ghế bành"));

            seedCategoryWithItems("Khác", "Các đồ dùng gia đình khác",
                    List.of("Quạt máy", "Hộp đồ nghề", "Tủ đựng đồ", "Máy lọc không khí", "Kệ để giày", "Máy hút bụi"));

            log.info("Tạo xong bộ Category và Item tiếng Việt.");
        }
    }

    private void seedCategoryWithItems(String catName, String catDesc, List<String> itemNames) {
        Category cat = new Category();
        cat.setName(catName);
        cat.setDescription(catDesc);
        cat = categoryRepository.save(cat);

        for (String itemName : itemNames) {
            Item item = new Item();
            item.setCategory(cat);
            item.setName(itemName);
            itemRepository.save(item);
        }
    }
}