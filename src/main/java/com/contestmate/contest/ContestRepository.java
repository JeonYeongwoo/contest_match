package com.contestmate.contest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ContestRepository extends JpaRepository<Contest, Long> {

    @Query("SELECT c FROM Contest c WHERE " +
            "(:region IS NULL OR c.onlineOffline = com.contestmate.contest.OnlineOffline.ONLINE " +
            "   OR LOWER(c.region) = LOWER(:region)) " +
            "AND (:category IS NULL OR LOWER(c.categories) LIKE LOWER(CONCAT('%', :category, '%'))) " +
            "AND (:organizer IS NULL OR LOWER(c.organizer) LIKE LOWER(CONCAT('%', :organizer, '%'))) " +
            "AND (:onlineOnly = FALSE OR c.onlineOffline = com.contestmate.contest.OnlineOffline.ONLINE " +
            "   OR c.onlineOffline = com.contestmate.contest.OnlineOffline.HYBRID) " +
            "AND (:individualOrTeam IS NULL OR c.individualOrTeam = :individualOrTeam " +
            "   OR c.individualOrTeam = com.contestmate.contest.IndividualOrTeam.BOTH) " +
            "AND (:minPrize IS NULL OR c.prizeAmountKrw IS NULL OR c.prizeAmountKrw >= :minPrize) " +
            "AND (:deadlineBefore IS NULL OR c.deadline IS NULL OR c.deadline <= :deadlineBefore) " +
            "AND (:keyword IS NULL OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "   OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Contest> search(@Param("region") String region,
                          @Param("category") String category,
                          @Param("organizer") String organizer,
                          @Param("onlineOnly") boolean onlineOnly,
                          @Param("individualOrTeam") IndividualOrTeam individualOrTeam,
                          @Param("minPrize") Long minPrize,
                          @Param("deadlineBefore") LocalDate deadlineBefore,
                          @Param("keyword") String keyword);

    List<Contest> findTop20ByDeadlineGreaterThanEqualOrderByDeadlineAsc(LocalDate from);
}
