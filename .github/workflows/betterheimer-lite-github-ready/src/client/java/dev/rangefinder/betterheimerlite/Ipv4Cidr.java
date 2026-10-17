package dev.rangefinder.betterheimerlite;

import java.util.ArrayList;
import java.util.List;

/** CIDR expansion deliberately limited to private, loopback, and link-local IPv4 networks. */
final class Ipv4Cidr {
    private static final long IPV4_MASK = 0xFFFF_FFFFL;

    private Ipv4Cidr() {
    }

    static List<String> expand(String cidr, int configuredLimit) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("Set a CIDR range in config/betterheimerlite.json.");
        }
        String[] pieces = cidr.trim().split("/", -1);
        if (pieces.length != 2) {
            throw new IllegalArgumentException("Range must use IPv4 CIDR notation, for example 192.168.1.0/24.");
        }

        long address = parseIpv4(pieces[0]);
        int prefix;
        try {
            prefix = Integer.parseInt(pieces[1]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("CIDR prefix must be between 0 and 32.");
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be between 0 and 32.");
        }

        long mask = prefix == 0 ? 0 : (IPV4_MASK << (32 - prefix)) & IPV4_MASK;
        long network = address & mask;
        long broadcast = network | (IPV4_MASK ^ mask);
        if (!isEntireRangeAllowed(network, broadcast)) {
            throw new IllegalArgumentException("Public IPv4 ranges are disabled. Use an authorized private, loopback, or link-local IPv4 range.");
        }

        long first = prefix <= 30 ? network + 1 : network;
        long last = prefix <= 30 ? broadcast - 1 : broadcast;
        long count = last - first + 1;
        int limit = Math.min(Math.max(configuredLimit, 1), ModConfig.HARD_MAX_HOSTS);
        if (count < 1) {
            throw new IllegalArgumentException("This CIDR contains no usable host addresses.");
        }
        if (count > ModConfig.HARD_MAX_HOSTS || count > limit) {
            throw new IllegalArgumentException("Range contains " + count + " host addresses; current limit is " + limit + " (hard cap " + ModConfig.HARD_MAX_HOSTS + "). Narrow the CIDR or lower maxHosts.");
        }

        List<String> hosts = new ArrayList<>((int) count);
        for (long current = first; current <= last; current++) {
            hosts.add(toIpv4(current));
        }
        return hosts;
    }

    private static boolean isEntireRangeAllowed(long start, long end) {
        return inside(start, end, 0x0A00_0000L, 0x0AFF_FFFFL)       // 10.0.0.0/8
                || inside(start, end, 0xAC10_0000L, 0xAC1F_FFFFL) // 172.16.0.0/12
                || inside(start, end, 0xC0A8_0000L, 0xC0A8_FFFFL) // 192.168.0.0/16
                || inside(start, end, 0x7F00_0000L, 0x7FFF_FFFFL) // 127.0.0.0/8
                || inside(start, end, 0xA9FE_0000L, 0xA9FE_FFFFL); // 169.254.0.0/16
    }

    private static boolean inside(long start, long end, long allowedStart, long allowedEnd) {
        return start >= allowedStart && end <= allowedEnd;
    }

    private static long parseIpv4(String text) {
        String[] octets = text.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalArgumentException("Only numeric IPv4 addresses are supported; DNS names and IPv6 are not accepted.");
        }
        long address = 0;
        for (String octet : octets) {
            if (octet.isEmpty() || !octet.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + text);
            }
            int value;
            try {
                value = Integer.parseInt(octet);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + text);
            }
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + text);
            }
            address = (address << 8) | value;
        }
        return address & IPV4_MASK;
    }

    private static String toIpv4(long address) {
        return ((address >>> 24) & 255) + "."
                + ((address >>> 16) & 255) + "."
                + ((address >>> 8) & 255) + "."
                + (address & 255);
    }
}
