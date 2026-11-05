import json, re
def load(p):
    t = open(p).read()
    t = t[t.index('{'):]
    t = re.sub(r'//.*', '', t)
    return json.loads(t)

def summarize(t):
    if isinstance(t, (str, int, bool, type(None))):
        return str(t)
    keys = list(t.keys())
    extra = ''
    if 'default' in t:
        extra += ' dflt=' + str(t['default'])
    if 'nullable' in t:
        extra += ' nullable=' + str(t['nullable'])
    return json.dumps(t) + extra

for n in ['JoinGroupRequest', 'JoinGroupResponse']:
    d = load(f'doc/spec/message/{n}.json')
    print('==', n)
    # top-level fields: print name, type, versions, default/nullable
    for f in d['fields']:
        t = f['type']
        if isinstance(t, dict):
            print('  TOP', f['name'], 'versions:', f.get('versions'), summarize(t))
        else:
            print('  TOP', f['name'], 'versions:', f.get('versions'), t)
    # nested types
    for ty in d.get('types', []):
        print('  -- nested type:', ty['name'])
        for f in ty['fields']:
            t = f['type']
            print('    ', f['name'], 'versions:', f.get('versions'), summarize(t))
