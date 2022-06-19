package org.anystub;

import org.anystub.http.AnySettingsHttp;
import org.anystub.http.AnySettingsHttpExtractor;
import org.anystub.http.HttpUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static org.anystub.http.HttpUtil.globalHeaders;

public class HttpSettingsUtil {

    /**
     * returns predicate to determine if header should be added to stub
     *
     * @return Predicate
     */
    public static Predicate<String> filterHeaders() {

        boolean currentAllHeaders = HttpUtil.globalAllHeaders;

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
