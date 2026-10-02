package edu.cit.verano.channel;

import edu.cit.verano.channel.TianggeDtos.ListingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * PACKAGE-PRIVATE publisher for Tiangge product catalog listings.
 * Maps internal sellerSkus to LegacySupply supplierSkus.
 */
@Component
class TianggeListingPublisher {

    private static final Logger log = LoggerFactory.getLogger(TianggeListingPublisher.class);

    private final TianggeClient tianggeClient;

    TianggeListingPublisher(TianggeClient tianggeClient) {
        this.tianggeClient = tianggeClient;
    }

    /**
     * Publishes 3 listings with their verified supplier SKU mappings.
     */
    void publishListings() {
        List<ListingDto> listings = List.of(
                new ListingDto("P100", "Wireless mouse", "XFM-7477"),
                new ListingDto("P200", "Mechanical keyboard", "XFM-7593"),
                new ListingDto("P300", "USB-C hub", "XFM-3161")
        );

        log.info("[TianggeListingPublisher] Publishing {} catalog listings to Tiangge...", listings.size());
        tianggeClient.publishListings(listings);
    }
}

