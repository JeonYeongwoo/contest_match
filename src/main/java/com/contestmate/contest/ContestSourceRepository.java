package com.contestmate.contest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContestSourceRepository extends JpaRepository<ContestSource, Long> {
    List<ContestSource> findByContestId(Long contestId);
}
