package org.adaway.vpn.dns;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.packet.namednumber.UdpPort;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Unit tests for the packet proxy's handling of hostile input.
 * <p>
 * Every byte handed to {@link DnsPacketProxy#handleDnsRequest} was written into the tunnel device
 * by some app on the phone, so it can be anything. The contract this pins down is not that a broken
 * packet is rejected, it is <em>how</em>: the only exception allowed out is the {@link IOException}
 * that means the network refused the forward. Anything else reaches the worker loop, which treats
 * it as an unexpected failure and stops the VPN thread, so a single malformed packet took the
 * tunnel down until something restarted it.
 */
public class DnsPacketProxyTest {

    /**
     * A DNS query pcap4j parses but cannot re-serialize, which made {@code getRawData()} throw an
     * ArrayIndexOutOfBoundsException straight through the proxy. Found by fuzzing.
     */
    private static final String UNSERIALIZABLE_DNS_PAYLOAD =
            "62EAAAABAAAAAAAAGUNs2c0JaZlLW4PGwAQd63pwnb//bboh8hQQY5RXKk0NALhLBIpApEAEWsOd32eDocYQJDXpAEAfDg==";

    @Test
    public void unserializableDnsQuery_isDiscarded() {
        byte[] packet = udpOverIpv4(Base64.getDecoder().decode(UNSERIALIZABLE_DNS_PAYLOAD));
        feed(packet, "a DNS query pcap4j cannot rebuild");
    }

    @Test
    public void randomPackets_neverEscape() {
        Random random = new Random(20260807);
        for (int round = 0; round < 20_000; round++) {
            byte[] payload = new byte[random.nextInt(200)];
            random.nextBytes(payload);
            if (payload.length > 11) {
                // Shaped like a DNS header so the parser goes all the way in.
                payload[2] = 0;
                payload[3] = 0;
                payload[4] = 0;
                payload[5] = 1;
                payload[6] = 0;
                payload[7] = (byte) random.nextInt(3);
                payload[8] = 0;
                payload[9] = (byte) random.nextInt(3);
                payload[10] = 0;
                payload[11] = (byte) random.nextInt(3);
            }
            feed(udpOverIpv4(payload), "random payload of " + payload.length + " bytes");
        }
    }

    @Test
    public void randomBytes_neverEscape() {
        Random random = new Random(1);
        for (int round = 0; round < 20_000; round++) {
            byte[] packet = new byte[random.nextInt(120)];
            random.nextBytes(packet);
            if (packet.length > 0) {
                // Claim to be IPv4 or IPv6 so it gets past the first gate.
                packet[0] = (byte) (random.nextBoolean() ? 0x45 : 0x60);
            }
            feed(packet, "random bytes, " + packet.length + " long");
        }
    }

    /**
     * Push a packet through the proxy and fail the test on anything but the documented way out.
     */
    private void feed(byte[] packet, String what) {
        try {
            proxy().handleDnsRequest(packet);
        } catch (IOException e) {
            // The one exception the worker knows how to handle: the network refused the packet.
        } catch (Exception e) {
            fail("handleDnsRequest let a " + e.getClass().getSimpleName()
                    + " escape for " + what + ", which stops the VPN thread: " + e);
        }
    }

    private DnsPacketProxy proxy() {
        return new DnsPacketProxy(new DiscardingEventLoop(), new AlwaysMappingServerMapper());
    }

    /**
     * Wrap a payload in a UDP/IPv4 packet aimed at port 53, which is what makes pcap4j parse the
     * payload as DNS rather than keep it as opaque bytes.
     */
    private static byte[] udpOverIpv4(byte[] payload) {
        try {
            UdpPacket.Builder udp = new UdpPacket.Builder()
                    .srcPort(UdpPort.getInstance((short) 40000))
                    .dstPort(UdpPort.DOMAIN)
                    .srcAddr(InetAddress.getByName("192.0.2.1"))
                    .dstAddr(InetAddress.getByName("192.0.2.2"))
                    .payloadBuilder(new UnknownPacket.Builder().rawData(payload))
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true);
            return new IpV4Packet.Builder()
                    .version(IpVersion.IPV4)
                    .tos(() -> (byte) 0)
                    .protocol(IpNumber.UDP)
                    .srcAddr((Inet4Address) InetAddress.getByName("192.0.2.1"))
                    .dstAddr((Inet4Address) InetAddress.getByName("192.0.2.2"))
                    .payloadBuilder(udp)
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .build()
                    .getRawData();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build the test packet.", e);
        }
    }

    /**
     * Stands in for a configured mapper: configuring a real one needs a live Android context, and
     * all this test needs is for the packet to be recognised as addressed to a mapped resolver.
     */
    private static final class AlwaysMappingServerMapper extends DnsServerMapper {
        @Override
        Optional<InetAddress> getDnsServerFromFakeAddress(InetAddress fakeDnsAddress) {
            try {
                return Optional.of(InetAddress.getByName("192.168.1.1"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Stands in for the worker: accepts everything, fails nothing. */
    private static final class DiscardingEventLoop implements DnsPacketProxy.EventLoop {
        @Override
        public void forwardPacket(DatagramPacket packet) {
            // Nothing to forward to in a unit test.
        }

        @Override
        public void forwardPacket(DatagramPacket packet, Consumer<byte[]> callback) {
            // Nothing to forward to in a unit test.
        }

        @Override
        public void queueDeviceWrite(IpPacket packet) {
            // Serialize it as the worker does, so a failure there is caught here too.
            packet.getRawData();
        }
    }
}
