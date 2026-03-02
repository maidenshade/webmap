import json
import re

# Load main alias map
with open("alias-map.json", "r", encoding="utf-8") as f:
    alias_map = json.load(f)

# Load cleaned state-specific aliases
with open("state-agency-aliases-clean.json", "r", encoding="utf-8") as f:
    state_aliases = json.load(f)

# State data
state_data = {
    "Alabama": "AL", "Alaska": "AK", "Arizona": "AZ", "Arkansas": "AR", "California": "CA",
    "Colorado": "CO", "Connecticut": "CT", "Delaware": "DE", "Florida": "FL", "Georgia": "GA",
    "Hawaii": "HI", "Idaho": "ID", "Illinois": "IL", "Indiana": "IN", "Iowa": "IA",
    "Kansas": "KS", "Kentucky": "KY", "Louisiana": "LA", "Maine": "ME", "Maryland": "MD",
    "Massachusetts": "MA", "Michigan": "MI", "Minnesota": "MN", "Mississippi": "MS", "Missouri": "MO",
    "Montana": "MT", "Nebraska": "NE", "Nevada": "NV", "New Hampshire": "NH", "New Jersey": "NJ",
    "New Mexico": "NM", "New York": "NY", "North Carolina": "NC", "North Dakota": "ND", "Ohio": "OH",
    "Oklahoma": "OK", "Oregon": "OR", "Pennsylvania": "PA", "Rhode Island": "RI", "South Carolina": "SC",
    "South Dakota": "SD", "Tennessee": "TN", "Texas": "TX", "Utah": "UT", "Vermont": "VT",
    "Virginia": "VA", "Washington": "WA", "West Virginia": "WV", "Wisconsin": "WI", "Wyoming": "WY"
}

# Expanded stop words
stop_words = {
    "state", "department", "division", "of", "natural", "resources", "forestry", "parks",
    "dnr", "dept", "commission", "authority", "board", "agency", "wildlife", "conservation",
    "transportation", "fish", "game", "rec", "land", "service", "services"
}

# Create lowercase versions of cleaned canonical keys to protect them
clean_state_keys_lower = {canonical.lower() for canonical in state_aliases.keys()}

# Function to detect state agency–like names
def looks_like_state_agency(name):
    cleaned = re.sub(r"[^a-z ]", "", name.lower())
    tokens = set(cleaned.split()) - stop_words
    for state, abbr in state_data.items():
        state_tokens = set(state.lower().split()) | {abbr.lower()}
        if tokens & state_tokens:
            return True
    return False

# Step 1: Remove vague or wrongly grouped entries
keys_to_delete = []
for key in list(alias_map.keys()):
    key_lower = key.lower()
    if key_lower in clean_state_keys_lower:
        # Only clean variants under preserved state key
        alias_map[key] = [v for v in alias_map[key] if not looks_like_state_agency(v)]
    elif looks_like_state_agency(key):
        keys_to_delete.append(key)
    else:
        # Clean variants if any look like state agencies
        alias_map[key] = [v for v in alias_map[key] if not looks_like_state_agency(v)]
        if not alias_map[key]:  # If empty after cleaning, mark for deletion
            keys_to_delete.append(key)

# Remove marked keys
for k in keys_to_delete:
    alias_map.pop(k, None)

# Step 2: Merge in cleaned state agency aliases
for canonical, new_variants in state_aliases.items():
    existing = alias_map.get(canonical, [])
    combined = sorted(set(existing + new_variants))
    alias_map[canonical] = combined

# Save cleaned alias map
with open("alias-map-cleaned.json", "w", encoding="utf-8") as f:
    json.dump(alias_map, f, indent=2)

print("✅ alias-map-cleaned.json written with cleaned and merged state-level aliases.")
