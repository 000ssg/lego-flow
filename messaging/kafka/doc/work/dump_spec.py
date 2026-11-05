import re, json

def load_spec(path):
    text = open(path).read()
    text = re.sub(r'//.*', '', text)
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    return json.loads(text)

def show(node, ind=0, out=None):
    if out is None:
        out = []
    if isinstance(node, dict):
        if 'type' in node:
            t = node['type']
            out.append(' ' * ind + f"{node.get('name','?')}: {t} v={node.get('versions')} "
                         f"null={node.get('nullable', False)} ignorable={node.get('ignorable', False)} "
                         f"dflt={node.get('default')}")
            if isinstance(t, dict) and 'type' in t:
                show(t['type'], ind + 2, out)
        elif 'type' in node and node['type'] == 'array' and isinstance(node, dict):
            pass
    else:
        pass
    return out

def walk(node, ind=0, out=None):
    if out is None:
        out = []
    if isinstance(node, dict):
        name = node.get('name')
        typ = node.get('type')
        if name is not None and typ is not None:
            out.append('  ' * ind + f"{name}: {typ} versions={node.get('versions')} "
                         f"nullable={node.get('nullable', False)} ignorable={node.get('ignorable', False)} "
                         f"default={node.get('default')}")
            if isinstance(typ, list):
                for el in typ:
                    if isinstance(el, dict):
                        walk(el, ind + 1, out)
            elif isinstance(typ, dict) and ('name' in typ or typ.get('type') == 'array'):
                walk(typ, ind + 1, out)
    return out

for path in ['messaging/kafka/doc/spec/message/OffsetFetchRequest.json',
             'messaging/kafka/doc/spec/message/OffsetFetchResponse.json']:
    spec = load_spec(path)
    print(f"===== {path} =====")
    print(f"apiKey={spec.get('apiKey')} type={spec.get('type')} flexibleVersions={spec.get('flexibleVersions')}")
    for line in walk(spec):
        print(line)
    print()
