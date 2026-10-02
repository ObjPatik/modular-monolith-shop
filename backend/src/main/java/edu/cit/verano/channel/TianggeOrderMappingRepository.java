package edu.cit.verano.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PACKAGE-PRIVATE repository for tracked Tiangge orders.
 */
@Repository
interface TianggeOrderMappingRepository extends JpaRepository<TianggeOrderMapping, String> {

    Optional<TianggeOrderMapping> findByOrderId(String orderId);

    Optional<TianggeOrderMapping> findByShopOrderId(String shopOrderId);

    List<TianggeOrderMapping> findByStatusOrderByPlacedAtAsc(String status);
}

