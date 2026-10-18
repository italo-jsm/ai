package com.italo.ai.memory.persistence;

import com.italo.ai.memory.model.*;
import jakarta.persistence.*;
import java.util.*;
import java.time.Instant;

@Entity
@Table(name = "memory")
public class MemoryEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false, length = 255) private String userId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private MemoryType type;
    @Column(nullable = false, columnDefinition = "text") private String subject;
    @Column(nullable = false, columnDefinition = "text") private String predicate;
    @Column(name = "memory_value", nullable = false, columnDefinition = "text") private String value;
    @Column(columnDefinition = "text") private String reason;

    protected MemoryEntity() {}
    public MemoryEntity(String userId, MemoryCandidate candidate) {
        if (userId == null || userId.isBlank() || userId.length() > 255)
            throw new IllegalArgumentException("userId deve conter entre 1 e 255 caracteres");
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = Instant.now();
        this.type = Objects.requireNonNull(candidate.type(), "type");
        this.subject = Objects.requireNonNull(candidate.subject(), "subject");
        this.predicate = Objects.requireNonNull(candidate.predicate(), "predicate");
        this.value = Objects.requireNonNull(candidate.value(), "value");
        this.reason = candidate.reason();
    }
    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
    public MemoryType getType() { return type; }
    public String getSubject() { return subject; }
    public String getPredicate() { return predicate; }
    public String getValue() { return value; }
    public String getReason() { return reason; }
}
