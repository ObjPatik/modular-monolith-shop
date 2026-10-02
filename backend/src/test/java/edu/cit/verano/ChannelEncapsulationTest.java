package edu.cit.verano;

import edu.cit.verano.channel.ChannelService;
import edu.cit.verano.channel.ChannelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class ChannelEncapsulationTest {

    @Test
    @DisplayName("Channel Boundary: ONLY ChannelService and domain types are public; internals are package-private")
    void testChannelEncapsulationBoundaries() throws ClassNotFoundException {
        // Public domain contracts
        assertTrue(Modifier.isPublic(ChannelService.class.getModifiers()), "ChannelService must be public");
        assertTrue(Modifier.isPublic(ChannelStatus.class.getModifiers()), "ChannelStatus must be public");

        // Internal classes MUST be package-private
        String[] packagePrivateClasses = {
                "edu.cit.verano.channel.ChannelServiceImpl",
                "edu.cit.verano.channel.TianggeClient",
                "edu.cit.verano.channel.TianggeDtos",
                "edu.cit.verano.channel.TianggeFeedPoller",
                "edu.cit.verano.channel.TianggeOrderProcessor",
                "edu.cit.verano.channel.TianggeBackorderManager",
                "edu.cit.verano.channel.TianggeHeartbeatService",
                "edu.cit.verano.channel.TianggeListingPublisher",
                "edu.cit.verano.channel.TianggeStockSync",
                "edu.cit.verano.channel.TianggeEventListener",
                "edu.cit.verano.channel.TianggeContext",
                "edu.cit.verano.channel.TianggeFeedState",
                "edu.cit.verano.channel.TianggeFeedStateRepository",
                "edu.cit.verano.channel.TianggeProcessedEvent",
                "edu.cit.verano.channel.TianggeProcessedEventRepository",
                "edu.cit.verano.channel.TianggeOrderMapping",
                "edu.cit.verano.channel.TianggeOrderMappingRepository"
        };

        for (String className : packagePrivateClasses) {
            Class<?> clazz = Class.forName(className);
            int mod = clazz.getModifiers();
            assertFalse(Modifier.isPublic(mod), className + " must NOT be public");
            assertFalse(Modifier.isProtected(mod), className + " must NOT be protected");
            assertFalse(Modifier.isPrivate(mod), className + " must NOT be private top-level");
        }
    }
}

