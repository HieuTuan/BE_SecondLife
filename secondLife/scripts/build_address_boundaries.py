"""Build the exact compressed administrative lookup resource from the upstream GeoJSON archive.

Usage: python scripts/build_address_boundaries.py input.zip output.bin.gz
Source and pinned version are recorded in geocoding/administrative-boundaries-LICENSE.txt.
Ordinate encoding: microdegree int32 when exactly reversible, otherwise MIN_INT + IEEE754 double.
"""
import gzip
import json
import struct
import sys
import zipfile

CITIES = {"/01_ha_noi/": "HANOI", "/48_da_nang/": "DANANG", "/79_ho_chi_minh/": "HO_CHI_MINH"}

def write_text(stream, text):
    encoded = text.encode("utf-8")
    stream.write(struct.pack(">I", len(encoded)))
    stream.write(encoded)

def ordinate(stream, value):
    scaled = round(value * 1000000)
    if -2147483647 <= scaled <= 2147483647 and scaled / 1000000 == value:
        stream.write(struct.pack(">i", scaled))
    else:
        stream.write(struct.pack(">id", -2147483648, value))

def build(source, destination):
    rows = []
    with zipfile.ZipFile(source) as archive:
        for name in archive.namelist():
            city = next((value for prefix, value in CITIES.items() if prefix in "/" + name), None)
            if city and "/wards/" in name and name.endswith(".geojson"):
                rows.extend((city, feature) for feature in json.loads(archive.read(name))["features"])
    if len(rows) != 388 or len({f["properties"]["code"] for _, f in rows}) != 388:
        raise ValueError("Expected 388 unique wards for the pinned dataset")
    with open(destination, "wb") as output, gzip.GzipFile(filename="", fileobj=output, mode="wb", mtime=0) as stream:
        stream.write(b"SLADM002")
        stream.write(struct.pack(">I", len(rows)))
        for city, feature in rows:
            for text in (city, feature["properties"]["code"], feature["properties"]["fullName"]):
                write_text(stream, text)
            stream.write(struct.pack(">4d", *feature["bbox"]))
            geometry = feature["geometry"]
            polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
            stream.write(struct.pack(">I", len(polygons)))
            for polygon in polygons:
                stream.write(struct.pack(">I", len(polygon)))
                for ring in polygon:
                    stream.write(struct.pack(">I", len(ring)))
                    for x, y in ring:
                        ordinate(stream, x)
                        ordinate(stream, y)

if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
