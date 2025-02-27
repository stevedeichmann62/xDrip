package com.eveningoutpost.dexdrip.cgm.carelinkfollow.client;

import java.util.Arrays;

public class CountryUtils {

    public static final String[] supportedCountryCodes = {
            "dz",
            "ba",
            "eg",
            "za",
            "ca",
            "cr",
            "mx",
            "ma",
            "pa",
            "pr",
            "us",
            "ar",
            "br",
            "cl",
            "co",
            "ve",
            "hk",
            "in",
            "id",
            "il",
            "jp",
            "kw",
            "lb",
            "my",
            "ph",
            "qa",
            "sa",
            "sg",
            "kr",
            "tw",
            "th",
            "tn",
            "tr",
            "ae",
            "vn",
            "at",
            "be",
            "bg",
            "hr",
            "cz",
            "dk",
            "ee",
            "fi",
            "fr",
            "de",
            "gr",
            "hu",
            "is",
            "ie",
            "it",
            "lv",
            "lt",
            "lu",
            "nl",
            "no",
            "pl",
            "pt",
            "ro",
            "ru",
            "rs",
            "sk",
            "si",
            "es",
            "se",
            "ch",
            "ua",
            "gb",
            "au",
            "nz",
            "bh",
            "om",
            "cn",
            "cy",
            "al",
            "am",
            "az",
            "bs",
            "bb",
            "by",
            "bm",
            "bo",
            "kh",
            "do",
            "ec",
            "sv",
            "ge",
            "gt",
            "hn",
            "ir",
            "iq",
            "jo",
            "xk",
            "ly",
            "mo",
            "mk",
            "mv",
            "mt",
            "mu",
            "yt",
            "md",
            "me",
            "na",
            "nc",
            "ni",
            "ng",
            "pk",
            "py",
            "mf",
            "sd",
            "uy",
            "aw",
            "ky",
            "cw",
            "pe"
    };

    public static boolean isSupportedCountry(String countryCode) {
        return Arrays.asList(supportedCountryCodes).contains(countryCode);
    }

    public static boolean isUS(String countryCode) {
        return countryCode.equals("us");
    }

}
