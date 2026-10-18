package com.italo.ai.memory.persistence;
import com.italo.ai.memory.model.MemoryType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface MemoryRepository extends JpaRepository<MemoryEntity, UUID> {
    @Query("""
        select m from MemoryEntity m where m.userId = :userId
        and (:type is null or m.type = :type)
        and (:subject is null or m.subject = :subject)
        and (:predicate is null or m.predicate = :predicate)
        and (:text is null or locate(lower(cast(:text as String)), lower(m.value)) > 0)
        order by m.createdAt desc, m.id
        """)
    Page<MemoryEntity> search(String userId, MemoryType type, String subject, String predicate, String text, Pageable pageable);
    List<MemoryEntity> findByIdInAndUserId(Collection<UUID> ids, String userId);
}
