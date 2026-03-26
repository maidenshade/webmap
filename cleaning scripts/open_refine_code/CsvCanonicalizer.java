import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

/**
 * Canonicalizes owner names from a CSV using type-specific blocking and merge rules.
 *
 * Blocking:
 * - STRICT types ("other", "heirs"): owner + city + state
 * - LOOSE types ("general corporate", "natural resource corporation",
 *   "investors", "banking and finance"): owner + state
 *
 * Output:
 * - augmented row-level CSV
 * - review CSV for inspection
 * - cluster summary CSV (one row per cluster)
 *
 * Usage:
 * java CsvCanonicalizer
 *   input.csv
 *   output.csv
 *   review.csv
 *   cluster_summary.csv
 *   owner
 *   canon_owner
 *   city
 *   state
 *   owner_type_code
 *   [ngramSize]
 */
public class CsvCanonicalizer {

    // ---------- thresholds ----------
    private static final double STRICT_AUTO_THRESHOLD = 0.90;
    private static final double STRICT_REVIEW_THRESHOLD = 0.85;

    private static final double LOOSE_SAME_CITY_THRESHOLD = 0.90;
    private static final double LOOSE_CROSS_CITY_THRESHOLD = 0.95;

    private static final int MIN_DISTINCT_CITIES_FOR_CROSS_CITY = 3;

    // ---------- owner type groups ----------
    private static final Set<String> STRICT_TYPES = Set.of(
        "other",
        "heirs"
);

private static final Set<String> LOOSE_TYPES = Set.of(
        "general corporate",
        "natural resource corporation",
        "investors",
        "banking and finance"
);

    static class RowData {
        int rowNumber;
        Map<String, String> values = new LinkedHashMap<>();

        String ownerOriginal;
        String cityOriginal;
        String stateOriginal;
        String ownerTypeOriginal;

        String ownerNormalized;
        String cityNormalized;
        String stateNormalized;
        String ownerTypeNormalized;

        String compositeNormalized;
        String clusterKey;
        String clusterId;
        int clusterSize;

        String canonicalOwner;

        double ownerSimilarity;
        boolean sameCityFlag;
        String mergeDecision;
        String needsReview;
        int scopeCityCount;
    }

    static class ClusterData {
        String clusterKey;
        String clusterId;

        List<Integer> rowIndexes = new ArrayList<>();
        Map<String, Integer> ownerCounts = new HashMap<>();

        Set<String> distinctOwners = new LinkedHashSet<>();
        Set<String> distinctOwnerNorms = new LinkedHashSet<>();
        Set<String> distinctCities = new LinkedHashSet<>();
        Set<String> distinctStates = new LinkedHashSet<>();
        Set<String> distinctOwnerTypes = new LinkedHashSet<>();

        String canonicalOwner;
        String canonicalOwnerNorm;
        String dominantOwnerType;
        int scopeCityCount;
        String representativeCityNorm;
        String blockingMode;
    }

    static class OwnerScopeStats {
        String stateNorm;
        String ownerNorm;
        Set<String> distinctCities = new LinkedHashSet<>();
    }

    private static class MergeEvaluation {
        String mergeDecision;
        boolean needsReview;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 10) {
            System.err.println("Usage:");
            System.err.println("  java CsvCanonicalizer <input.csv> <output.csv> <review.csv> <clusterSummary.csv> <ownerColumn> <canonicalColumn> <cityColumn> <stateColumn> <ownerTypeColumn> [ngramSize]");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  java CsvCanonicalizer owners.csv owners_out.csv owners_review.csv owners_clusters.csv owner canon_owner city state owner_type_code 2");
            System.exit(1);
        }

        Path inputPath = Paths.get(args[0]);
        Path outputPath = Paths.get(args[1]);
        Path reviewPath = Paths.get(args[2]);
        Path clusterSummaryPath = Paths.get(args[3]);

        String ownerColumn = args[4];
        String canonicalColumn = args[5];
        String cityColumn = args[6];
        String stateColumn = args[7];
        String ownerTypeColumn = args[8];
        int ngramSize = (args.length >= 10) ? Integer.parseInt(args[9]) : 2;

        canonicalizeCsv(
                inputPath,
                outputPath,
                reviewPath,
                clusterSummaryPath,
                ownerColumn,
                canonicalColumn,
                cityColumn,
                stateColumn,
                ownerTypeColumn,
                ngramSize
        );
    }

    public static void canonicalizeCsv(
            Path inputPath,
            Path outputPath,
            Path reviewPath,
            Path clusterSummaryPath,
            String ownerColumn,
            String canonicalColumn,
            String cityColumn,
            String stateColumn,
            String ownerTypeColumn,
            int ngramSize) throws IOException {

        NGramFingerprintKeyer ngramKeyer = new NGramFingerprintKeyer();
        FingerprintKeyer wordKeyer = new FingerprintKeyer();

        List<RowData> rows = new ArrayList<>();
        Map<String, ClusterData> clusters = new LinkedHashMap<>();
        Map<String, OwnerScopeStats> ownerScopeMap = new HashMap<>();
        List<String> headers;

        try (Reader reader = Files.newBufferedReader(inputPath);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(false)
                     .setTrim(false)
                     .build()
                     .parse(reader)) {

            headers = new ArrayList<>(parser.getHeaderNames());
            System.out.println("Headers found: " + headers);

           ownerColumn = resolveColumnName(headers, ownerColumn);
           cityColumn = resolveColumnName(headers, cityColumn);
           stateColumn = resolveColumnName(headers, stateColumn);
           ownerTypeColumn = resolveColumnName(headers, ownerTypeColumn);

            int rowIndex = 0;
            int clusterCounter = 1;

            for (CSVRecord record : parser) {
                RowData row = new RowData();
                row.rowNumber = rowIndex + 1;

                for (String header : headers) {
                    row.values.put(header, record.get(header));
                }

                row.ownerOriginal = safeString(record.get(ownerColumn));
                row.cityOriginal = safeString(record.get(cityColumn));
                row.stateOriginal = safeString(record.get(stateColumn));
                row.ownerTypeOriginal = safeString(record.get(ownerTypeColumn));

                row.ownerNormalized = wordKeyer.normalizeOwner(row.ownerOriginal);
                row.cityNormalized = wordKeyer.normalizeGeneric(row.cityOriginal);
                row.stateNormalized = wordKeyer.normalizeState(row.stateOriginal);
                row.ownerTypeNormalized = normalizeOwnerType(row.ownerTypeOriginal);

                row.compositeNormalized = buildCompositeForType(
                        row.ownerNormalized,
                        row.cityNormalized,
                        row.stateNormalized,
                        row.ownerTypeNormalized
                );

                row.clusterKey = ngramKeyer.key(row.compositeNormalized, ngramSize);

                ClusterData cluster = clusters.get(row.clusterKey);
                if (cluster == null) {
                    cluster = new ClusterData();
                    cluster.clusterKey = row.clusterKey;
                    cluster.clusterId = String.format("CL%06d", clusterCounter++);
                    cluster.blockingMode = blockingModeForType(row.ownerTypeNormalized);
                    clusters.put(row.clusterKey, cluster);
                }

                cluster.rowIndexes.add(rowIndex);

                if (!row.ownerOriginal.isBlank()) {
                    cluster.ownerCounts.merge(row.ownerOriginal, 1, Integer::sum);
                    cluster.distinctOwners.add(row.ownerOriginal);
                }
                if (!row.ownerNormalized.isBlank()) {
                    cluster.distinctOwnerNorms.add(row.ownerNormalized);
                }
                if (!row.cityOriginal.isBlank()) {
                    cluster.distinctCities.add(row.cityOriginal);
                }
                if (!row.stateOriginal.isBlank()) {
                    cluster.distinctStates.add(row.stateOriginal);
                }
                if (!row.ownerTypeNormalized.isBlank()) {
                    cluster.distinctOwnerTypes.add(row.ownerTypeNormalized);
                }

                String ownerScopeKey = row.stateNormalized + "||" + row.ownerNormalized;
                OwnerScopeStats stats = ownerScopeMap.computeIfAbsent(ownerScopeKey, k -> {
                    OwnerScopeStats s = new OwnerScopeStats();
                    s.stateNorm = row.stateNormalized;
                    s.ownerNorm = row.ownerNormalized;
                    return s;
                });
                if (!row.cityNormalized.isBlank()) {
                    stats.distinctCities.add(row.cityNormalized);
                }

                rows.add(row);
                rowIndex++;
            }
        }

        // finalize cluster-level attributes
        for (ClusterData cluster : clusters.values()) {
            cluster.canonicalOwner = chooseCanonicalValue(cluster.ownerCounts);
            cluster.canonicalOwnerNorm = wordKeyer.normalizeOwner(cluster.canonicalOwner);
            cluster.dominantOwnerType = chooseDominantOwnerType(cluster, rows);
            cluster.representativeCityNorm = mostCommonCityNorm(cluster, rows);

            int maxScopeCityCount = 0;
            for (Integer rowIndex : cluster.rowIndexes) {
                RowData row = rows.get(rowIndex);
                String ownerScopeKey = row.stateNormalized + "||" + row.ownerNormalized;
                OwnerScopeStats scopeStats = ownerScopeMap.get(ownerScopeKey);
                int cityCount = scopeStats == null ? 0 : scopeStats.distinctCities.size();
                if (cityCount > maxScopeCityCount) {
                    maxScopeCityCount = cityCount;
                }
                row.scopeCityCount = cityCount;
            }
            cluster.scopeCityCount = maxScopeCityCount;

            for (Integer rowIndex : cluster.rowIndexes) {
                RowData row = rows.get(rowIndex);
                row.canonicalOwner = cluster.canonicalOwner;
                row.clusterId = cluster.clusterId;
                row.clusterSize = cluster.rowIndexes.size();
            }
        }

        // evaluate row-level merge decisions
        for (ClusterData cluster : clusters.values()) {
            for (Integer rowIndex : cluster.rowIndexes) {
                RowData row = rows.get(rowIndex);

                row.ownerSimilarity = diceCoefficientBigrams(
                        row.ownerNormalized,
                        cluster.canonicalOwnerNorm
                );

                row.sameCityFlag = safeEquals(
                        row.cityNormalized,
                        cluster.representativeCityNorm
                );

                MergeEvaluation eval = evaluateMergeDecision(
                        row.cityNormalized,
                        cluster.representativeCityNorm,
                        row.ownerSimilarity,
                        cluster.dominantOwnerType,
                        row.scopeCityCount
                );

                row.mergeDecision = eval.mergeDecision;
                row.needsReview = eval.needsReview ? "Y" : "";
            }
        }

        writeAugmentedCsv(outputPath, headers, rows, canonicalColumn);
        writeReviewCsv(reviewPath, rows);
        writeClusterSummaryCsv(clusterSummaryPath, clusters.values(), rows);
    }

    // ------------------------------------------------------------
    // Blocking logic
    // ------------------------------------------------------------

    private static String buildCompositeForType(
            String ownerNorm,
            String cityNorm,
            String stateNorm,
            String ownerTypeNorm) {

        if (LOOSE_TYPES.contains(ownerTypeNorm)) {
            // corporate/investor/banking types can match across cities
            return ownerNorm + " | " + stateNorm;
        }

        // heirs/other/default stay city-specific
        return ownerNorm + " | " + cityNorm + " | " + stateNorm;
    }

    private static String blockingModeForType(String ownerTypeNorm) {
        if (LOOSE_TYPES.contains(ownerTypeNorm)) {
            return "OWNER_STATE";
        }
        return "OWNER_CITY_STATE";
    }

    // ------------------------------------------------------------
    // Merge decision logic
    // ------------------------------------------------------------

    private static MergeEvaluation evaluateMergeDecision(
            String rowCityNorm,
            String canonicalCityNorm,
            double ownerSimilarity,
            String ownerTypeNorm,
            int scopeCityCount) {

        MergeEvaluation eval = new MergeEvaluation();

        boolean sameCity = safeEquals(rowCityNorm, canonicalCityNorm);
        boolean strictType = STRICT_TYPES.contains(ownerTypeNorm);
        boolean looseType = LOOSE_TYPES.contains(ownerTypeNorm);

        if (strictType) {
            if (sameCity && ownerSimilarity >= STRICT_AUTO_THRESHOLD) {
                eval.mergeDecision = "AUTO_MERGE_STRICT";
                eval.needsReview = false;
                return eval;
            }
            if (sameCity && ownerSimilarity >= STRICT_REVIEW_THRESHOLD) {
                eval.mergeDecision = "REVIEW_STRICT";
                eval.needsReview = true;
                return eval;
            }

            eval.mergeDecision = "NO_MERGE_STRICT";
            eval.needsReview = false;
            return eval;
        }

        if (looseType) {
            if (sameCity && ownerSimilarity >= LOOSE_SAME_CITY_THRESHOLD) {
                eval.mergeDecision = "AUTO_MERGE_LOOSE_SAME_CITY";
                eval.needsReview = false;
                return eval;
            }

            if (!sameCity
                    && ownerSimilarity >= LOOSE_CROSS_CITY_THRESHOLD
                    && scopeCityCount >= MIN_DISTINCT_CITIES_FOR_CROSS_CITY) {
                eval.mergeDecision = "AUTO_MERGE_LOOSE_CROSS_CITY";
                eval.needsReview = false;
                return eval;
            }

            if (!sameCity
                    && ownerSimilarity >= STRICT_AUTO_THRESHOLD
                    && ownerSimilarity < LOOSE_CROSS_CITY_THRESHOLD) {
                eval.mergeDecision = "REVIEW_LOOSE_CROSS_CITY";
                eval.needsReview = true;
                return eval;
            }

            eval.mergeDecision = "NO_MERGE_LOOSE";
            eval.needsReview = false;
            return eval;
        }

        // fallback: conservative default
        if (sameCity && ownerSimilarity >= STRICT_AUTO_THRESHOLD) {
            eval.mergeDecision = "AUTO_MERGE_DEFAULT";
            eval.needsReview = false;
            return eval;
        }
        if (sameCity && ownerSimilarity >= STRICT_REVIEW_THRESHOLD) {
            eval.mergeDecision = "REVIEW_DEFAULT";
            eval.needsReview = true;
            return eval;
        }

        eval.mergeDecision = "NO_MERGE_DEFAULT";
        eval.needsReview = false;
        return eval;
    }

    // ------------------------------------------------------------
    // Canonical choice
    // ------------------------------------------------------------

    /**
     * Canonical owner = most frequent original value.
     * Ties: longer trimmed value, then alphabetical.
     */
    private static String chooseCanonicalValue(Map<String, Integer> valueCounts) {
        if (valueCounts.isEmpty()) {
            return "";
        }

        return valueCounts.entrySet().stream()
                .max(
                        Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                .thenComparingInt(e -> e.getKey().trim().length())
                                .thenComparing(Map.Entry::getKey)
                )
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static String chooseDominantOwnerType(ClusterData cluster, List<RowData> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (Integer idx : cluster.rowIndexes) {
            String t = rows.get(idx).ownerTypeNormalized;
            if (!t.isBlank()) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "";
        }

        return counts.entrySet().stream()
                .max(
                        Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                .thenComparing(Map.Entry::getKey)
                )
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static String mostCommonCityNorm(ClusterData cluster, List<RowData> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (Integer idx : cluster.rowIndexes) {
            String city = rows.get(idx).cityNormalized;
            if (!city.isBlank()) {
                counts.merge(city, 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return "";
        }

        return counts.entrySet().stream()
                .max(
                        Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                .thenComparing(Map.Entry::getKey)
                )
                .map(Map.Entry::getKey)
                .orElse("");
    }

    // ------------------------------------------------------------
    // Similarity
    // ------------------------------------------------------------

    /**
     * Dice coefficient over character bigrams.
     * Returns 1.0 for exact match, 0.0 for no overlap.
     */
    private static double diceCoefficientBigrams(String a, String b) {
        a = emptySafe(a);
        b = emptySafe(b);

        if (a.equals(b)) {
            return 1.0;
        }
        if (a.length() < 2 || b.length() < 2) {
            return 0.0;
        }

        Map<String, Integer> aBigrams = bigramCounts(a);
        Map<String, Integer> bBigrams = bigramCounts(b);

        int intersection = 0;
        int totalA = 0;
        int totalB = 0;

        for (int c : aBigrams.values()) {
            totalA += c;
        }
        for (int c : bBigrams.values()) {
            totalB += c;
        }

        for (String gram : aBigrams.keySet()) {
            if (bBigrams.containsKey(gram)) {
                intersection += Math.min(aBigrams.get(gram), bBigrams.get(gram));
            }
        }

        return (2.0 * intersection) / (totalA + totalB);
    }

    private static Map<String, Integer> bigramCounts(String s) {
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < s.length() - 1; i++) {
            String gram = s.substring(i, i + 2);
            counts.merge(gram, 1, Integer::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------
    // CSV writers
    // ------------------------------------------------------------

    private static void writeAugmentedCsv(
            Path outputPath,
            List<String> originalHeaders,
            List<RowData> rows,
            String canonicalColumn) throws IOException {

        List<String> outputHeaders = new ArrayList<>(originalHeaders);

        addIfMissing(outputHeaders, canonicalColumn);
        addIfMissing(outputHeaders, "owner_cluster_key");
        addIfMissing(outputHeaders, "owner_cluster_id");
        addIfMissing(outputHeaders, "owner_cluster_size");
        addIfMissing(outputHeaders, "owner_norm");
        addIfMissing(outputHeaders, "city_norm");
        addIfMissing(outputHeaders, "state_norm");
        addIfMissing(outputHeaders, "cluster_input_norm");
        addIfMissing(outputHeaders, "owner_similarity");
        addIfMissing(outputHeaders, "same_city_flag");
        addIfMissing(outputHeaders, "merge_decision");
        addIfMissing(outputHeaders, "needs_review");
        addIfMissing(outputHeaders, "scope_city_count");

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .builder()
                     .setHeader(outputHeaders.toArray(new String[0]))
                     .build())) {

            for (RowData row : rows) {
                List<String> out = new ArrayList<>(Collections.nCopies(outputHeaders.size(), ""));

                for (String header : originalHeaders) {
                    int idx = outputHeaders.indexOf(header);
                    out.set(idx, row.values.getOrDefault(header, ""));
                }

                setValue(outputHeaders, out, canonicalColumn, row.canonicalOwner);
                setValue(outputHeaders, out, "owner_cluster_key", row.clusterKey);
                setValue(outputHeaders, out, "owner_cluster_id", row.clusterId);
                setValue(outputHeaders, out, "owner_cluster_size", Integer.toString(row.clusterSize));
                setValue(outputHeaders, out, "owner_norm", row.ownerNormalized);
                setValue(outputHeaders, out, "city_norm", row.cityNormalized);
                setValue(outputHeaders, out, "state_norm", row.stateNormalized);
                setValue(outputHeaders, out, "cluster_input_norm", row.compositeNormalized);
                setValue(outputHeaders, out, "owner_similarity", String.format(Locale.US, "%.4f", row.ownerSimilarity));
                setValue(outputHeaders, out, "same_city_flag", row.sameCityFlag ? "Y" : "");
                setValue(outputHeaders, out, "merge_decision", row.mergeDecision);
                setValue(outputHeaders, out, "needs_review", row.needsReview);
                setValue(outputHeaders, out, "scope_city_count", Integer.toString(row.scopeCityCount));

                printer.printRecord(out);
            }
        }
    }

    private static void writeReviewCsv(Path reviewPath, List<RowData> rows) throws IOException {
        String[] reviewHeaders = new String[] {
                "owner_cluster_id",
                "owner_cluster_size",
                "owner_cluster_key",
                "canon_owner",
                "owner_original",
                "city_original",
                "state_original",
                "owner_type_code",
                "owner_norm",
                "city_norm",
                "state_norm",
                "cluster_input_norm",
                "owner_similarity",
                "same_city_flag",
                "merge_decision",
                "needs_review",
                "scope_city_count",
                "row_number"
        };

        List<RowData> sorted = new ArrayList<>(rows);
        sorted.sort(
                Comparator.comparing((RowData r) -> emptySafe(r.clusterId))
                        .thenComparing(r -> emptySafe(r.canonicalOwner))
                        .thenComparing(r -> emptySafe(r.ownerOriginal))
                        .thenComparingInt(r -> r.rowNumber)
        );

        try (BufferedWriter writer = Files.newBufferedWriter(reviewPath);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .builder()
                     .setHeader(reviewHeaders)
                     .build())) {

            for (RowData row : sorted) {
                printer.printRecord(
                        row.clusterId,
                        row.clusterSize,
                        row.clusterKey,
                        row.canonicalOwner,
                        row.ownerOriginal,
                        row.cityOriginal,
                        row.stateOriginal,
                        row.ownerTypeOriginal,
                        row.ownerNormalized,
                        row.cityNormalized,
                        row.stateNormalized,
                        row.compositeNormalized,
                        String.format(Locale.US, "%.4f", row.ownerSimilarity),
                        row.sameCityFlag ? "Y" : "",
                        row.mergeDecision,
                        row.needsReview,
                        row.scopeCityCount,
                        row.rowNumber
                );
            }
        }
    }

    private static void writeClusterSummaryCsv(
            Path clusterSummaryPath,
            Collection<ClusterData> clusters,
            List<RowData> rows) throws IOException {

        String[] headers = new String[] {
                "owner_cluster_id",
                "owner_cluster_key",
                "blocking_mode",
                "dominant_owner_type",
                "canon_owner",
                "canon_owner_norm",
                "cluster_size",
                "distinct_owner_count",
                "distinct_owner_norm_count",
                "distinct_city_count",
                "distinct_state_count",
                "scope_city_count_max",
                "distinct_owner_types",
                "distinct_cities",
                "distinct_states",
                "original_owners"
        };

        List<ClusterData> sortedClusters = new ArrayList<>(clusters);
        sortedClusters.sort(Comparator.comparing(c -> emptySafe(c.clusterId)));

        try (BufferedWriter writer = Files.newBufferedWriter(clusterSummaryPath);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .builder()
                     .setHeader(headers)
                     .build())) {

            for (ClusterData cluster : sortedClusters) {
                printer.printRecord(
                        cluster.clusterId,
                        cluster.clusterKey,
                        cluster.blockingMode,
                        cluster.dominantOwnerType,
                        cluster.canonicalOwner,
                        cluster.canonicalOwnerNorm,
                        cluster.rowIndexes.size(),
                        cluster.distinctOwners.size(),
                        cluster.distinctOwnerNorms.size(),
                        cluster.distinctCities.size(),
                        cluster.distinctStates.size(),
                        cluster.scopeCityCount,
                        joinSorted(cluster.distinctOwnerTypes),
                        joinSorted(cluster.distinctCities),
                        joinSorted(cluster.distinctStates),
                        formatOriginalOwners(cluster.ownerCounts)
                );
            }
        }
    }

    private static String formatOriginalOwners(Map<String, Integer> ownerCounts) {
        return ownerCounts.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(" | "));
    }

    private static String joinSorted(Collection<String> values) {
        return values.stream()
                .filter(v -> v != null && !v.trim().isEmpty())
                .sorted()
                .collect(Collectors.joining(" | "));
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static String normalizeOwnerType(String s) {
    String v = emptySafe(s).toLowerCase(Locale.ROOT).trim();
    v = v.replace('_', ' ');
    v = v.replace('-', ' ');
    v = v.replace("&", "and");
    v = v.replaceAll("\\s+", " ");

    if (v.equals("general corporate")) return "general corporate";
    if (v.equals("natural resource corporation")) return "natural resource corporation";
    if (v.equals("investors")) return "investors";
    if (v.equals("banking and finance")) return "banking and finance";
    if (v.equals("other")) return "other";
    if (v.equals("heirs")) return "heirs";

    return v;
}

private static String cleanHeader(String s) {
    if (s == null) return "";
    return s.replace("\uFEFF", "").trim();
}
    private static String resolveColumnName(List<String> headers, String requestedColumn) {
    String target = cleanHeader(requestedColumn).toLowerCase();

    for (String h : headers) {
        if (cleanHeader(h).toLowerCase().equals(target)) {
            return h;  // return the actual header as stored
        }
    }

    throw new IllegalArgumentException(
        "Column not found: " + requestedColumn + " ; headers found = " + headers
    );
}
    

    private static void addIfMissing(List<String> headers, String column) {
        if (!headers.contains(column)) {
            headers.add(column);
        }
    }

    private static void setValue(List<String> headers, List<String> row, String column, String value) {
        int idx = headers.indexOf(column);
        if (idx >= 0) {
            row.set(idx, value == null ? "" : value);
        }
    }

    private static boolean safeEquals(String a, String b) {
        return emptySafe(a).equals(emptySafe(b));
    }

    private static String emptySafe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String safeString(String s) {
        return s == null ? "" : s.trim();
    }
}