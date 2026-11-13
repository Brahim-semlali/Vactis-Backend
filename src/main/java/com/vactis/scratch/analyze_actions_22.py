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
req = urllib.request.Request('http://localhost:8082/api/actions', headers={'Authorization': f'Bearer {token}'})
with urllib.request.urlopen(req) as resp:
    actions_res = json.loads(resp.read().decode('utf-8'))

actions = actions_res.get('items', actions_res) if isinstance(actions_res, dict) else actions_res

print(f"Total actions: {len(actions)}")
etats = {}
for a in actions:
    e = a.get('etatAction')
    etats[e] = etats.get(e, 0) + 1

print("Etats distribution:", etats)

req_kpi = urllib.request.Request('http://localhost:8082/api/actions/kpis', headers={'Authorization': f'Bearer {token}'})
try:
    with urllib.request.urlopen(req_kpi) as resp:
        kpis = json.loads(resp.read().decode('utf-8'))
        print("KPIs:", kpis)
except Exception as ex:
    print("KPIs error:", ex)

print("\n--- DETAIL OF ALL ACTIONS ---")
for idx, a in enumerate(actions, 1):
    m = a.get('medecin') or {}
    nom = f"{m.get('nom', '')} {m.get('prenom', '')}".strip()
    statut_med = m.get('statut')
    segment_med = m.get('segment')
    score_urg = m.get('scoreUrgence')
    act = a.get('actionRecommandee')
    etat = a.get('etatAction')
    source = a.get('sourceRecommandation')
    date_v = a.get('dateVisite')
    urg = a.get('urgence')
    aid = a.get('id')
    print(f"#{idx:2d} | ID:{aid:4d} | Med:{nom:25s} | Statut:{str(statut_med):15s} | Seg:{str(segment_med):10s} | Act:{str(act):25s} | Etat:{str(etat):10s} | ScoreUrg:{score_urg} | Urg:{urg} | DateV:{date_v} | Src:{source}")
