package dev.airmouse;

public final class NetworkAddressTest {
    public static void main(String[] args) {
        String[] accepted = {"192.168.1.4", "172.24.99.238", "10.0.0.1",
            "2403:0:614:2e0:1410:9117:665:4c6a", "[2403::1]", "fd12::3"};
        String[] rejected = {"", "example.com", "192.168.999.1", "127.0.0.1",
            "8.8.8.8", "::", "::1", "ff02::1", "fe80::1%wlan0", "fe80::1",
            "2403:::1", "[2403::1]:47821", "http://192.168.1.4"};
        for (String address : accepted) {
            if (!NetworkAddress.valid(address)) throw new AssertionError("Rejected " + address);
        }
        for (String address : rejected) {
            if (NetworkAddress.valid(address)) throw new AssertionError("Accepted " + address);
        }
        System.out.println("PASS: " + (accepted.length + rejected.length) + " IPv4/IPv6 address checks");
    }
}
