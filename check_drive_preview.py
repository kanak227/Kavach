import urllib.request
import re

url = "https://drive.google.com/file/d/1TQs03g_4Kbrt6UAxx5_dhUzrUKOBkD1w/view?usp=sharing"
req = urllib.request.Request(url, headers={"User-Agent": "facebookexternalhit/1.1"})
with urllib.request.urlopen(req) as resp:
    html = resp.read().decode("utf-8", errors="ignore")
    print("Full meta tags from Google Drive:")
    for m in re.findall(r'<meta[^>]+>', html):
        if any(k in m for k in ['og:', 'image', 'description']):
            print(m)
