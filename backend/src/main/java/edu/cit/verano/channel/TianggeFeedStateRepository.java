package edu.cit.verano.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * PACKAGE-PRIVATE repository for persisting the order feed cursor.
 */
@Repository
interface TianggeFeedStateRepository extends JpaRepository<TianggeFeedState, Long> {
}

