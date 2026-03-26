import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Fingerprint keyer which generates a fingerprint from a sorted list of unique character N-grams after normalization.
 * Default N is 2.
 */
public class NGramFingerprintKeyer extends FingerprintKeyer {

    static final Pattern ctrlspace = Pattern.compile("[\\p{Cntrl}\\p{Space}]",
            Pattern.UNICODE_CHARACTER_CLASS);

    @Override
    public String key(String s, Object... o) {
        if (s == null) {
            throw new IllegalArgumentException("NGram fingerprint keyer accepts a string parameter");
        }

        int ngramSize = 2;
        if (o != null && o.length > 0) {
            if (!(o[0] instanceof Number)) {
                throw new IllegalArgumentException("Optional ngram size parameter must be numeric");
            }
            ngramSize = ((Number) o[0]).intValue();
        }

        s = normalizeOwner(s);

        // Remove all remaining whitespace/control chars after normalization
        s = ctrlspace.matcher(s).replaceAll("");

        if (ngramSize <= 0) {
            throw new IllegalArgumentException("NGram size must be greater than zero");
        }

        if (s.length() < ngramSize) {
            return s;
        }

        return sortedNGrams(s, ngramSize).collect(Collectors.joining());
    }

    /**
     * Generate a stream of sorted unique character N-grams from a string.
     *
     * @param s
     *            String to generate N-grams from
     * @param size
     *            number of characters per N-gram
     * @return a stream of sorted unique N-gram Strings
     */
    protected Stream<String> sortedNGrams(String s, int size) {
        return IntStream.rangeClosed(0, s.length() - size)
                .mapToObj(i -> s.substring(i, i + size))
                .sorted()
                .distinct();
    }

    /**
     * @deprecated 2020-10-17 by tfmorris. Use {@link #sortedNGrams(String, int)}
     */
    @Deprecated
    protected TreeSet<String> ngram_split(String s, int size) {
        TreeSet<String> set = new TreeSet<String>();
        int length = s.length();
        for (int i = 0; i + size <= length; i++) {
            set.add(s.substring(i, i + size));
        }
        return set;
    }
}