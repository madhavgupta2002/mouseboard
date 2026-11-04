package dev.airmouse;

import java.net.Inet6Address;
import java.net.InetAddress;

/** Numeric addresses only: never perform a user-controlled hostname lookup. */
final class NetworkAddress {
    static String normalize(String input) {
        String value = input.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    static boolean valid(String input) {
        String value = normalize(input);
        if (value.contains(":")) {
            if (!value.matches("[0-9a-fA-F:]+")) return false;
            try {
                InetAddress address = InetAddress.getByName(value);
                if (!(address instanceof Inet6Address)) return false;
                int first = address.getAddress()[0] & 255;
                // Global unicast or ULA. Exclude link-local addresses whose
                // interface scope differs between the Mac and the phone.
                return (first & 0xe0) == 0x20 || (first & 0xfe) == 0xfc;
            } catch (Exception invalid) { return false; }
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) return false;
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) return false;
        }
        return octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
            || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 169 && octets[1] == 254);
    }
}
