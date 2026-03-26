import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;

public class FingerprintKeyer extends Keyer {

    static final Pattern punctctrl = Pattern.compile(
            "\\p{Punct}|[\\x00-\\x08\\x0E-\\x1F\\x7F\\x80-\\x84\\x86-\\x9F]",
            Pattern.UNICODE_CHARACTER_CLASS);

    public static final Pattern DIACRITICS_AND_FRIENDS = Pattern
            .compile("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");

    protected static final Pattern WHITESPACE = Pattern.compile(
            "\\s+",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern FUNCTION_WORDS = Pattern.compile(
            "\\b(?:a|an|the|of|and|in|on|at|to|for|by|with|from)\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern PARENTHETICALS = Pattern.compile(
            "\\([^)]*\\)",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern DBA_TAIL = Pattern.compile(
            "\\bdba\\b.*$",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern CORPORATION_VARIANTS = Pattern.compile(
            "\\b(?:coporation|cororation|corporaton|corporaiton|corproation|corpration)\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern INCORPORATED_VARIANTS = Pattern.compile(
            "\\b(?:incoporated|incorported|incorprated|incorporatd)\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern COMPANY_VARIANTS = Pattern.compile(
            "\\b(?:compny|comapny|companey)\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern TRANS_VARIANTS = Pattern.compile(
            "\\btrans\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern WEST_VIRGINIA_FULL = Pattern.compile(
            "\\bwest\\s+virginia\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern WEST_VA = Pattern.compile(
            "\\bwest\\s+va\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern W_VA = Pattern.compile(
            "\\bw\\s+va\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern W_V = Pattern.compile(
            "\\bw\\s+v\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern WVA = Pattern.compile(
            "\\bwva\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern WV = Pattern.compile(
            "\\bwv\\b",
            Pattern.UNICODE_CHARACTER_CLASS);

    protected static final Pattern SPLIT_PO = Pattern.compile("\\bp\\s+o\\b", Pattern.UNICODE_CHARACTER_CLASS);
    protected static final Pattern SPLIT_WV = Pattern.compile("\\bw\\s+v\\b", Pattern.UNICODE_CHARACTER_CLASS);
    protected static final Pattern SPLIT_NC = Pattern.compile("\\bn\\s+c\\b", Pattern.UNICODE_CHARACTER_CLASS);
    protected static final Pattern SPLIT_SC = Pattern.compile("\\bs\\s+c\\b", Pattern.UNICODE_CHARACTER_CLASS);
    protected static final Pattern SPLIT_NE = Pattern.compile("\\bn\\s+e\\b", Pattern.UNICODE_CHARACTER_CLASS);
    protected static final Pattern SPLIT_NW = Pattern.compile("\\bn\\s+w\\b", Pattern.UNICODE_CHARACTER_CLASS);
    protected static final Pattern SPLIT_SE = Pattern.compile("\\bs\\s+e\\b", Pattern.UNICODE_CHARACTER_CLASS);
    protected static final Pattern SPLIT_SW = Pattern.compile("\\bs\\s+w\\b", Pattern.UNICODE_CHARACTER_CLASS);

    // Remove from OWNER names only
    protected static final Set<String> OWNER_REMOVABLE_TOKENS = Set.of(
            "co",
            "company",
            "corp",
            "corporation",
            "inc",
            "incorporated",
            "comp",
            "llc",
            "ltd",
            "limited",
            "lp",
            "llp",
            "pllc",
            "plc",
            "pc",
            "int",
            "intl",
            "wv"
    );

    private static final ImmutableMap<String, String> NONDIACRITICS = ImmutableMap.<String, String> builder()
            .put("ß", "ss")
            .put("æ", "ae")
            .put("ø", "oe")
            .put("å", "aa")
            .put("©", "c")
            .put("\u00F0", "d")
            .put("\u0111", "d")
            .put("\u0256", "d")
            .put("\u00FE", "th")
            .put("ƿ", "w")
            .put("\u0127", "h")
            .put("\u0131", "i")
            .put("\u0138", "k")
            .put("\u0142", "l")
            .put("\u014B", "n")
            .put("\u017F", "s")
            .put("\u0167", "t")
            .put("œ", "oe")
            .put("ẜ", "s")
            .put("ẝ", "s")
            .build();

    @Override
    public String key(String s, Object... o) {
        if (s == null || o != null && o.length > 0) {
            throw new IllegalArgumentException("Fingerprint keyer accepts a single string parameter");
        }

        return WHITESPACE.splitAsStream(normalizeOwner(s))
                .filter(token -> !token.isEmpty())
                .sorted()
                .distinct()
                .collect(Collectors.joining(" "));
    }

    protected String normalize(String s) {
        return normalizeOwner(s);
    }

    // -----------------------------
    // PUBLIC NORMALIZERS
    // -----------------------------

    protected String normalizeOwner(String s) {
        s = basicNormalize(s);
        s = removeParentheticals(s);
        s = collapseWhitespace(s);

        s = removeDbaTail(s);
        s = collapseWhitespace(s);

        s = mergeSplitAbbreviations(s);
        s = normalizeCommonTypos(s);
        s = normalizeBusinessDescriptors(s);
        s = normalizeAndRemoveWvVariants(s);

        s = removeFunctionWords(s);
        s = collapseWhitespace(s);

        s = removeOwnerTokens(s);
        s = collapseWhitespace(s);

        return s;
    }

    protected String normalizeGeneric(String s) {
        s = basicNormalize(s);
        s = mergeSplitAbbreviations(s);
        s = collapseWhitespace(s);
        return s;
    }

    protected String normalizeState(String s) {
        s = basicNormalize(s);
        s = mergeSplitAbbreviations(s);
        s = normalizeStateVariantsOnly(s);
        s = collapseWhitespace(s);
        return s;
    }

    // -----------------------------
    // INTERNAL HELPERS
    // -----------------------------

    protected String basicNormalize(String s) {
        if (s == null) {
            return "";
        }

        s = cleanInvisibleCharacters(s);
        s = CharMatcher.whitespace().trimFrom(s);
        s = s.toLowerCase();
        s = stripDiacritics(s);
        s = stripNonDiacritics(s);
        s = replacePunctuationWithSpaces(s);
        s = collapseWhitespace(s);
        return s;
    }

    protected String cleanInvisibleCharacters(String s) {
        return s
                .replace("\uFEFF", "")
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\u2060", "")
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '"')
                .replace('\u201D', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-');
    }

    protected String replacePunctuationWithSpaces(String s) {
        return punctctrl.matcher(s).replaceAll(" ");
    }

    protected String collapseWhitespace(String s) {
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    protected String removeParentheticals(String s) {
        return PARENTHETICALS.matcher(s).replaceAll(" ");
    }

    protected String removeDbaTail(String s) {
        return DBA_TAIL.matcher(s).replaceAll("");
    }

    protected String mergeSplitAbbreviations(String s) {
        s = SPLIT_PO.matcher(s).replaceAll("po");
        s = SPLIT_WV.matcher(s).replaceAll("wv");
        s = SPLIT_NC.matcher(s).replaceAll("nc");
        s = SPLIT_SC.matcher(s).replaceAll("sc");
        s = SPLIT_NE.matcher(s).replaceAll("ne");
        s = SPLIT_NW.matcher(s).replaceAll("nw");
        s = SPLIT_SE.matcher(s).replaceAll("se");
        s = SPLIT_SW.matcher(s).replaceAll("sw");
        return s;
    }

    protected String normalizeCommonTypos(String s) {
        s = CORPORATION_VARIANTS.matcher(s).replaceAll("corporation");
        s = INCORPORATED_VARIANTS.matcher(s).replaceAll("incorporated");
        s = COMPANY_VARIANTS.matcher(s).replaceAll("company");
        return s;
    }

    protected String normalizeBusinessDescriptors(String s) {
        s = TRANS_VARIANTS.matcher(s).replaceAll("transmission");
        return s;
    }

    // For OWNER names: normalize WV forms to "wv", then remove "wv" as token
    protected String normalizeAndRemoveWvVariants(String s) {
        s = normalizeStateVariantsOnly(s);
        return s;
    }

    // For STATE field: normalize to "wv" but DO NOT remove it
    protected String normalizeStateVariantsOnly(String s) {
        s = WEST_VIRGINIA_FULL.matcher(s).replaceAll("wv");
        s = WEST_VA.matcher(s).replaceAll("wv");
        s = W_VA.matcher(s).replaceAll("wv");
        s = W_V.matcher(s).replaceAll("wv");
        s = WVA.matcher(s).replaceAll("wv");
        s = WV.matcher(s).replaceAll("wv");
        return s;
    }

    protected String removeFunctionWords(String s) {
        return FUNCTION_WORDS.matcher(s).replaceAll(" ");
    }

    protected String removeOwnerTokens(String s) {
        return WHITESPACE.splitAsStream(s)
                .filter(token -> !token.isEmpty())
                .filter(token -> !OWNER_REMOVABLE_TOKENS.contains(token))
                .collect(Collectors.joining(" "));
    }

    @Deprecated
    protected String asciify(String s) {
        return normalizeOwner(s);
    }

    protected static String stripDiacritics(String str) {
        str = Normalizer.normalize(str, Normalizer.Form.NFKD);
        str = DIACRITICS_AND_FRIENDS.matcher(str).replaceAll("");
        return str;
    }

    private static String stripNonDiacritics(String orig) {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < orig.length(); i++) {
            String source = orig.substring(i, i + 1);
            String replace = NONDIACRITICS.get(source);
            result.append(replace == null ? source : replace);
        }
        return result.toString();
    }
}