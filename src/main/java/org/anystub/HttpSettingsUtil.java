package org.anystub;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static org.anystub.HttpGlobalSettings.globalHeaders;

public class HttpSettingsUtil {

    private HttpSettingsUtil() {

    }
    /**
     * returns predicate which determines if a header should be added to stub
     * uses test and global Settings
     *
     * @return Predicate
     */
    public static Predicate<String> filterHeaders() {

        boolean currentAllHeaders = HttpGlobalSettings.globalAllHeaders;

        AnySettingsHttp settings = AnySettingsHttpExtractor.discoverSettings();

        if (settings != null) {
            currentAllHeaders = settings.allHeaders();
        }

        if (currentAllHeaders ) {
            return s -> true;
        }
        Set<String> headersToAdd = new HashSet<>();
        if (settings != null) {
            headersToAdd.addAll(asList(settings.headers()));
        }
        if ((settings == null || !settings.overrideGlobal()) && globalHeaders != null) {
            headersToAdd.addAll(asList(globalHeaders));
        }
        return headersToAdd::contains;

    }
}
