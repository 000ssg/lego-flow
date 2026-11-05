#!/usr/bin/env python3
"""Check version coverage for all Kafka codec tests."""
import re
import os

codec_dir = '/Users/sergey.sidorov/work/projects/github/lego-flow/messaging/kafka/src/main/java/ssg/legoflow/messaging/kafka/codec'
test_dir = '/Users/sergey.sidorov/work/projects/github/lego-flow/messaging/kafka/src/test/java/ssg/legoflow/messaging/kafka/codec'

print("Codec                                    Versions        Tested                    Status")
print("-" * 100)

total = 0
fully_tested = 0
missing_details = []

for codec_file in sorted(os.listdir(codec_dir)):
    if not codec_file.endswith('Codec.java') or codec_file.startswith('Kafka'):
        continue

    test_file = codec_file.replace('Codec.java', 'CodecTest.java')
    test_path = os.path.join(test_dir, test_file)

    with open(os.path.join(codec_dir, codec_file)) as f:
        content = f.read()

    min_match = re.search(r'MIN_VERSION\s*=\s*(\d+)', content)
    max_match = re.search(r'MAX_VERSION\s*=\s*(\d+)', content)

    if not min_match or not max_match:
        continue

    min_ver = int(min_match.group(1))
    max_ver = int(max_match.group(1))
    total += 1

    tested = set()
    if os.path.exists(test_path):
        with open(test_path) as f:
            test_content = f.read()

        # Pattern 1: void vN() or void vN...() methods
        for m in re.finditer(r'void\s+v(\d+)[\w]*\s*\(', test_content):
            v = int(m.group(1))
            if min_ver <= v <= max_ver:
                tested.add(v)

        # Pattern 2: (short) N with optional space
        for m in re.finditer(r'\(short\s*\)\s*(\d+)', test_content):
            v = int(m.group(1))
            if min_ver <= v <= max_ver:
                tested.add(v)

        # Pattern 3: MIN_VERSION/MAX_VERSION in loops
        if 'MIN_VERSION' in test_content and 'MAX_VERSION' in test_content:
            for v in range(min_ver, max_ver + 1):
                tested.add(v)

    versions_str = f"{min_ver}-{max_ver} ({max_ver - min_ver + 1}v)"
    tested_str = ', '.join(str(v) for v in sorted(tested)) if tested else '(none)'
    status = '✅' if tested == set(range(min_ver, max_ver + 1)) else '⚠️'

    if tested != set(range(min_ver, max_ver + 1)):
        missing = set(range(min_ver, max_ver + 1)) - tested
        missing_details.append((codec_file.replace('Codec.java', ''), list(missing)))

    if tested == set(range(min_ver, max_ver + 1)):
        fully_tested += 1

    name = codec_file.replace('Codec.java', '')
    print(f"{name:<35} {versions_str:<15} {tested_str:<35} {status}")

print()
print(f"Total codecs: {total}")
print(f"Fully tested: {fully_tested}")
print(f"Missing coverage: {total - fully_tested}")

if missing_details:
    print()
    print("Missing coverage details:")
    for name, missing in missing_details:
        print(f"  {name}: missing {missing}")
