package com.pawpose.domain.lostpet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;

public interface LostPetRepository extends JpaRepository<LostPet, Long> {
    List<LostPet> findByUserIdOrderByIdDesc(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LostPet p where p.id = :id")
    Optional<LostPet> lockById(@Param("id") Long id);
}
