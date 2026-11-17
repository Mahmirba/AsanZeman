#!/usr/bin/env python3
"""Prints every line and branch JaCoCo reports as missed, so the gate's reason is visible in the CI log."""
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
for pkg in root.iter("package"):
    for sf in pkg.iter("sourcefile"):
        missed = [f"{ln.get('nr')}{'b' if int(ln.get('mb')) > 0 else ''}" for ln in sf.findall("line")
                  if int(ln.get("mi")) > 0 or int(ln.get("mb")) > 0]
        if missed:
            print(f"{pkg.get('name')}/{sf.get('name')}: {' '.join(missed)}   (nn = line missed, nnb = branch missed)")
