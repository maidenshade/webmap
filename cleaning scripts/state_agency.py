import json
import re

# Load the original state agency alias map
with open("state-agency-aliases.json", "r", encoding="utf-8") as f:
    original_aliases = json.load(f)

# List of state names and abbreviations
state_abbrevs = {
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

# Keywords for agencies
agency_keywords = [
    "bureau", "department", "dept", "commission", "wildlife", "fish", "game",
    "transportation", "division", "dnr", "div", "board", "authority", "recreation", "rec"
]

def normalize(text):
    return re.sub(r'[^a-z0-9 ]', '', text.lower())

filtered_aliases = {}

for state, abbr in state_abbrevs.items():
    canonical = f"State of {state}"
    state_norm = normalize(state)
    abbr_norm = normalize(abbr)

    # Start fresh with filtered aliases
    filtered = []

    # Pull existing aliases from original if any
    original = original_aliases.get(canonical, [])
    for alias in original:
        alias_norm = normalize(alias)
        if re.search(rf'\b({re.escape(state_norm)}|{re.escape(abbr_norm)})\b', alias_norm):
            filtered.append(alias)

    # Add generated catch-all forms
    catchalls = set()
    for word in agency_keywords:
        catchalls.add(f"{state} {word.title()}")
        catchalls.add(f"{abbr} {word.upper()}")
        catchalls.add(f"{state} {word.upper()}")
        catchalls.add(f"{abbr} {word.title()}")
        catchalls.add(f"{state.upper()} {word}")
        catchalls.add(f"{abbr.upper()} {word}")

    catchalls.add(f"{state} State")
    catchalls.add(f"{abbr} State")

    # Merge filtered + catchalls
    all_variants = sorted(set(filtered + list(catchalls)))
    if all_variants:
        filtered_aliases[canonical] = all_variants

# Save cleaned + expanded state aliases
with open("state-agency-aliases-clean.json", "w", encoding="utf-8") as f:
    json.dump(filtered_aliases, f, indent=2)

print(f"✅ Cleaned and expanded aliases written for {len(filtered_aliases)} states.")
