package edu.cit.verano.channel;

import org.springframework.stereotype.Service;

/**
 * PACKAGE-PRIVATE implementation of the public ChannelService contract.
 */
@Service
class ChannelServiceImpl implements ChannelService {

    private final TianggeHeartbeatService heartbeatService;
    private final TianggeStockSync stockSync;
    private final TianggeFeedPoller feedPoller;
    private final TianggeClient tianggeClient;

    ChannelServiceImpl(TianggeHeartbeatService heartbeatService,
                       TianggeStockSync stockSync,
                       TianggeFeedPoller feedPoller,
                       TianggeClient tianggeClient) {
        this.heartbeatService = heartbeatService;
        this.stockSync = stockSync;
        this.feedPoller = feedPoller;
        this.tianggeClient = tianggeClient;
    }

    @Override
    public void triggerHeartbeat() {
        heartbeatService.sendHeartbeat();
    }

    @Override
    public void syncStock() {
        stockSync.publishStock();
    }

    @Override
    public ChannelStatus getStatus() {
        return new ChannelStatus(
                "Tiangge Marketplace",
                heartbeatService.isOnline(),
                tianggeClient.getInstanceId(),
                feedPoller.getCursor(),
                heartbeatService.getLastHeartbeatAt()
        );
    }
}

