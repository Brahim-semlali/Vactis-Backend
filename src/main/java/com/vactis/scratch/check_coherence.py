import urllib.request, json, base64, hmac, hashlib, time

def get_token():
    def b64url(b): return base64.urlsafe_b64encode(b).decode('utf-8').rstrip('=')
    secret = base64.b64decode('404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970')
    now = int(time.time())
    header = {'alg': 'HS256', 'typ': 'JWT'}
    payload = {'sub': 'admin', 'iat': now, 'exp': now + 86400}
    p1 = b64url(json.dumps(header, separators=(',', ':')).encode('utf-8'))
    p2 = b64url(json.dumps(payload, separators=(',', ':')).encode('utf-8'))
    sig = hmac.new(secret, f'{p1}.{p2}'.encode('utf-8'), hashlib.sha256).digest()
    return f'{p1}.{p2}.{b64url(sig)}'

token = get_token()
req = urllib.request.Request('http://localhost:8082/api/medecins', headers={'Authorization': f'Bearer {token}'})
with urllib.request.urlopen(req) as resp:
    meds = json.loads(resp.read().decode('utf-8'))

items = meds.get('items', meds) if isinstance(meds, dict) else meds
print(f'Total meds: {len(items)}')

req_act = urllib.request.Request('http://localhost:8082/api/actions', headers={'Authorization': f'Bearer {token}'})
with urllib.request.urlopen(req_act) as resp:
    actions_res = json.loads(resp.read().decode('utf-8'))
actions = actions_res.get('items', actions_res) if isinstance(actions_res, dict) else actions_res
print(f'Total actions: {len(actions)}')
urgences = set(a.get('urgence') for a in actions)
print('Current urgences in actions:', urgences)
for a in actions[:6]:
    m = a.get('medecin', {})
    print(f"{m.get('nom')} {m.get('prenom')}: action.urgence={a.get('urgence')}, niveauIntensite={m.get('niveauIntensite')}, scoreUrgence={m.get('scoreUrgence')}, horizon={a.get('horizonJours')}")

