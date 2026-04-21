package com.planno.store.core.util;

import java.text.Normalizer;
import java.util.Locale;

public final class StringNormalizer {

    private StringNormalizer() {
    }

    public static String slugify(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}

