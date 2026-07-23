package com.orchestrator.common.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;

@Slf4j
public class UrlSecurityValidator {

    public static boolean isValidWebhookUrl(String urlString) {
        return isValidWebhookUrl(urlString, false);
    }

    public static boolean isValidWebhookUrl(String urlString, boolean allowPrivateIps) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }

        try {
            URI uri = new URI(urlString).normalize();
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                log.warn("SSRF check failed: Invalid scheme '{}' for URL {}", scheme, urlString);
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                log.warn("SSRF check failed: Empty host for URL {}", urlString);
                return false;
            }

            if (allowPrivateIps) {
                return true;
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (isRestrictedIp(address)) {
                    log.warn("SSRF check failed: Host '{}' resolved to restricted IP address '{}'", host, address.getHostAddress());
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.warn("SSRF check failed: Error parsing or resolving URL '{}': {}", urlString, e.getMessage());
            return false;
        }
    }

    public static boolean isRestrictedIp(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }

        byte[] ip = address.getAddress();
        if (ip.length == 4) { // IPv4
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;

            // 10.0.0.0/8
            if (b0 == 10) return true;

            // 172.16.0.0/12
            if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;

            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true;

            // 127.0.0.0/8
            if (b0 == 127) return true;

            // 169.254.0.0/16 (Link Local / Cloud IMDS e.g. 169.254.169.254)
            if (b0 == 169 && b1 == 254) return true;

            // 0.0.0.0/8
            if (b0 == 0) return true;
        } else if (ip.length == 16) { // IPv6
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;

            // IPv6 Unique Local Address fc00::/7
            if ((b0 & 0xFE) == 0xFC) return true;

            // IPv6 Link-Local Address fe80::/10
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) return true;
        }

        return false;
    }
}
