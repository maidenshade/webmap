import json
from collections import defaultdict

# Load owner data from file
with open("owner-data-clean.json", "r", encoding="utf-8") as f:
    owner_data = json.load(f)

# Utility to clean and tokenize names
def clean_name(name):
    return (
        name.lower()
        .replace("&", "and")
        .replace("'", "")
        .replace(",", "")
        .replace(".", "")
        .replace("(", "")
        .replace(")", "")
        .replace("/", " ")
        .replace("-", " ")
        .replace("llc", "")  # Optional: strip common suffixes
        .replace("inc", "")
        .replace("ltd", "")
        .replace("lp", "")
        .replace("co", "")
        .replace("company", "")
        .replace("corporation", "")
        .replace("corp", "")
        .replace("  ", " ")
        .strip()
    )

def get_tokens(name):
    return set(clean_name(name).split())

# Only take top N owners per county
TOP_N = 20

# Build alias suggestions within each county
alias_map_suggestions = defaultdict(set)

for owners in owner_data.values():
    top_owners = sorted(owners, key=lambda x: -x.get("percent_total", 0))[:TOP_N]
    name_list = [o.get("standardiz", "").strip() for o in top_owners if o.get("standardiz")]

    for i, name_a in enumerate(name_list):
        tokens_a = get_tokens(name_a)
        for j, name_b in enumerate(name_list):
            if i == j:
                continue
            tokens_b = get_tokens(name_b)

            if not tokens_a or not tokens_b:
                continue

            # If tokens_a ⊆ tokens_b or vice versa, consider similar
            if tokens_a.issubset(tokens_b) or tokens_b.issubset(tokens_a):
                # Pick canonical name as the longer original (or most frequent)
                canonical = name_a if len(name_a) >= len(name_b) else name_b
                variant = name_b if canonical == name_a else name_a
                alias_map_suggestions[canonical.upper()].add(variant)

# Finalize into standard dict
final_alias_map = {
    canonical: sorted(variants)
    for canonical, variants in alias_map_suggestions.items()
}

# Save to file
with open("alias-map-suggestions.json", "w", encoding="utf-8") as out:
    json.dump(final_alias_map, out, indent=2)
