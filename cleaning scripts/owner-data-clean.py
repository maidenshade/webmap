import json
import math

input_file = "C:/users/lshad/Regrid/webmap/parcel-mapping-project/data/owner-data.json"   
output_file = "C:/users/lshad/Regrid/webmap/parcel-mapping-project/data/owner-data-clean.json"  

def clean_nan(obj):
    if isinstance(obj, list):
        return [clean_nan(item) for item in obj]
    elif isinstance(obj, dict):
        return {k: clean_nan(v) for k, v in obj.items()}
    elif isinstance(obj, float) and math.isnan(obj):
        return None
    else:
        return obj

with open(input_file, 'r', encoding='utf-8') as f:
    data = json.load(f)

clean_data = {k: clean_nan(v) for k, v in data.items()}

with open(output_file, 'w', encoding='utf-8') as f:
    json.dump(clean_data, f, indent=2)

print(f"Cleaned JSON saved to {output_file}")
