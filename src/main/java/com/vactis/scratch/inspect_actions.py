import urllib.request, json, base64, hmac, hashlib, time

def get_token():
    def b64url(b):
        return base64.urlsafe_b64encode(b).decode('utf-8').rstrip('=')
    secret = base64.b64decode('404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970')
    now = int(time.time())
    header = {'alg': 'HS256', 'typ': 'JWT'}
    payload = {'sub': 'admin', 'iat': now, 'exp': now + 86400}
    p1 = b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))
    p2 = b64url(json.dumps(payload, separators=(',', ':')).encode('utf-8'))
    sig = hmac.new(secret, f'{p1}.{p2}'.encode('utf-8'), hashlib.sha256).digest()
    p3 = b64url(sig)
    return f'{p1}.{p2}.{p3}'

token = get_token()

def call_api(endpoint):
    url = f'http://localhost:8082{endpoint}'
    req = urllib.request.Request(url, headers={'Authorization': f'Bearer {token}'})
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode('utf-8'))

actions = call_api('/api/actions')
items = actions.get('items', []) if isinstance(actions, dict) and 'items' in actions else actions

print("Total actions:", len(items))
for a in items[:3]:
    print(json.dumps(a, indent=2, ensure_ascii=False))

recomms = call_api('/api/recommandations')
print("\nRecommandations count:", len(recomms) if isinstance(recomms, list) else len(recomms.get('items', [])))
r_items = recomms.get('items', []) if isinstance(recomms, dict) and 'items' in recomms else recomms
for r in r_items[:3]:
    print(f"{r.get('nomMedecin')}: Action={r.get('actionRecommandee')}, Urgence={r.get('scoreUrgence')}, Valeur={r.get('scoreValeur')}, Statut={r.get('statut')}, Interv={r.get('intervalleEffectif')}")
