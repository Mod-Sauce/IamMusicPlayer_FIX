package dev.felnull.imp.client.lava;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IMPProxySelector extends ProxySelector {
    private static final Logger LOGGER = LogManager.getLogger(IMPProxySelector.class);

    private final List<Proxy> proxies;
    private final Map<Proxy, Long> failedAt = new ConcurrentHashMap<>();
    private static final long RETRY_MS = 60_000;

    public IMPProxySelector(List<Proxy> proxies) {
        this.proxies = List.copyOf(proxies);
    }

    @Override
    public List<Proxy> select(URI uri) {
        long now = System.currentTimeMillis();

        List<Proxy> available = proxies.stream()
                .filter(proxy -> {
                    long t = failedAt.getOrDefault(proxy, 0L);
                    return t == 0 || now - t > RETRY_MS;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        available.add(Proxy.NO_PROXY); // 兜底直连
        return available;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        LOGGER.warn("Proxy failed [{}] for {}: {}", socketAddress, uri, e.getMessage());

        proxies.stream()
                .filter(p -> p.address() != null && p.address().equals(socketAddress))
                .findFirst()
                .ifPresent(p -> failedAt.put(p, System.currentTimeMillis()));
    }
}
