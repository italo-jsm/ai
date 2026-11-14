package com.italo.ai.memory.persistence;

import com.italo.ai.memory.model.*;
import com.italo.ai.user.UserEntity;
import jakarta.persistence.*;
import java.util.*;
import java.time.Instant;

@Entity
@Table(name = "memory")
public class MemoryEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private MemoryType type;
    @Column(nullable = false, columnDefinition = "text") private String subject;
    @Column(nullable = false, columnDefinition = "text") private String predicate;
    @Column(name = "memory_value", nullable = false, columnDefinition = "text") private String value;
    @Column(columnDefinition = "text") private String reason;

    protected MemoryEntity() {}
    public MemoryEntity(UserEntity user, MemoryCandidate candidate) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "user");
        this.createdAt = Instant.now();
        this.type = Objects.requireNonNull(candidate.type(), "type");
        this.subject = Objects.requireNonNull(candidate.subject(), "subject");
        this.predicate = Objects.requireNonNull(candidate.predicate(), "predicate");
        this.value = Objects.requireNonNull(candidate.value(), "value");
        this.reason = candidate.reason();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return user.getId(); }
    public UserEntity getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public MemoryType getType() { return type; }
    public String getSubject() { return subject; }
    public String getPredicate() { return predicate; }
    public String getValue() { return value; }
    public String getReason() { return reason; }
}
