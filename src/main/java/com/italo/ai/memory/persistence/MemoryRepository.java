package com.italo.ai.memory.persistence;
import com.italo.ai.memory.model.MemoryType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface MemoryRepository extends JpaRepository<MemoryEntity, UUID> {
    @Query("""
        select m from MemoryEntity m where m.user.id = :userId
        and (:type is null or m.type = :type)
        and (:subject is null or m.subject = :subject)
        and (:predicate is null or m.predicate = :predicate)
        and (:text is null or locate(lower(cast(:text as String)), lower(m.value)) > 0)
        order by m.createdAt desc, m.id
        """)
    Page<MemoryEntity> search(UUID userId, MemoryType type, String subject, String predicate, String text, Pageable pageable);
    @Query("select m from MemoryEntity m where m.id in :ids and m.user.id = :userId")
    List<MemoryEntity> findByIdInAndUserId(Collection<UUID> ids, UUID userId);
}
