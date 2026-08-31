package com.ecommerce.management.entity;

import com.ecommerce.management.entity.enums.AttachableType;
import com.ecommerce.management.entity.enums.AttachmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "attachments", indexes = @Index(name = "idx_attachments_attachable", columnList = "attachable_type,attachable_id"))
@Getter @Setter @NoArgsConstructor
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "attachable_type", nullable = false, length = 100)
    private AttachableType attachableType;
    @Column(name = "attachable_id", nullable = false)
    private Long attachableId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private AttachmentType type;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(nullable = false, length = 500)
    private String path;
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;
    @Column(nullable = false)
    private Long size;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
