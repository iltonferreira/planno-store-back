package com.planno.store.store.repository;

import com.planno.store.store.domain.Store;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    List<Store> findAllByOrderByCreatedAtAsc();

    Optional<Store> findFirstByStatusOrderByCreatedAtAsc(Store.StoreStatus status);

    Optional<Store> findBySlugIgnoreCase(String slug);

    Optional<Store> findBySlugIgnoreCaseAndStatus(String slug, Store.StoreStatus status);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);

    @Query("""
            select s
            from Store s
            where (:status is null or s.status = :status)
            and (
                :search is null
                or upper(s.name) like upper(concat('%', cast(:search as string), '%'))
                or upper(s.slug) like upper(concat('%', cast(:search as string), '%'))
                or upper(s.contactEmail) like upper(concat('%', cast(:search as string), '%'))
            )
            order by s.createdAt asc
            """)
    List<Store> search(@Param("status") Store.StoreStatus status, @Param("search") String search);
}
