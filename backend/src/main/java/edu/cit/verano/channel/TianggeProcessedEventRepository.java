package edu.cit.verano.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PACKAGE-PRIVATE repository for tracked processed event IDs.
 */
@Repository
interface TianggeProcessedEventRepository extends JpaRepository<TianggeProcessedEvent, String> {
}

