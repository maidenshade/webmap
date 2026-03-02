import os
import csv


csv_dir = "/lustre/isaac24/scratch/lshade1/completed/outputs"
output_file = "combined_top20.csv"


top20_suffix = "top20.csv"
summary_suffix = "summary.csv"


header_written = False

with open(output_file, "w", newline="") as outfile:
    writer = csv.writer(outfile)

    for root, dirs, files in os.walk(csv_dir):
        for file in files:
            if file.endswith(top20_suffix):
                top20_path = os.path.join(root, file)

                
                summary_path = None
                for f in files:
                    if f.endswith(summary_suffix):
                        summary_path = os.path.join(root, f)
                        break

                if not summary_path:
                    print(f"⚠️  Summary CSV not found for: {top20_path}")
                    continue

                
                try:
                    with open(summary_path, "r", newline="") as sfile:
                        reader = csv.DictReader(sfile)
                        first_row = next(reader)
                        geoid = first_row.get("geoid")
                        if not geoid:
                            print(f"⚠️  GEOID not found in: {summary_path}")
                            continue
                except Exception as e:
                    print(f"❌ Error reading summary.csv: {summary_path} — {e}")
                    continue

                
                try:
                    with open(top20_path, "r", newline="") as tfile:
                        reader = csv.reader(tfile)
                        headers = next(reader)
                        if not header_written:
                            writer.writerow(["geoid"] + headers)
                            header_written = True
                        for row in reader:
                            writer.writerow([geoid] + row)
                except Exception as e:
                    print(f"❌ Error reading top20.csv: {top20_path} — {e}")
                    continue

print(f"\n✅ Aggregation complete. Output saved to: {output_file}")
