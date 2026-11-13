package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Entity
@Table(name = "category_question_templates")
@Getter
@Setter
@NoArgsConstructor
public class CategoryQuestionTemplate {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;

    @Column(name = "item_id")
    private UUID itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", insertable = false, updatable = false)
    private Item item;

    @Column(name = "template_text", nullable = false, columnDefinition = "TEXT")
    private String templateText;

    // e.g. "Dựa vào hình ảnh bạn đăng lên tôi thấy ngoại hình đẹp {AI_APPEARANCE_ESTIMATE}. Bạn đồng ý không?\nVui lòng cung cấp thêm thông tin:\n1. Hãng\n2. Model\n3. Thời gian đã sử dụng\n4. Tình trạng bảo hành"
}
