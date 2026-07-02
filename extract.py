import json
import re

with open('form.html', 'r', encoding='utf-16') as f:
    html = f.read()
    
match = re.search(r'FB_PUBLIC_LOAD_DATA_\s*=\s*(\[.*?\]);\n', html)
if match:
    data = json.loads(match.group(1))
    # data[1][1] contains the form items
    items = data[1][1]
    for item in items:
        # Check if item has title and field info
        if len(item) > 4 and item[4]:
            title = item[1]
            field_id = item[4][0][0]
            print(f"Title: {title}")
            print(f"ID: {field_id}")
            print("---")
else:
    print("Could not find FB_PUBLIC_LOAD_DATA_")
