import json
from collections import defaultdict
import re

# Load your alias-map.json
with open('alias-map.json', 'r') as f:
    alias_map = json.load(f)

# Common stopwords to exclude
stopwords = {
    "llc", "co", "company", "corporation", "timberlands", "land", "surface", "heirs", "inc", "corp", "timber", "timberland", "forest", "state",
    "department", "authority", "city", "united", "states", "properties", "dept", "lp", "and", "the"
}

# Function to tokenize and clean names
def clean_and_tokenize(name):
    name = name.lower()
    name = name.replace('&', ' and ')  # replace ampersand with 'and'
    name = re.sub(r'[^a-z0-9 ]+', '', name)
    tokens = name.split()
    return [t for t in tokens if t not in stopwords]


# Map tokens to canonical names where they appear
token_to_names = defaultdict(set)

for canonical_name in alias_map:
    tokens = clean_and_tokenize(canonical_name)
    for token in tokens:
        token_to_names[token].add(canonical_name)

# Keep only tokens that appear in multiple canonical names
multi_use_tokens = {
    token: sorted(list(names))
    for token, names in token_to_names.items()
    if len(names) >= 2
}

# Sort by number of appearances
multi_use_tokens_sorted = dict(
    sorted(multi_use_tokens.items(), key=lambda x: len(x[1]), reverse=True)
)

# Print results
for token, names in multi_use_tokens_sorted.items():
    print(f"{token} ({len(names)}):")
    for name in names:
        print(f"  - {name}")
    print()
# Save to file
with open("tokens.json", "w", encoding="utf-8") as out:
    json.dump(final_alias_map, out, indent=2)
