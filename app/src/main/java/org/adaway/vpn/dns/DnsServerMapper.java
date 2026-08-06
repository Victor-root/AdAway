package org.adaway.vpn.dns;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static java.util.Collections.emptyList;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;

import org.adaway.helper.PreferenceHelper;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import timber.log.Timber;

/**
 * This class is in charge of mapping DNS server addresses between network DNS and fake DNS.
 * <p>
 * Fake DNS addresses are registered as VPN interface DNS to capture DNS traffic.
 * Each original DNS server is directly mapped to one fake address.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class DnsServerMapper {
    /**
     * The TEST NET addresses blocks, defined in RFC5735.
     */
    private static final String[] TEST_NET_ADDRESS_BLOCKS = {
            "192.0.2.0/24", // TEST-NET-1
            "198.51.100.0/24", // TEST-NET-2
            "203.0.113.0/24" // TEST-NET-3
    };
    /**
     * This IPv6 address prefix for documentation, defined in RFC3849.
     */
    private static final String IPV6_ADDRESS_PREFIX_RESERVED_FOR_DOCUMENTATION = "2001:db8::/32";
    /**
     * VPN network IPv6 interface prefix length.
     */
    private static final int IPV6_PREFIX_LENGTH = 120;
    /**
     * The public DNS server to fall back to when the active network reports no DNS server.
     * Prevents establishing a tunnel without any resolver, which would break all name resolution.
     */
    private static final String FALLBACK_DNS_SERVER = "1.1.1.1";
    /**
     * The original DNS servers.
     */
    private final List<InetAddress> dnsServers;

    /**
     * Constructor.
     */
    public DnsServerMapper() {
        this.dnsServers = new ArrayList<>();
    }

    /**
     * Configure the VPN.
     * <p>
     * Add interface address per IP family and fake DNS server per system DNS server.
     *
     * @param context The application context.
     * @param builder The builder of the VPN to configure.
     */
    public void configureVpn(Context context, VpnService.Builder builder) {
        // Get DNS servers
        List<InetAddress> dnsServers = getNetworkDnsServers(context);
        // Configure tunnel network address
        Subnet ipv4Subnet = addIpv4Address(builder);
        Subnet ipv6Subnet = hasIpV6DnsServers(context, dnsServers) ? addIpv6Address(builder) : null;
        // Configure DNS mapping
        this.dnsServers.clear();
        for (InetAddress dnsServer : dnsServers) {
            Subnet subnetForDnsServer = dnsServer instanceof Inet4Address ? ipv4Subnet : ipv6Subnet;
            if (subnetForDnsServer == null) {
                continue;
            }
            this.dnsServers.add(dnsServer);
            int serverIndex = this.dnsServers.size();
            InetAddress dnsAddressAlias = subnetForDnsServer.getAddress(serverIndex);
            Timber.i("Mapping DNS server %s as %s.", dnsServer, dnsAddressAlias);
            builder.addDnsServer(dnsAddressAlias);
            if (dnsServer instanceof Inet4Address) {
                builder.addRoute(dnsAddressAlias, 32);
            }
        }
        // Safety net: never establish a tunnel without a resolver. If the active network
        // reported no usable DNS server (e.g. queried during a network transition or right
        // after boot), apps under the VPN would have no DNS at all and lose connectivity
        // until the VPN profile is recreated. Fall back to a public DNS so the tunnel always
        // resolves. This only triggers when no DNS server was mapped above, so the normal
        // path is left untouched.
        if (this.dnsServers.isEmpty()) {
            try {
                InetAddress fallbackDnsServer = InetAddress.getByName(FALLBACK_DNS_SERVER);
                this.dnsServers.add(fallbackDnsServer);
                InetAddress dnsAddressAlias = ipv4Subnet.getAddress(this.dnsServers.size());
                Timber.w("No network DNS server found, falling back to %s mapped as %s.", fallbackDnsServer, dnsAddressAlias);
                builder.addDnsServer(dnsAddressAlias);
                builder.addRoute(dnsAddressAlias, 32);
            } catch (UnknownHostException e) {
                Timber.w(e, "Failed to add fallback DNS server.");
            }
        }
    }

    public InetAddress getDefaultDnsServerAddress() {
        if (this.dnsServers.isEmpty()) {
            try {
                return InetAddress.getByName(FALLBACK_DNS_SERVER);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Failed to parse hardcoded DNS IP address.", e);
            }
        }
        // Return last DNS server added
        return this.dnsServers.get(this.dnsServers.size() - 1);
    }

    /**
     * Get the original DNS server address from fake DNS server address.
     *
     * @param fakeDnsAddress The fake DNS address to get the original DNS server address.
     * @return The original DNS server address, wrapped into an {@link Optional} or {@link Optional#empty()} if it does not exists.
     */
    Optional<InetAddress> getDnsServerFromFakeAddress(InetAddress fakeDnsAddress) {
        byte[] address = fakeDnsAddress.getAddress();
        int index = address[address.length - 1] - 2;
        if (index < 0 || index >= this.dnsServers.size()) {
            return Optional.empty();
        }
        InetAddress dnsAddress = this.dnsServers.get(index);
        Timber.d("handleDnsRequest: Incoming packet to %s AKA %d AKA %s", fakeDnsAddress.getHostAddress(), index, dnsAddress.getHostAddress());
        return Optional.of(dnsAddress);
    }

    /**
     * Get the DNS server addresses from the device networks.
     *
     * @param context The application context.
     * @return The DNS server addresses, an empty collection if no network.
     */
    private List<InetAddress> getNetworkDnsServers(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        dumpNetworkInfo(connectivityManager);
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return getAnyNonVpnNetworkDns(connectivityManager);
        } else if (isNotVpnNetwork(connectivityManager, activeNetwork)) {
            Timber.d("Get DNS servers from active network %s", activeNetwork);
            return getNetworkDnsServers(connectivityManager, activeNetwork);
        } else {
            return getDnsFromNonVpnNetworkWithMatchingTransportType(connectivityManager, activeNetwork);
        }
    }

    /**
     * Dump all network properties to logs.
     *
     * @param connectivityManager The connectivity manager.
     */
    // getAllNetworks() is deprecated in favour of the network callbacks, but those report networks
    // one at a time as they change. Every use below needs the opposite: the full list at this
    // instant, either to log it or to fall back to a non-VPN network once the active one turned
    // out to be unusable. getActiveNetwork() returns a single network and cannot answer that, so
    // there is no non-deprecated replacement for what these methods do.
    @SuppressWarnings("deprecation")
    private void dumpNetworkInfo(ConnectivityManager connectivityManager) {
        Network activeNetwork = connectivityManager.getActiveNetwork();
        Timber.d("Dumping network and dns configuration:");
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            boolean cellular = networkCapabilities != null && networkCapabilities.hasTransport(TRANSPORT_CELLULAR);
            boolean wifi = networkCapabilities != null && networkCapabilities.hasTransport(TRANSPORT_WIFI);
            boolean vpn = networkCapabilities != null && networkCapabilities.hasTransport(TRANSPORT_VPN);
            LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            String dnsList = linkProperties == null ? "none" : linkProperties.getDnsServers()
                    .stream()
                    .map(InetAddress::toString)
                    .collect(Collectors.joining(", "));
            Timber.d(
                    "Network %s %s: %s%s%s with dns %s",
                    network,
                    network.equals(activeNetwork) ? "[default]" : "[other]",
                    cellular ? "cellular" : "",
                    wifi ? "WiFi" : "",
                    vpn ? " VPN" : "",
                    dnsList);
        }
    }

    /**
     * Get the DNS server addresses of any network without VPN capability.
     *
     * @param connectivityManager The connectivity manager.
     * @return The DNS server addresses, an empty collection if no applicable DNS server found.
     */
    @SuppressWarnings("deprecation") // getAllNetworks(), see dumpNetworkInfo().
    private List<InetAddress> getAnyNonVpnNetworkDns(ConnectivityManager connectivityManager) {
        for (Network network : connectivityManager.getAllNetworks()) {
            if (isNotVpnNetwork(connectivityManager, network)) {
                List<InetAddress> dnsServers = getNetworkDnsServers(connectivityManager, network);
                if (!dnsServers.isEmpty()) {
                    Timber.d("Get DNS servers from non VPN network %s", network);
                    return dnsServers;
                }
            }
        }
        return emptyList();
    }

    /**
     * Get the DNS server addresses of a network with the same transport type as the active network except VPN.
     *
     * @param connectivityManager The connectivity manager.
     * @param activeNetwork       The active network to filter similar transport type.
     * @return The DNS server addresses, an empty collection if no applicable DNS server found.
     */
    @SuppressWarnings("deprecation") // getAllNetworks(), see dumpNetworkInfo().
    private List<InetAddress> getDnsFromNonVpnNetworkWithMatchingTransportType(
            ConnectivityManager connectivityManager,
            Network activeNetwork
    ) {
        // Get active network transport
        NetworkCapabilities activeNetworkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (activeNetworkCapabilities == null) {
            return emptyList();
        }
        int activeNetworkTransport = -1;
        if (activeNetworkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
            activeNetworkTransport = TRANSPORT_CELLULAR;
        } else if (activeNetworkCapabilities.hasTransport(TRANSPORT_WIFI)) {
            activeNetworkTransport = TRANSPORT_WIFI;
        }
        // Check all network to find one without VPN and matching transport
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            if (networkCapabilities == null) {
                continue;
            }
            if (networkCapabilities.hasTransport(activeNetworkTransport) && !networkCapabilities.hasTransport(TRANSPORT_VPN)) {
                List<InetAddress> dns = getNetworkDnsServers(connectivityManager, network);
                if (!dns.isEmpty()) {
                    Timber.d("Get DNS servers from non VPN matching type network %s", network);
                    return dns;
                }
            }
        }
        return emptyList();
    }

    /**
     * Get the DNS server addresses of a network.
     *
     * @param connectivityManager The connectivity manager.
     * @param network             The network to get DNS server addresses.
     * @return The DNS server addresses, an empty collection if no network.
     */
    private List<InetAddress> getNetworkDnsServers(ConnectivityManager connectivityManager, Network network) {
        LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties == null) {
            return emptyList();
        }
        return linkProperties.getDnsServers();
    }

    /**
     * Check a network does not have VPN transport.
     *
     * @param connectivityManager The connectivity manager.
     * @param network             The network to check.
     * @return <code>true</code> if a network is not a VPN, <code>false</code> otherwise.
     */
    private boolean isNotVpnNetwork(ConnectivityManager connectivityManager, Network network) {
        if (network == null) {
            return false;
        }
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
        return networkCapabilities != null && !networkCapabilities.hasTransport(TRANSPORT_VPN);
    }

    /**
     * Add IPv4 network address to the VPN.
     *
     * @param builder The build of the VPN to configure.
     * @return The IPv4 address of the VPN network.
     */
    private Subnet addIpv4Address(VpnService.Builder builder) {
        for (String addressBlock : TEST_NET_ADDRESS_BLOCKS) {
            try {
                Subnet subnet = Subnet.parse(addressBlock);
                InetAddress address = subnet.getAddress(0);
                builder.addAddress(address, subnet.prefixLength);
                Timber.d("Set %s as IPv4 network address to tunnel interface.", address);
                return subnet;
            } catch (IllegalArgumentException e) {
                Timber.w(e, "Failed to add %s network address to tunnel interface.", addressBlock);
            }
        }
        throw new IllegalStateException("Failed to add any IPv4 address for TEST-NET to tunnel interface.");
    }

    /**
     * Add IPv6 network address to the VPN.
     *
     * @param builder The build of the VPN to configure.
     * @return The IPv4 address of the VPN network.
     */
    private Subnet addIpv6Address(VpnService.Builder builder) {
        Subnet subnet = Subnet.parse(IPV6_ADDRESS_PREFIX_RESERVED_FOR_DOCUMENTATION);
        builder.addAddress(subnet.address, IPV6_PREFIX_LENGTH);
        Timber.d("Set %s as IPv6 network address to tunnel interface.", subnet.address);
        return subnet;
    }


    /**
     * Compute the DNS servers the tunnel would actually forward to for a raw network DNS list, the
     * same set {@link #configureVpn} maps at establish time, so callers can tell whether a network
     * DNS change actually affects the tunnel before rebuilding it. It applies the very same IPv6
     * rule as {@link #configureVpn}: IPv4 servers are always kept, while IPv6 servers are kept only
     * when {@link #hasIpV6DnsServers} would add an IPv6 subnet (IPv6 enabled, or a single-server
     * network), so an IPv6 resolver churn on a dual-stack network does not needlessly rebuild an
     * IPv4-only tunnel. When the raw list holds nothing the tunnel can use, it returns the public
     * fallback resolver, exactly what {@link #configureVpn} establishes in that case, so the two
     * stay in lockstep and a drop to "no usable resolver" is still detected as a change rather than
     * silently leaving the tunnel pinned to a resolver that is gone.
     *
     * @param context    The application context.
     * @param dnsServers The raw DNS servers reported by the network.
     * @return The effective DNS servers, in input order; never empty (the public fallback stands in
     * when the raw list holds only servers the tunnel would drop).
     */
    public static List<InetAddress> getEffectiveDnsServers(Context context, List<InetAddress> dnsServers) {
        boolean keepIpv6 = hasIpV6DnsServers(context, dnsServers);
        List<InetAddress> effectiveDnsServers = new ArrayList<>();
        for (InetAddress dnsServer : dnsServers) {
            if (dnsServer instanceof Inet4Address || keepIpv6) {
                effectiveDnsServers.add(dnsServer);
            }
        }
        if (effectiveDnsServers.isEmpty()) {
            // Mirror configureVpn's safety net: with no usable resolver the tunnel forwards to the
            // public fallback, so report that here too and keep detection in lockstep with establish.
            effectiveDnsServers.add(getFallbackDnsServer());
        }
        return effectiveDnsServers;
    }

    private static InetAddress getFallbackDnsServer() {
        try {
            return InetAddress.getByName(FALLBACK_DNS_SERVER);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Failed to parse hardcoded DNS IP address.", e);
        }
    }

    private static boolean hasIpV6DnsServers(Context context, Collection<InetAddress> dnsServers) {
        boolean hasIpv6Server = dnsServers.stream()
                .anyMatch(server -> server instanceof Inet6Address);
        boolean hasOnlyOnServer = dnsServers.size() == 1;
        boolean isIpv6Enabled = PreferenceHelper.getEnableIpv6(context);
        return (isIpv6Enabled || hasOnlyOnServer) && hasIpv6Server;
    }
}
