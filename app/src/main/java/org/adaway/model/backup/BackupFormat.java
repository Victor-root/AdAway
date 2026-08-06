package org.adaway.model.backup;

import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.HostsSource;
import org.adaway.util.RegexUtils;
import org.json.JSONException;
import org.json.JSONObject;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;

/**
 * This class defines user lists and hosts sources file format.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
final class BackupFormat {
    /*
     * Source backup format.
     */
    static final String SOURCES_KEY = "sources";
    static final String SOURCE_LABEL_ATTRIBUTE = "label";
    static final String SOURCE_URL_ATTRIBUTE = "url";
    static final String SOURCE_ENABLED_ATTRIBUTE = "enabled";
    static final String SOURCE_ALLOW_ATTRIBUTE = "allow";
    static final String SOURCE_REDIRECT_ATTRIBUTE = "redirect";
    /*
     * User source backup format.
     */
    static final String BLOCKED_KEY = "blocked";
    static final String ALLOWED_KEY = "allowed";
    static final String REDIRECTED_KEY = "redirected";
    static final String ENABLED_ATTRIBUTE = "enabled";
    static final String HOST_ATTRIBUTE = "host";
    static final String REDIRECT_ATTRIBUTE = "redirect";

    BackupFormat() {

    }

    static JSONObject sourceToJson(HostsSource source) throws JSONException {
        JSONObject sourceObject = new JSONObject();
        sourceObject.put(SOURCE_LABEL_ATTRIBUTE, source.getLabel());
        sourceObject.put(SOURCE_URL_ATTRIBUTE, source.getUrl());
        sourceObject.put(SOURCE_ENABLED_ATTRIBUTE, source.isEnabled());
        sourceObject.put(SOURCE_ALLOW_ATTRIBUTE, source.isAllowEnabled());
        sourceObject.put(SOURCE_REDIRECT_ATTRIBUTE, source.isRedirectEnabled());
        return sourceObject;
    }

    static HostsSource sourceFromJson(JSONObject sourceObject) throws JSONException {
        HostsSource source = new HostsSource();
        // A backup file can come from anyone. Both of these end up written verbatim into the
        // generated hosts file, which in root mode is installed as /system/etc/hosts, so a
        // value carrying a line break would inject arbitrary host entries there.
        String label = sourceObject.getString(SOURCE_LABEL_ATTRIBUTE);
        if (containsLineBreak(label)) {
            throw new JSONException("Invalid source label: " + label);
        }
        source.setLabel(label);
        String url = sourceObject.getString(SOURCE_URL_ATTRIBUTE);
        if (!HostsSource.isValidUrl(url) || containsLineBreak(url)) {
            throw new JSONException("Invalid source URL: "+url);
        }
        source.setUrl(url);
        source.setEnabled(sourceObject.getBoolean(SOURCE_ENABLED_ATTRIBUTE));
        source.setAllowEnabled(sourceObject.getBoolean(SOURCE_ALLOW_ATTRIBUTE));
        source.setRedirectEnabled(sourceObject.getBoolean(SOURCE_REDIRECT_ATTRIBUTE));
        return source;
    }

    static JSONObject hostToJson(HostListItem host) throws JSONException {
        JSONObject hostObject = new JSONObject();
        hostObject.put(HOST_ATTRIBUTE, host.getHost());
        String redirection = host.getRedirection();
        if (redirection != null && !redirection.isEmpty()) {
            hostObject.put(REDIRECT_ATTRIBUTE, redirection);
        }
        hostObject.put(ENABLED_ATTRIBUTE, host.isEnabled());
        return hostObject;
    }

    static HostListItem hostFromJson(JSONObject hostObject) throws JSONException {
        HostListItem host = new HostListItem();
        // Same trust boundary as sourceFromJson: these two land in the hosts file, so they are
        // held to the same format the app enforces when the user types them in by hand.
        // Wildcards are accepted because allowed entries legitimately use them.
        String hostname = hostObject.getString(HOST_ATTRIBUTE);
        if (!RegexUtils.isValidWildcardHostname(hostname)) {
            throw new JSONException("Invalid host name: " + hostname);
        }
        host.setHost(hostname);
        if (hostObject.has(REDIRECT_ATTRIBUTE)) {
            String redirection = hostObject.getString(REDIRECT_ATTRIBUTE);
            if (!RegexUtils.isValidIP(redirection)) {
                throw new JSONException("Invalid redirection: " + redirection);
            }
            host.setRedirection(redirection);
        }
        host.setEnabled(hostObject.getBoolean(ENABLED_ATTRIBUTE));
        host.setSourceId(USER_SOURCE_ID);
        return host;
    }

    /**
     * Check whether a value carries a line break, which would let it inject additional lines
     * into the generated hosts file.
     *
     * @param value The value to check.
     * @return <code>true</code> if the value contains a carriage return or a line feed.
     */
    private static boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0);
    }
}
