package dev.felnull.imp.client.gui.config.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;

public record UserProxy(Proxy.Type type, String host, int port) {

    public static final UserProxy EMPTY =
            new UserProxy(Proxy.Type.DIRECT, "", 0);

    public Proxy getProxy() {
        if (type == Proxy.Type.DIRECT) {
            return Proxy.NO_PROXY;
        }
        return new Proxy(type, new InetSocketAddress(host, port));
    }

    public static UserProxy of(Proxy proxy) {
        if (proxy == null || proxy.type() == Proxy.Type.DIRECT) {
            return EMPTY;
        }

        if (proxy.address() instanceof InetSocketAddress addr) {
            return new UserProxy(
                    proxy.type(),
                    addr.getHostString(),
                    addr.getPort()
            );
        }

        return EMPTY;
    }

    public boolean isValid() {
        if (type == Proxy.Type.DIRECT) return true;
        return host != null && !host.isBlank() && port > 0 && port <= 65535;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserProxy other)) return false;
        if (type == Proxy.Type.DIRECT && other.type == Proxy.Type.DIRECT) return true;
        return type == other.type && port == other.port && Objects.equals(host, other.host);
    }

    @Override
    public int hashCode() {
        if (type == Proxy.Type.DIRECT) {
            return 0;
        }
        return Objects.hash(type, host, port);
    }
}