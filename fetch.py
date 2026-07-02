import urllib.request
import re

url = "https://docs.google.com/forms/d/e/1FAIpQLSco9XSW8OpzyR3ty03T-iUMQ6dAa6LhaJzeVihmrIx4OuhL6A/viewform"
html = urllib.request.urlopen(url).read().decode('utf-8')

matches = re.findall(r'\[(\d+),\"([^\"]+)\",', html)
for match in matches:
    print(f"ID: {match[0]} -> {match[1]}")
