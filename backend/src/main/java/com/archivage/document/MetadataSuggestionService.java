package com.archivage.document;

import com.archivage.document.dto.MetadataSuggestionsDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MetadataSuggestionService {

    private static final int MAX_TEXT = 500_000;
    private static final int MAX_EACH = 20;

    private static final Pattern ISO_DATE = Pattern.compile("\\b(20\\d{2}|19\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])\\b");
    private static final Pattern FR_DATE = Pattern.compile("\\b(0?[1-9]|[12]\\d|3[01])[/.\\-](0?[1-9]|1[0-2])[/.\\-](\\d{2}|\\d{4})\\b");
    private static final Pattern REF = Pattern.compile(
            "(?:Réf|RÉF|Ref|REF|Réference|Reference)[.\\s:]*([A-Za-z0-9/_\\-.]{4,48})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    public MetadataSuggestionsDto suggestFromOcrText(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return new MetadataSuggestionsDto(List.of(), List.of(), List.of());
        }
        String text = ocrText.length() > MAX_TEXT ? ocrText.substring(0, MAX_TEXT) : ocrText;

        Set<String> dates = new LinkedHashSet<>();
        addAll(dates, ISO_DATE, text, 0);
        addFrDates(dates, text);

        Set<String> refs = new LinkedHashSet<>();
        addAll(refs, REF, text, 1);

        Set<String> emails = new LinkedHashSet<>();
        addAll(emails, EMAIL, text, 0);

        return new MetadataSuggestionsDto(
                trimList(dates),
                trimList(refs),
                trimList(emails)
        );
    }

    private void addAll(Set<String> out, Pattern p, String text, int group) {
        Matcher m = p.matcher(text);
        while (m.find() && out.size() < MAX_EACH) {
            String g = group == 0 ? m.group() : m.group(group);
            if (g != null && !g.isBlank()) {
                out.add(g.trim());
            }
        }
    }

    private void addFrDates(Set<String> out, String text) {
        Matcher m = FR_DATE.matcher(text);
        while (m.find() && out.size() < MAX_EACH) {
            String d = m.group(1);
            String mo = m.group(2);
            String y = m.group(3);
            if (y.length() == 2) {
                y = "20" + y;
            }
            try {
                int yi = Integer.parseInt(y);
                int mi = Integer.parseInt(mo);
                int di = Integer.parseInt(d);
                if (mi >= 1 && mi <= 12 && di >= 1 && di <= 31) {
                    out.add(String.format("%04d-%02d-%02d", yi, mi, di));
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }

    private static List<String> trimList(Set<String> s) {
        List<String> list = new ArrayList<>(s);
        return list.size() <= MAX_EACH ? list : list.subList(0, MAX_EACH);
    }
}
